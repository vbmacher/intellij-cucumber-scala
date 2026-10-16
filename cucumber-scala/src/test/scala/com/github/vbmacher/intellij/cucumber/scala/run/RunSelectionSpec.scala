package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala._
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class RunSelectionSpec extends ScCucumberSpecBase {
  @Test
  def testExampleRowsHaveDistinctLocationSelectorsAndInheritedTags(): Unit = {
    myFixture.configureByText("outlines.feature",
      """@payments
        |Feature: Payments
        |  @smoke
        |  Scenario Outline: Duplicate title
        |    Given <amount>
        |    @first
        |    Examples:
        |      | amount |
        |      | 10 |
        |    @second
        |    Examples:
        |      | amount |
        |      | -1 |
        |""".stripMargin)
    ScCucumber.inReadAction {
      val all = ScenarioSelection.locations(getProject, GlobalSearchScope.projectScope(getProject), "@payments and @smoke").toOption.get
      all.size shouldBe 2
      ScenarioSelection.tags(all.head.scenario) should contain allOf("@first", "@second", "@payments")
      val impossible = io.cucumber.tagexpressions.TagExpressionParser.parse("@first and @second")
      import scala.jdk.CollectionConverters._
      ScenarioSelection.tagSets(all.head.scenario).exists(tags => impossible.evaluate(tags.asJava)) shouldBe false
      all.map(_.selector).distinct.size shouldBe 2
      val second = ScenarioSelection.locations(getProject, GlobalSearchScope.projectScope(getProject), "@second and not @first").toOption.get
      second.size shouldBe 1
      val selected = new ScCucumberRunProducer().selection(second.head.target.getFirstChild).get
      selected._2 shouldBe second.head.line
    }
  }

  @Test
  def testConfigurationTracksLineEditsAndPassesTagsLiterally(): Unit = {
    val file = myFixture.configureByText("run.feature", "Feature: Run\n  Scenario: Run\n    Given setup\n")
    val config = new ScCucumberConfigurationType().getConfigurationFactories.head.createTemplateConfiguration(getProject).asInstanceOf[ScCucumberRunConfiguration]
    config.setModule(myFixture.getModule)
    config.featureUrl = file.getVirtualFile.getUrl
    config.setLine(2)
    config.tags = "@smoke and not @$(touch /tmp/never-run)"
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = myFixture.getEditor.getDocument.insertString(0, "# added line\n")
    })
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    config.line shouldBe 3
    ScCucumber.inReadAction {
      val args = config.programArguments
      args(args.indexOf("--tags") + 1) shouldBe config.tags
      args.last should endWith("run.feature:3")
    }
  }


}
