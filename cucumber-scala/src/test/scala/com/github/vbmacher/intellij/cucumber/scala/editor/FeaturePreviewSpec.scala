package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.publish.{FeatureHtml, FeaturePreviewEditor}
import com.intellij.ide.ui.{LafManager, LafManagerListener}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.Color
import javax.swing.{JEditorPane, UIManager}
import javax.swing.text.{LabelView, View}

@RunWith(classOf[JUnit4])
class FeaturePreviewSpec extends ScCucumberSpecBase {
  private def feature = myFixture.configureByText("theme.feature",
    "Feature: Theme aware preview\n  Description text\n  Scenario: Visible scenario\n    Given visible step\n").asInstanceOf[GherkinFile]

  private def textColors(pane: JEditorPane): Map[String, Color] = {
    pane.setSize(800, 600)
    pane.getPreferredSize
    def leaves(view: View): Seq[(String, Color)] = view match {
      case label: LabelView =>
        val end = math.min(label.getEndOffset, pane.getDocument.getLength)
        Seq(pane.getDocument.getText(label.getStartOffset, end - label.getStartOffset).trim -> label.getForeground)
      case _ => (0 until view.getViewCount).flatMap(i => leaves(view.getView(i)))
    }
    leaves(pane.getUI.getRootView(pane)).toMap
  }

  private def assertTextColors(pane: JEditorPane, color: Color): Unit = {
    val colors = textColors(pane)
    Seq("Theme aware preview", "Description text", "Scenario: Visible scenario", "Given visible step").foreach { text =>
      withClue(text + ": ") { colors(text) shouldBe color }
    }
  }

  @Test
  def testRenderedHeadingsAndStepsAreReadableInLightAndDarkThemes(): Unit = {
    val file = feature
    Seq(false -> Color.decode("#222222"), true -> Color.decode("#dddddd")).foreach { case (dark, color) =>
      val pane = new JEditorPane("text/html", FeatureHtml.render(file, dark = dark))
      assertTextColors(pane, color)
    }
  }

  @Test
  def testOpenPreviewRefreshesWhenThemeChangesWithoutSourceEdits(): Unit = {
    val defaults = UIManager.getLookAndFeelDefaults
    val previous = defaults.get("ui.theme.is.dark")
    defaults.put("ui.theme.is.dark", false)
    val preview = new FeaturePreviewEditor(getProject, feature.getVirtualFile)
    val pane = preview.getPreferredFocusedComponent.asInstanceOf[JEditorPane]
    try {
      PlatformTestUtil.waitWithEventsDispatching("Initial feature preview", () => pane.getText.contains("Visible scenario"), 10)
      Seq(true -> Color.decode("#dddddd"), false -> Color.decode("#222222")).foreach { case (dark, color) =>
        defaults.put("ui.theme.is.dark", dark)
        ApplicationManager.getApplication.getMessageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(LafManager.getInstance())
        PlatformTestUtil.waitWithEventsDispatching("Feature preview theme change", () => textColors(pane).get("Theme aware preview").contains(color), 10)
        assertTextColors(pane, color)
      }
    } finally {
      Disposer.dispose(preview)
      if (previous == null) defaults.remove("ui.theme.is.dark") else defaults.put("ui.theme.is.dark", previous)
    }
  }
}
