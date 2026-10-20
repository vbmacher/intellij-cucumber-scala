package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.components.JBList
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._
import javax.swing._
import javax.swing.tree.{DefaultMutableTreeNode, TreePath}

@RunWith(classOf[JUnit4])
class SuitePanelSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(panel: SuitePanel, text: String): JButton =
    children(panel).collectFirst { case b: JButton if b.getText == text => b }.get
  private def combo(panel: SuitePanel, first: String): JComboBox[_] =
    children(panel).collectFirst { case c: JComboBox[_] if c.getItemAt(0).toString == first => c }.get
  private def field(panel: SuitePanel, name: String): JTextField =
    children(panel).collectFirst { case f: JTextField if f.getAccessibleContext.getAccessibleName == name => f }.get
  private def tree(panel: SuitePanel): JTree = children(panel).collectFirst { case t: JTree => t }.get
  private def nodes(panel: SuitePanel): Seq[DefaultMutableTreeNode] =
    tree(panel).getModel.getRoot.asInstanceOf[DefaultMutableTreeNode].depthFirstEnumeration().asScala.toSeq
      .collect { case node: DefaultMutableTreeNode => node }
  private def findings(panel: SuitePanel): Seq[DefaultMutableTreeNode] =
    nodes(panel).filter(_.getUserObject.isInstanceOf[SuiteAnalysis.Finding])
  private def path(node: DefaultMutableTreeNode): TreePath = new TreePath(node.getPath.map(_.asInstanceOf[AnyRef]))
  private def select(panel: SuitePanel, index: Int): Unit =
    tree(panel).setSelectionPath(path(findings(panel)(index)))
  private def related(panel: SuitePanel): JBList[SuiteAnalysis.Source] =
    children(panel).collectFirst { case l: JBList[_] => l.asInstanceOf[JBList[SuiteAnalysis.Source]] }.get
  private def check(panel: SuitePanel, text: String): JCheckBox =
    children(panel).collectFirst { case c: JCheckBox if c.getText == text => c }.get
  private def hasText(panel: SuitePanel, text: String): Boolean =
    children(panel).exists { case l: JLabel => Option(l.getText).exists(_.contains(text)); case _ => false }
  private def await(panel: SuitePanel, text: String): Unit =
    PlatformTestUtil.waitWithEventsDispatching(text, () => hasText(panel, text), 10)
  private def panel(): SuitePanel = {
    val value = new SuitePanel(getProject)
    Disposer.register(getTestRootDisposable, value)
    await(value, "step usages")
    value
  }
  private def fixtures(): Unit = {
    myFixture.addFileToProject("steps/Steps.scala",
      "class Steps {\n Given(\"account {int}\") { (id: Int) => () }\n Given(\"account {word}\") { (id: String) => () }\n Then(\"unused\") { () => () }\n}")
    myFixture.configureByText("payments.feature",
      "Feature: Payments\n @smoke\n Scenario: Account\n  Given account 42\n  Then missing\n")
  }

  @Test
  def testAmbiguityExplainsCompetingDefinitionsAndNavigatesBothSources(): Unit = {
    fixtures()
    val view = panel()
    combo(view, "Scenarios").getSelectedItem shouldBe SuiteView.Scenarios
    findings(view).size shouldBe 1
    button(view, "Source").isEnabled shouldBe false
    combo(view, "Scenarios").setSelectedItem(SuiteView.Ambiguous)
    hasText(view, "Feature steps matching more than one Scala definition") shouldBe true
    val results = tree(view)
    findings(view).size shouldBe 1
    val node = findings(view).head
    val hit = node.getUserObject.asInstanceOf[SuiteAnalysis.Finding].source
    hit.label should include("Given account 42")
    hit.location should endWith("payments.feature:4")
    select(view, 0)
    val sources = related(view)
    sources.getModel.getSize shouldBe 2
    hasText(view, "Competing definitions · 2") shouldBe true
    button(view, "Source").doClick()
    myFixture.getEditor.getCaretModel.getOffset shouldBe myFixture.getFile.getText.indexOf("Given")
    sources.setSelectedIndex(1)
    val target = sources.getSelectedValue.target
    button(view, "Open related source").doClick()
    val openedEditor = FileEditorManager.getInstance(getProject).getSelectedTextEditor
    openedEditor.getDocument.getText should include("class Steps")
    openedEditor.getCaretModel.getOffset shouldBe target.getTextOffset
    val cell = results.getCellRenderer.getTreeCellRendererComponent(results, node, true, false, true, 1, false)
    cell.setSize(340, 60)
    cell.validate()
    children(cell.asInstanceOf[Container]).collect { case l: JLabel => l }.exists(l => l.getText == hit.location && l.getHeight > 0) shouldBe true
  }

  @Test
  def testFiltersAndGroupingWorkWithoutReadAccessAndRecoverFromInvalidTags(): Unit = {
    fixtures()
    val view = panel()
    ScalaCucumberSettings.getInstance(getProject).tagCategories = "Smoke => @smoke"
    val moduleName = myFixture.getModule.getName
    val finished = new java.util.concurrent.atomic.AtomicBoolean(false)
    val failure = new AtomicReference[Throwable]()
    SwingUtilities.invokeLater(() => {
      try {
        ApplicationManager.getApplication.isReadAccessAllowed shouldBe false
        Seq("Tag", "Category").foreach { group =>
          check(view, group).doClick()
          findings(view).size shouldBe 1
        }
        check(view, "Tag").isSelected shouldBe true
        check(view, "Category").isSelected shouldBe true
        check(view, "Module").isSelected shouldBe true
        nodes(view).map(_.toString) should contain("@smoke · 1")
        nodes(view).map(_.toString) should contain("Smoke · 1")
        findings(view).head.getPath.length shouldBe 5
        check(view, "Module").doClick()
        findings(view).head.getPath.length shouldBe 4
        Seq("Tag", "Category").foreach(check(view, _).doClick())
        field(view, "Scenario tag expression").setText("@smoke and (")
        findings(view).size shouldBe 0
        hasText(view, "Invalid tag expression") shouldBe true
        combo(view, "Scenarios").setSelectedItem(SuiteView.Definitions)
        findings(view).size shouldBe 3
        hasText(view, "Invalid tag expression") shouldBe false
        combo(view, "Any step kind").setSelectedItem("Given")
        findings(view).size shouldBe 2
        select(view, 0)
        related(view).getModel.getSize shouldBe 1
        field(view, "Suite text filter").setText("does-not-exist")
        findings(view).size shouldBe 0
        button(view, "Source").isEnabled shouldBe false
        field(view, "Suite text filter").setText("steps/Steps.scala")
        findings(view).size shouldBe 2
        combo(view, "Scenarios").setSelectedItem(SuiteView.Scenarios)
        field(view, "Suite text filter").setText("")
        field(view, "Scenario tag expression").setText("@smoke")
        findings(view).size shouldBe 1
        hasText(view, "Invalid tag expression") shouldBe false
        field(view, "Suite text filter").setText(moduleName)
        findings(view).size shouldBe 1
      } catch { case error: Throwable => failure.set(error) }
      finally finished.set(true)
    })
    PlatformTestUtil.waitWithEventsDispatching("filter listeners completed", () => finished.get(), 10)
    Option(failure.get()).foreach(throw _)
  }

  @Test
  def testCombinedGroupsCountDistinctScenariosAndAllowMultipleSelection(): Unit = {
    fixtures()
    myFixture.addFileToProject("refunds.feature", "Feature: Refunds\n @smoke @payments\n Scenario: Refund\n  Then missing\n")
    ScalaCucumberSettings.getInstance(getProject).tagCategories = "Smoke => @smoke\nPayments => @payments"
    val view = panel()
    Seq("Tag", "Category").foreach(check(view, _).doClick())
    hasText(view, "Scenarios · 2") shouldBe true
    val allNodes = nodes(view)
    allNodes.filter(n => n.getLevel == 1).map(_.toString) shouldBe Seq(myFixture.getModule.getName + " · 2")
    val tags = allNodes.filter(n => n.getLevel == 2)
    tags.map(_.toString).toSet shouldBe Set("@smoke · 2", "@payments · 1")
    val smoke = tags.find(_.toString == "@smoke · 2").get
    val payments = tags.find(_.toString == "@payments · 1").get
    tree(view).setSelectionPaths(Array(path(smoke), path(payments)))
    tree(view).getSelectionCount shouldBe 2
    button(view, "Run").isEnabled shouldBe true
    button(view, "Run").getToolTipText shouldBe "Run 2 selected scenarios"
    button(view, "Source").isEnabled shouldBe false
    tree(view).collapsePath(path(smoke))
    tree(view).isExpanded(path(smoke)) shouldBe false
    Seq("Module", "Tag", "Category").foreach(check(view, _).doClick())
    findings(view).size shouldBe 2
    nodes(view).count(n => n.getLevel == 1) shouldBe 2
  }

  @Test
  def testControlsRemainVisibleAtToolWindowWidthsAndEmptyViewsHaveNoFakeRows(): Unit = {
    fixtures()
    val view = panel()
    def layout(container: Container): Unit = {
      container.doLayout()
      container.getComponents.collect { case child: Container => child }.foreach(layout)
    }
    Seq(360, 760).foreach { width =>
      Seq(SuiteView.Scenarios, SuiteView.Definitions, SuiteView.Ambiguous).foreach { category =>
        combo(view, "Scenarios").setSelectedItem(category)
        if (category == SuiteView.Ambiguous) select(view, 0)
        view.setSize(width, 850)
        (1 to 3).foreach(_ => layout(view))
        Seq(check(view, "Module"), check(view, "Tag"), check(view, "Category"), combo(view, "Scenarios"), field(view, "Suite text filter"), button(view, "Refresh"), button(view, "Source"), button(view, "Preview"), button(view, "Run"), button(view, "Insert")).filter(_.isVisible).foreach { component =>
          val bounds = SwingUtilities.convertRectangle(component.getParent, component.getBounds, view)
          bounds.x should be >= 0
          bounds.x + bounds.width should be <= width
          bounds.height should be > 0
        }
        field(view, "Suite text filter").getWidth should be > 200
        button(view, "Source").getWidth should be < 150
        if (category == SuiteView.Scenarios) button(view, "Run").getY shouldBe button(view, "Source").getY
        if (category == SuiteView.Definitions) button(view, "Insert").getY shouldBe button(view, "Source").getY
        tree(view).getScrollableTracksViewportWidth shouldBe true
        if (width == 360 && category == SuiteView.Ambiguous) {
          val hint = children(view).collectFirst { case l: JLabel if Option(l.getText).exists(_.startsWith("Feature steps matching")) => l }.get
          hint.getHeight should be > 30
        }
      }
    }
    combo(view, "Scenarios").setSelectedItem(SuiteView.Repeated)
    findings(view).size shouldBe 0
    button(view, "Source").isEnabled shouldBe false
    button(view, "Preview").isEnabled shouldBe false
    button(view, "Cancel").isVisible shouldBe false
  }

  @Test
  def testRefreshReplacesFindingsAfterSourceEdits(): Unit = {
    fixtures()
    val view = panel()
    combo(view, "Scenarios").setSelectedItem(SuiteView.Undefined)
    findings(view).size shouldBe 1
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable { override def run(): Unit = {
      val document = myFixture.getEditor.getDocument
      document.replaceString(0, document.getTextLength, "Feature: Payments\n Scenario: Account\n  Given account 42\n")
    } })
    com.intellij.psi.PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    button(view, "Refresh").doClick()
    await(view, "step usages")
    findings(view).size shouldBe 0
  }

  @Test
  def testFilteringKeepsTheSelectedFinding(): Unit = {
    fixtures()
    val view = panel()
    combo(view, "Scenarios").setSelectedItem(SuiteView.Definitions)
    select(view, findings(view).size - 1)
    val target = tree(view).getLastSelectedPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject.asInstanceOf[SuiteAnalysis.Finding].source.target
    field(view, "Suite text filter").setText(".scala")
    findings(view).size should be > 0
    tree(view).getLastSelectedPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject
      .asInstanceOf[SuiteAnalysis.Finding].source.target shouldBe target
  }
}
