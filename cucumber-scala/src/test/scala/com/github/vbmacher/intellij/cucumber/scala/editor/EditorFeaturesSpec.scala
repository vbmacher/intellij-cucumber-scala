package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala._
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep, GherkinTag}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class EditorFeaturesSpec extends ScCucumberSpecBase {
  @Test
  def testNumberingOnlyChangesTitlesAndIsIdempotent(): Unit = {
    val file = myFixture.configureByText("titles.feature", "@smoke\nFeature: Titles\n  Scenario: First\n    Given unchanged\n  Scenario Outline: Second\n    Given <value>\n    Examples:\n      | value |\n      | 1 |\n")
    val edits = NumberScenarios.plan(Seq(file), None, 1, 2, "")
    edits.map(_.after) shouldBe Seq("01 - First", "02 - Second")
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable { override def run(): Unit = NumberScenarios.apply(edits) })
    file.getText should include("Scenario: 01 - First")
    file.getText should include("Scenario Outline: 02 - Second")
    file.getText should include("@smoke\nFeature: Titles")
    NumberScenarios.plan(Seq(file), None, 1, 2, "") shouldBe empty
  }

  @Test
  def testHtmlRendersLocalEscapedTablesAndEveryExamplesBlock(): Unit = {
    val file = myFixture.configureByText("unicode.feature", "Feature: Crédits\n  Scenario Outline: Safe <value>\n    Given <script>alert(1)</script>\n    Examples: First\n      | value |\n      | A\\|B |\n    Examples: Second\n      | value |\n      | hello |\n").asInstanceOf[GherkinFile]
    val html = FeatureHtml.render(file)
    html should include("Crédits")
    html should include("&lt;script&gt;")
    html should not include "<script>"
    html should include("A|B")
    html should include("Examples: First")
    html should include("Examples: Second")
    html should include("source:")
    html should include("default-src 'none'")
  }

  @Test
  def testFuzzySuggestionsPreserveArgumentsWithoutChangingResolution(): Unit = {
    myFixture.addFileToProject("Accounts.scala", "class Accounts { Given(\"account {string} exists\") { (name: String) => () } }")
    myFixture.configureByText("accounts.feature", "Feature: Accounts\n  Scenario: Accounts\n    Given account \"Alice\" exist\n")
    ScCucumber.inReadAction {
      val step = myFixture.findElementByText("Given account \"Alice\" exist", classOf[GherkinStep])
      ScCucumber.matchingSteps(step) shouldBe empty
      val suggestions = ScFuzzySteps.suggestions(step)
      suggestions.map(_.wording) should contain("account \"Alice\" exists")
      step.getName shouldBe "account \"Alice\" exist"
      ScCucumber.matchingSteps(step) shouldBe empty
    }
  }

  @Test
  def testRequirementReferencesResolveEveryExplicitLocalId(): Unit = {
    val settings = getProject.getService(classOf[ScalaCucumberSettings])
    settings.requirementMappings = "@AC-(.*) => requirements.md#AC-$1"
    try {
      val doc = myFixture.addFileToProject("requirements.md", "# AC-PAY-12\n\nSecond criterion {#AC-PAY-12}\n")
      myFixture.configureByText("requirements.feature", "@AC-PAY-12\nFeature: Requirements\n  Scenario: Match\n    Given setup\n")
      ScCucumber.inReadAction {
        val tag = PsiTreeUtil.findChildOfType(myFixture.getFile, classOf[GherkinTag])
        val targets = RequirementReferences.targets(tag)
        targets.size shouldBe 2
        targets.foreach(_.getContainingFile shouldBe doc)
        targets.map(_.getTextOffset).distinct.size shouldBe 2
        tag.getReferences.collect { case r: com.intellij.psi.PsiPolyVariantReference => r }.flatMap(_.multiResolve(false)).length shouldBe 2
      }
    } finally settings.requirementMappings = ""
  }
}
