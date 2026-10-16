package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala._
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
  def testConfiguredMissingRequirementAndNativeSuppression(): Unit = {
    getProject.getService(classOf[ScalaCucumberSettings]).requirementMappings = "@REQ-(.*) => docs/requirements.md#$1"
    myFixture.enableInspections(new RequirementInspection)
    myFixture.configureByText("requirements.feature", "@REQ-42\nFeature: Requirements\n  Scenario: Requirement\n    Given value\n")
    descriptions.toLowerCase should include("requirement")
    getProject.getService(classOf[ScalaCucumberSettings]).requirementMappings = ""
    descriptions should not include "Configured requirement"
  }
}
