package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import javax.swing._

@RunWith(classOf[JUnit4])
class PairedStepPanelSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(panel: PairedStepPanel): JButton =
    children(panel).collectFirst { case b: JButton if b.getText == "Open definition" => b }.get
  private def pin(panel: PairedStepPanel): JCheckBox =
    children(panel).collectFirst { case c: JCheckBox if c.getText == "Pin preview" => c }.get
  private def combo(panel: PairedStepPanel): JComboBox[_] =
    children(panel).collectFirst { case c: JComboBox[_] => c }.get
  private def hasText(panel: PairedStepPanel, text: String): Boolean =
    children(panel).exists { case l: JLabel => Option(l.getText).exists(_.contains(text)); case _ => false }
  private def await(panel: PairedStepPanel, text: String): Unit =
    PlatformTestUtil.waitWithEventsDispatching(text, () => hasText(panel, text), 10)
  private def preview(panel: PairedStepPanel) =
    EditorFactory.getInstance().getAllEditors.find(e => e.isViewer && SwingUtilities.isDescendingFrom(e.getComponent, panel)).get
  private def fixtures(): Unit = {
    myFixture.addFileToProject("steps/Steps.scala",
      "class Steps {\n Given(\"account {int}\") { (id: Int) => () }\n Then(\"ready\") { () => () }\n}")
    myFixture.configureByText("payments.feature",
      "Feature: Payments\n Scenario: Account\n  Given account 42\n  Then ready\n  Then missing\n")
    moveTo("account 42")
  }
  private def moveTo(text: String): Unit =
    myFixture.getEditor.getCaretModel.moveToOffset(myFixture.getFile.getText.indexOf(text))
  private def panel(): PairedStepPanel = {
    val value = new PairedStepPanel(getProject)
    Disposer.register(getTestRootDisposable, value)
    value
  }

  @Test
  def testAutomaticallyPreviewsCurrentStepAndDistinguishesUndefinedFromNoStep(): Unit = {
    fixtures()
    val view = panel()
    await(view, "1 matching Scala definition")
    hasText(view, "Given account 42") shouldBe true
    preview(view).getDocument.getText should include("Given(\"account {int}\")")
    button(view).isEnabled shouldBe true
    pin(view).isEnabled shouldBe true
    combo(view).getItemCount shouldBe 1
    combo(view).isVisible shouldBe false
    moveTo("missing")
    await(view, "Undefined step")
    button(view).isEnabled shouldBe false
    pin(view).isEnabled shouldBe false
    combo(view).getItemCount shouldBe 0
    combo(view).getParent.getParent.isVisible shouldBe false
    preview(view).getComponent.isVisible shouldBe false
    moveTo("Scenario")
    await(view, "No feature step selected")
    hasText(view, "Undefined step") shouldBe false
    preview(view).getDocument.getText shouldBe ""
  }

  @Test
  def testPinCancelsQueuedSelectionAndUnpinFollowsCurrentCaret(): Unit = {
    fixtures()
    val view = panel()
    await(view, "1 matching Scala definition")
    moveTo("ready")
    pin(view).doClick()
    hasText(view, "Pinned.") shouldBe true
    com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    hasText(view, "Given account 42") shouldBe true
    preview(view).getDocument.getText should include("account {int}")
    pin(view).doClick()
    await(view, "Then ready")
    preview(view).getDocument.getText should include("Then(\"ready\")")
  }

  @Test
  def testAmbiguousCandidatesPreviewAndOpenTheirOwnDefinition(): Unit = {
    fixtures()
    myFixture.addFileToProject("steps/OtherSteps.scala",
      "class OtherSteps {\n Given(\"account {word}\") { (id: String) => () }\n}")
    val view = panel()
    await(view, "Ambiguous step: 2 matching definitions")
    combo(view).isVisible shouldBe true
    val first = preview(view).getDocument.getText
    combo(view).setSelectedIndex(1)
    val second = preview(view).getDocument.getText
    second should not be first
    pin(view).doClick()
    button(view).doClick()
    val editor = FileEditorManager.getInstance(getProject).getSelectedTextEditor
    editor.getDocument.getText should include(second)
    editor.getDocument.getText.substring(editor.getCaretModel.getOffset) should startWith("Given")
    hasText(view, "Given account 42") shouldBe true
  }

  @Test
  def testSwitchingEditorsRefreshesPreviewWithoutMovingCaret(): Unit = {
    fixtures()
    val feature = myFixture.getFile.getVirtualFile
    val view = panel()
    await(view, "1 matching Scala definition")
    val other = myFixture.addFileToProject("notes.txt", "Notes")
    FileEditorManager.getInstance(getProject).openFile(other.getVirtualFile, true)
    await(view, "No feature step selected")
    FileEditorManager.getInstance(getProject).openFile(feature, true)
    await(view, "Given account 42")
  }

  @Test
  def testEmptyStateAndControlsFitNarrowToolWindow(): Unit = {
    val view = panel()
    button(view).isEnabled shouldBe false
    pin(view).isEnabled shouldBe false
    preview(view).getComponent.isVisible shouldBe false
    def layout(container: Container): Unit = {
      container.doLayout()
      container.getComponents.collect { case child: Container => child }.foreach(layout)
    }
    Seq(360, 760).foreach { width =>
      view.setSize(width, 650)
      (1 to 3).foreach(_ => layout(view))
      Seq(button(view), pin(view)).foreach { component =>
        val bounds = SwingUtilities.convertRectangle(component.getParent, component.getBounds, view)
        bounds.x should be >= 0
        bounds.x + bounds.width should be <= width
        bounds.height should be > 0
      }
    }
    fixtures()
    moveTo("ready")
    await(view, "1 matching Scala definition")
    Seq(360, 760).foreach { width =>
      view.setSize(width, 650)
      (1 to 3).foreach(_ => layout(view))
      val definitions = combo(view).getParent
      val bounds = SwingUtilities.convertRectangle(definitions.getParent, definitions.getBounds, view)
      bounds.x should be >= 0
      bounds.x + bounds.width should be <= width
      bounds.width should be > 100
      bounds.height should be > 0
      if (width == 360) {
        children(view).collect { case label: JLabel if Option(label.getText).exists(text =>
          text.startsWith("Read-only Scala") || text.startsWith("1 matching Scala")) => label
        }.foreach(_.getHeight should be > 20)
      }
    }
  }
}
