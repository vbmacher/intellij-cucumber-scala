package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.editor.ScHooks
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jdom.Element
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

  @Test
  def testHooksUseRuntimeTagSemanticsAndRetainUnknownConditions(): Unit = {
    myFixture.addFileToProject("Hooks.scala", """class Hooks {
      |  Before("@payments and not @slow", 5) { () => () }
      |  After("@other") { () => () }
      |  Before(dynamicTags) { () => () }
      |  BeforeStep { () => () }
      |}
      |""".stripMargin)
    myFixture.configureByText("hooks.feature", "@payments\nFeature: Hooks\n  @smoke\n  Scenario: Hooks\n    Given setup\n")
    ScCucumber.inReadAction {
      val scenario = myFixture.findElementByText("Scenario: Hooks", classOf[GherkinStepsHolder])
      val hooks = ScHooks.applicable(scenario)
      hooks.size shouldBe 3
      hooks.exists(h => h.kind == "Before" && h.order.contains(5)) shouldBe true
      hooks.exists(_.reason.contains("unknown")) shouldBe true
      hooks.exists(_.kind == "BeforeStep") shouldBe true
    }
  }
  @Test
  def testIdenticalScenarioAndExampleRowsKeepSeparateHistoryIdentities(): Unit = {
    myFixture.configureByText("identities.feature", "Feature: Identities\n  Scenario: Same\n    Given setup\n  Scenario: Same\n    Given setup\n  Scenario Outline: Rows\n    Given <value>\n    Examples:\n      | value |\n      | same |\n      | same |\n")
    ScCucumber.inReadAction {
      val locations = ScenarioSelection.locations(getProject, GlobalSearchScope.projectScope(getProject), "").toOption.get
      locations.size shouldBe 4
      locations.map(RunResults.identity).distinct.size shouldBe 4
    }
  }

  private def config(): ScCucumberRunConfiguration =
    new ScCucumberConfigurationType().getConfigurationFactories.head.createTemplateConfiguration(getProject).asInstanceOf[ScCucumberRunConfiguration]

  @Test
  def testSavedSelectionMustPreserveAllSelectors(): Unit = {
    val file = myFixture.configureByText("selected.feature", "Feature: Selected\n  Scenario: A\n    Given setup\n  Scenario: B\n    Given setup\n  Scenario: C\n    Given setup\n")
    val original = config()
    original.setModule(myFixture.getModule)
    original.featureUrl = file.getVirtualFile.getUrl
    original.glue = "steps"
    original.selectedLocations = Seq(file.getVirtualFile.getPath + ":2", file.getVirtualFile.getPath + ":4")
    val saved = new Element("configuration")
    original.writeExternal(saved)
    val restored = config()
    restored.readExternal(saved)
    ScCucumber.inReadAction {
      restored.programArguments.filter(_.contains("selected.feature")) shouldBe original.selectedLocations
    }
    original.selectedLocations = Seq.empty
    original.writeExternal(saved)
    restored.readExternal(saved)
    ScCucumber.inReadAction {
      restored.programArguments.filter(_.contains("selected.feature")) shouldBe Seq(file.getVirtualFile.getPath)
    }
  }

  @Test
  def testAutomaticGlueIncludesSeparateRegistrationPackagesAndRespectsOverrides(): Unit = {
    myFixture.addFileToProject("steps/Steps.scala", "package steps\nclass Steps { Given(\"setup\") { () => () } }")
    myFixture.addFileToProject("hooks/Hooks.scala", "package hooks\nclass Hooks { Before { () => () } }")
    myFixture.addFileToProject("parameters/Parameters.scala", "package parameters\nclass Parameters { ParameterType(\"token\", \"[A-Z]+\") { (text: String) => text } }")
    myFixture.addFileToProject("tables/Tables.scala", "package tables\nclass Tables { DataTableType { (entry: Map[String, String]) => entry(\"name\") } }")
    myFixture.addFileToProject("docstrings/DocStrings.scala", "package docstrings\nclass DocStrings { DocStringType(\"text\") { (text: String) => text } }")
    myFixture.addFileToProject("defaults/Defaults.scala", "package defaults\nclass Defaults { DefaultParameterTransformer { (value: String, tpe: java.lang.reflect.Type) => value }; DefaultDataTableCellTransformer { (value: String, tpe: java.lang.reflect.Type) => value }; DefaultDataTableEntryTransformer { (value: Map[String, String], tpe: java.lang.reflect.Type) => value } }")
    myFixture.addFileToProject("unrelated/Other.scala", "package unrelated\nclass Other { def Before(body: () => Unit): Unit = (); Before(() => ()) }")
    val file = myFixture.configureByText("glue.feature", "Feature: Glue\n  Scenario: Check\n    Given setup\n")
    val configuration = config()
    configuration.setModule(myFixture.getModule)
    configuration.featureUrl = file.getVirtualFile.getUrl
    ScCucumber.inReadAction {
      val args = configuration.programArguments
      val packages = args.sliding(2).collect { case Seq("--glue", pkg) => pkg }.toSeq
      packages shouldBe Seq("defaults", "docstrings", "hooks", "parameters", "steps", "tables")
      configuration.glue = "explicit.glue"
      configuration.programArguments.sliding(2).collect { case Seq("--glue", pkg) => pkg }.toSeq shouldBe Seq("explicit.glue")
    }
  }

  @Test
  def testHistoryMustDistinguishChangedExamplesHeaders(): Unit = {
    myFixture.configureByText("identity.feature", "Feature: Identity\n  Scenario Outline: Transfer\n    Given transfer <from> to <to>\n    Examples:\n      | from | to |\n      | A | B |\n")
    def identity() = ScCucumber.inReadAction {
      val location = ScenarioSelection.locations(getProject, GlobalSearchScope.projectScope(getProject), "").toOption.get.head
      RunResults.identity(location)
    }
    val before = identity()
    val document = myFixture.getEditor.getDocument
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = {
        val start = document.getText.indexOf("| from | to |")
        document.replaceString(start, start + "| from | to |".length, "| to | from |")
      }
    })
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    identity() should not be before
  }

}
