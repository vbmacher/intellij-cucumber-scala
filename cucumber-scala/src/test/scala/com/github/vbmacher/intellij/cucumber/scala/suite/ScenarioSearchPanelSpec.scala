package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing._

@RunWith(classOf[JUnit4])
class ScenarioSearchPanelSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(panel: ScenarioSearchPanel, text: String): JButton =
    children(panel).collectFirst { case button: JButton if button.getText == text => button }.get
  private def status(panel: ScenarioSearchPanel, text: String): Boolean =
    children(panel).exists { case label: JLabel => Option(label.getText).exists(_.contains(text)); case _ => false }
  private def await(panel: ScenarioSearchPanel, text: String): Unit =
    PlatformTestUtil.waitWithEventsDispatching(text, () => status(panel, text), 10)
  private def mode(panel: ScenarioSearchPanel, text: String): Unit =
    children(panel).collectFirst { case combo: JComboBox[_] if combo.getItemAt(0) == "Exact text" => combo }.get.setSelectedItem(text)
  private def results(panel: ScenarioSearchPanel): JList[_] = children(panel).collectFirst { case list: JList[_] => list }.get
  private def panel(embedding: ScenarioSearch.Embeddings): ScenarioSearchPanel = {
    val value = new ScenarioSearchPanel(getProject, (_, _) => embedding)
    Disposer.register(getTestRootDisposable, value)
    value
  }

  @Test
  def testSearchLayoutAndContextualIndexControls(): Unit = {
    val embedding = new ScenarioSearch.Embeddings {
      override val id: String = "fixture model"
      override def embed(text: String, check: () => Unit): Vector[Double] = Vector(1, 0)
    }
    val view = panel(embedding)
    def visible(component: Component): Boolean =
      Iterator.iterate(component)(_.getParent).takeWhile(c => c != null && c != view).forall(_.isVisible)
    def layout(container: Container): Unit = {
      container.doLayout()
      container.getComponents.collect { case child: Container => child }.foreach(layout)
    }
    button(view, "Go to source").isEnabled shouldBe false
    button(view, "Cancel").isVisible shouldBe false
    button(view, "Delete index").isEnabled shouldBe false
    visible(button(view, "Rebuild index")) shouldBe false
    val query = children(view).collectFirst { case field: JTextField => field }.get
    query.getAccessibleContext.getAccessibleName shouldBe "Scenario query"
    Seq(360, 760).foreach { width =>
      view.setSize(width, 700)
      (1 to 3).foreach(_ => layout(view))
      button(view, "Search").getHeight should be <= 40
      val fieldBounds = SwingUtilities.convertRectangle(query.getParent, query.getBounds, view)
      fieldBounds.getWidth should be >= 150.0
      val list = results(view)
      val listBounds = SwingUtilities.convertRectangle(list, list.getVisibleRect, view)
      listBounds.getY should be < 200.0
      list.getScrollableTracksViewportWidth shouldBe true
    }
    mode(view, "Semantic")
    visible(button(view, "Rebuild index")) shouldBe true
    mode(view, "Exact text")
    visible(button(view, "Rebuild index")) shouldBe false
    children(view).collectFirst { case toggle: JToggleButton if toggle.getText == "Semantic index" => toggle }.get.doClick()
    visible(button(view, "Rebuild index")) shouldBe true
  }

  @Test
  def testSearchSnippetNavigationSourceInvalidationAndIndexDeletion(): Unit = {
    myFixture.configureByText("payments.feature", "Feature: Payments\n  Scenario: Insufficient funds\n    Then transfer rejected\n")
    val embedding = new ScenarioSearch.Embeddings {
      override val id: String = "fixture model"
      override def embed(text: String, check: () => Unit): Vector[Double] = Vector(1, 0)
    }
    val view = panel(embedding)
    button(view, "Search").doClick()
    await(view, "1 scenario · Exact text")
    results(view).getModel.getSize shouldBe 1
    button(view, "Go to source").isEnabled shouldBe true
    val hitList = results(view).asInstanceOf[JList[ScenarioSearch.Hit]]
    val cell = hitList.getCellRenderer.getListCellRendererComponent(hitList, hitList.getModel.getElementAt(0), 0, true, false)
    cell.setSize(300, 60)
    cell.validate()
    val labels = children(cell.asInstanceOf[Container]).collect { case label: JLabel => label }
    labels.exists(_.getText == "Insufficient funds") shouldBe true
    labels.exists(label => label.getText.contains("payments.feature:") && label.getHeight > 0) shouldBe true
    children(view).collectFirst { case area: JTextArea => area.getText }.get should include("Then transfer rejected")
    button(view, "Go to source").doClick()
    myFixture.getEditor.getCaretModel.getOffset shouldBe myFixture.getFile.getText.indexOf("Scenario:")
    myFixture.getEditor.getCaretModel.moveToOffset(0)
    val list = results(view)
    val action = list.getActionMap.get(list.getInputMap.get(KeyStroke.getKeyStroke("ENTER")))
    action.actionPerformed(new java.awt.event.ActionEvent(list, 0, "enter"))
    myFixture.getEditor.getCaretModel.getOffset shouldBe myFixture.getFile.getText.indexOf("Scenario:")
    button(view, "Rebuild index").doClick()
    await(view, "Semantic index ready")
    mode(view, "Semantic")
    children(view).collectFirst { case field: JTextField => field }.get.setText("prevent overspending")
    button(view, "Search").doClick()
    await(view, "1 scenario · Semantic")
    results(view).getModel.getSize shouldBe 1
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = myFixture.getEditor.getDocument.insertString(myFixture.getEditor.getDocument.getTextLength, "    And changed\n")
    })
    results(view).getModel.getSize shouldBe 0
    button(view, "Search").doClick()
    status(view, "Build the semantic index first") shouldBe true
    mode(view, "Exact text")
    children(view).collectFirst { case field: JTextField => field }.get.setText("changed")
    button(view, "Search").doClick()
    await(view, "1 scenario · Exact text")
    button(view, "Rebuild index").doClick()
    await(view, "Semantic index ready")
    button(view, "Delete index").doClick()
    mode(view, "Semantic")
    button(view, "Search").doClick()
    status(view, "Build the semantic index first") shouldBe true
  }

  @Test
  def testProviderFailureAndDeletedInFlightIndexCannotDisableExactSearch(): Unit = PlatformTestUtil.withSystemProperty[RuntimeException](
    "intellij.progress.task.ignoreHeadless", "true", () => {
      myFixture.configureByText("test.feature", "Feature: Test\n  Scenario: Account\n    Given balance\n")
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val finished = new CountDownLatch(1)
      val fail = new AtomicBoolean(true)
      val embedding = new ScenarioSearch.Embeddings {
        override val id: String = "fixture model"
        override def embed(text: String, check: () => Unit): Vector[Double] = {
          if (fail.get()) throw new java.io.IOException("model unavailable")
          entered.countDown()
          try { release.await(10, TimeUnit.SECONDS); Vector(1, 0) } finally finished.countDown()
        }
      }
      val view = panel(embedding)
      button(view, "Rebuild index").doClick()
      await(view, "model unavailable")
      results(view).asInstanceOf[com.intellij.ui.components.JBList[_]].getEmptyText.getText shouldBe "Search unavailable"
      button(view, "Search").doClick()
      await(view, "1 scenario · Exact text")
      fail.set(false)
      button(view, "Rebuild index").doClick()
      try {
        PlatformTestUtil.waitWithEventsDispatching("embedding started", () => entered.getCount == 0, 10)
        button(view, "Search").isEnabled shouldBe false
        button(view, "Cancel").isVisible shouldBe true
        button(view, "Cancel").doClick()
        button(view, "Search").isEnabled shouldBe true
        button(view, "Cancel").isVisible shouldBe false
        results(view).asInstanceOf[com.intellij.ui.components.JBList[_]].getEmptyText.getText shouldBe "Search cancelled"
        status(view, "Cancelled") shouldBe true
        button(view, "Delete index").doClick()
      } finally release.countDown()
      PlatformTestUtil.waitWithEventsDispatching("embedding finished", () => finished.getCount == 0, 10)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      mode(view, "Semantic")
      button(view, "Search").doClick()
      status(view, "Build the semantic index first") shouldBe true
      mode(view, "Exact text")
      button(view, "Search").doClick()
      await(view, "1 scenario · Exact text")
  })
}
