package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala._
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.inspections.{CucumberMissedExamplesInspection, CucumberStepInspection}
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinPystring, GherkinStepsHolder}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class NativeEditorBaselineSpec extends ScCucumberSpecBase {
  @Test
  def testNativeUndefinedAndAmbiguousDiagnosticsAndCreationActions(): Unit = {
    myFixture.addFileToProject("One.scala", "class One { Given(\"duplicate\") { () => () } }")
    myFixture.addFileToProject("Two.scala", "class Two { Given(\"duplicate\") { () => () } }")
    myFixture.enableInspections(new CucumberStepInspection)
    myFixture.configureByText("diagnostics.feature", "Feature: Diagnostics\n  Scenario: Diagnostics\n    Given duplicate\n    When missing<caret>\n")
    val diagnostics = myFixture.doHighlighting().asScala.flatMap(h => Option(h.getDescription)).mkString("\n")
    diagnostics.toLowerCase should include("undefined")
    diagnostics.toLowerCase should include("ambiguous")
    val actions = myFixture.getAvailableIntentions.asScala.map(_.getText).mkString("\n")
    actions.toLowerCase should include("create step")
    actions.toLowerCase should include("create all")
  }

  @Test
  def testNativeScenarioToOutlineAndMissingExamplesActions(): Unit = {
    myFixture.addFileToProject("Values.scala", "class Values { Given(\"value {int}\") { (value: Int) => () } }")
    myFixture.configureByText("outline.feature", "Feature: Outline\n  Scenario: Convert<caret>\n    Given value 1\n")
    val conversion = myFixture.getAvailableIntentions.asScala.find(_.getText.equalsIgnoreCase("Convert scenario to outline")).get
    myFixture.launchAction(conversion)
    myFixture.getFile.getText should include("Scenario Outline:")
    myFixture.getFile.getText should include("<value>")
    myFixture.getFile.getText should include("Examples:")
    myFixture.configureByText("missing-examples.feature", "Feature: Outline\n  Scenario Outline: Missing<caret>\n    Given value <n>\n")
    myFixture.enableInspections(new CucumberMissedExamplesInspection)
    myFixture.doHighlighting()
    val examples = myFixture.getAvailableIntentions.asScala.find(_.getText == "Create Examples Section").get
    myFixture.launchAction(examples)
    myFixture.getFile.getText should include("Examples:")
  }

  @Test
  def testFrenchParsingKeywordCompletionFormattingAndFolding(): Unit = {
    val file = myFixture.configureByText("french.feature", "# language: fr\nFonctionnalité: Calcul\nScénario: Calcul\nSoit valeur\nQua<caret>\n").asInstanceOf[GherkinFile]
    file.getLocaleLanguage shouldBe "fr"
    Option(myFixture.completeBasic()).toSeq.flatten.map(_.getLookupString).exists(_.startsWith("Quand")) shouldBe true
    if (myFixture.getLookup != null) com.intellij.codeInsight.lookup.LookupManager.getInstance(getProject).hideActiveLookup()
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = CodeStyleManager.getInstance(getProject).reformat(file)
    })
    PsiTreeUtil.findChildrenOfType(file, classOf[GherkinStepsHolder]).size() shouldBe 1
    file.getText should include("Fonctionnalité: Calcul")
    val builder = new org.jetbrains.plugins.cucumber.psi.GherkinFoldingBuilder
    builder.buildFoldRegions(file.getNode, myFixture.getEditor.getDocument).nonEmpty shouldBe true
  }

  @Test
  def testTypedJsonAndXmlDocStringsUseNativeLanguageInjection(): Unit = {
    val file = myFixture.configureByText("docstrings.feature", "Feature: Documents\n  Scenario: Documents\n    Then receipt\n      \"\"\"json\n      { \"accepted\": true }\n      \"\"\"\n    Then document\n      \"\"\"xml\n      <accepted>true</accepted>\n      \"\"\"\n")
    val docstrings = PsiTreeUtil.findChildrenOfType(file, classOf[GherkinPystring]).asScala.toSeq
    val languages = docstrings.flatMap(doc => Option(InjectedLanguageManager.getInstance(getProject).getInjectedPsiFiles(doc)).toSeq.flatMap(_.asScala).map(_.first.getLanguage.getID))
    languages.toSet shouldBe Set("JSON", "XML")
  }
}
