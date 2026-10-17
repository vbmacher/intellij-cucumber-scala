package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.inspections.{GherkinStyleInspection, OutlineConsistencyInspection, RequirementInspection}
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class InspectionFeaturesSpec extends ScCucumberSpecBase {
  private def descriptions = myFixture.doHighlighting().asScala.flatMap(h => Option(h.getDescription)).mkString("\n")
  @Test
  def testOutlineHeadersAcrossMultipleExamplesAndArguments(): Unit = {
    myFixture.enableInspections(new OutlineConsistencyInspection)
    myFixture.configureByText("outline.feature", "Feature: Outline\n  Scenario Outline: Outline\n    Given value <n>\n      | data |\n      | <other> |\n    Examples:\n      | n | n |\n      | 1 | 2 |\n    Examples:\n      | other |\n      | value |\n")
    val warnings = descriptions
    warnings should include("Duplicate Examples header 'n'")
    warnings should include("No Examples column 'n'")
    warnings should include("No Examples column 'other'")
  }

  @Test
  def testStyleOptionsAndSafeDuplicateTagFix(): Unit = {
    val inspection = new GherkinStyleInspection
    inspection.maxSteps = 1
    inspection.checkOrder = true
    inspection.tagPattern = "@[a-z]+"
    myFixture.enableInspections(inspection)
    myFixture.configureByText("style.feature", "Feature: Style\n  @smoke @smoke @BAD\n  Scenario: Style<caret>\n    Then end\n    Given start\n")
    val warnings = descriptions
    warnings should include("duplicate tag")
    warnings should include("configured maximum of 1")
    warnings should include("step kind appears after")
    warnings should include("configured convention")
    inspection.getOptionController.getOption("maxSteps") shouldBe Integer.valueOf(1)
    myFixture.getEditor.getCaretModel.moveToOffset(myFixture.getFile.getText.indexOf("@smoke", myFixture.getFile.getText.indexOf("@smoke") + 1) + 2)
    val fix = myFixture.getAvailableIntentions.asScala.find(_.getText == "Remove duplicate tag").get
    myFixture.launchAction(fix)
    "@smoke".r.findAllIn(myFixture.getFile.getText).size shouldBe 1
    myFixture.getFile.getText should include("@BAD")
  }

  @Test
  def testConfiguredMissingRequirementAndNativeSuppression(): Unit = {
    ScalaCucumberSettings.getInstance(getProject).requirementMappings = "@REQ-(.*) => docs/requirements.md#$1"
    myFixture.enableInspections(new RequirementInspection)
    myFixture.configureByText("requirements.feature", "@REQ-42\nFeature: Requirements\n  Scenario: Requirement\n    Given value\n")
    descriptions.toLowerCase should include("requirement")
    ScalaCucumberSettings.getInstance(getProject).requirementMappings = ""
    descriptions should not include "Configured requirement"
  }
}
