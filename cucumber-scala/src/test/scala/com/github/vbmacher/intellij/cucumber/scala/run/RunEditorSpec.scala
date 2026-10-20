package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import javax.swing.{JSpinner, JTextField}
import javax.swing.text.JTextComponent

@RunWith(classOf[JUnit4])
class RunEditorSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }

  private def field(controls: Seq[Component], name: String): JTextComponent =
    controls.collectFirst { case text: JTextComponent if text.getAccessibleContext.getAccessibleName == name => text }.get

  private def configuration(): ScCucumberRunConfiguration = {
    val result = new ScCucumberConfigurationType().getConfigurationFactories.head.createTemplateConfiguration(getProject)
      .asInstanceOf[ScCucumberRunConfiguration]
    result.setModule(myFixture.getModule)
    result
  }

  @Test
  def testResetAndApplyPreserveRunOptions(): Unit = {
    val file = myFixture.configureByText("run.feature", "Feature: Run\n  Scenario: Run\n    Given setup\n")
    val config = configuration()
    config.featureUrl = file.getVirtualFile.getUrl
    config.setLine(2)
    config.glue = "steps,hooks"
    config.tags = "@smoke and not @ignored"
    config.vmOptions = "-Xmx2g"
    config.programOptions = "--threads 2"
    config.workingDirectory = "/tmp/cucumber project"
    config.environment = Map("TOKEN" -> "a=b", "EMPTY" -> "")
    val editor = new ScCucumberRunEditor(getProject)
    try {
      val controls = children(editor.getComponent)
      editor.resetFrom(config)
      // The fixture uses temp:// URLs; non-local URLs must still survive editing.
      field(controls, "Feature file").getText shouldBe file.getVirtualFile.getUrl
      val restored = configuration()
      editor.applyTo(restored)
      restored.featureUrl shouldBe config.featureUrl
      restored.line shouldBe 2
      restored.getConfigurationModule.getModule shouldBe myFixture.getModule
      restored.glue shouldBe config.glue
      restored.tags shouldBe config.tags
      restored.vmOptions shouldBe config.vmOptions
      restored.programOptions shouldBe config.programOptions
      restored.workingDirectory shouldBe config.workingDirectory
      restored.environment shouldBe config.environment

      val other = myFixture.addFileToProject("with spaces/other.feature", "Feature: Other\n  Scenario: Other\n    Given setup\n")
      field(controls, "Feature file").setText(other.getVirtualFile.getUrl)
      controls.collectFirst { case spinner: JSpinner => spinner }.get.setValue(Int.box(0))
      field(controls, "Glue packages").setText("")
      controls.collectFirst { case tags: com.intellij.ui.TextFieldWithAutoCompletion[_] => tags }.get.setText("@other")
      field(controls, "VM options").setText("-Dvalue=other")
      field(controls, "Cucumber options").setText("--plugin json:results.json")
      field(controls, "Working directory").setText("")
      field(controls, "Environment variables").setText("URL=https://example.test/?a=b\nEMPTY=")
      editor.applyTo(restored)
      restored.featureUrl shouldBe other.getVirtualFile.getUrl
      restored.line shouldBe 0
      restored.glue shouldBe ""
      restored.tags shouldBe "@other"
      restored.vmOptions shouldBe "-Dvalue=other"
      restored.programOptions shouldBe "--plugin json:results.json"
      restored.workingDirectory shouldBe ""
      restored.environment shouldBe Map("URL" -> "https://example.test/?a=b", "EMPTY" -> "")
      editor.resetFrom(config)
      editor.applyTo(restored)
      restored.featureUrl shouldBe config.featureUrl
      restored.environment shouldBe config.environment
    } finally Disposer.dispose(editor)
  }

  @Test
  def testInvalidLineDoesNotSilentlySelectWholeFeature(): Unit = {
    val editor = new ScCucumberRunEditor(getProject)
    try {
      val controls = children(editor.getComponent)
      val spinner = controls.collectFirst { case value: JSpinner => value }.get
      val text = spinner.getEditor.asInstanceOf[JSpinner.DefaultEditor].getTextField
      Seq("not a line", "-1").foreach { value =>
        text.setText(value)
        intercept[ConfigurationException](editor.applyTo(configuration()))
      }
    } finally Disposer.dispose(editor)
  }

  @Test
  def testExtraDialogHeightDoesNotStretchSingleLineFields(): Unit = {
    val editor = new ScCucumberRunEditor(getProject)
    try {
      val panel = editor.getComponent
      panel.setSize(JBUI.scale(1000), JBUI.scale(800))
      def layout(container: Container): Unit = {
        container.doLayout()
        container.getComponents.collect { case child: Container => child }.foreach(layout)
      }
      layout(panel)
      val controls = children(panel)
      Seq("Feature file", "Glue packages", "VM options", "Cucumber options", "Working directory").foreach { name =>
        val text = field(controls, name).asInstanceOf[JTextField]
        withClue(name) {
          text.getHeight should be > 0
          text.getHeight should be < JBUI.scale(48)
          text.getWidth should be > panel.getWidth / 2
        }
      }
    } finally Disposer.dispose(editor)
  }
}
