package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.github.vbmacher.intellij.cucumber.scala.tables.ScalaTableClass
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class SuiteAnalysisSpec extends ScCucumberSpecBase {
  @Test
  def testSbtSourceSetsUseTheirImportedProjectInsteadOfIdeModuleNames(): Unit = {
    val system = new ProjectSystemId("SBT")
    val properties = ExternalSystemModulePropertyManager.getInstance(myFixture.getModule)
    val manager = ExternalProjectsManagerImpl.getInstance(getProject)
    def registerBuild(path: String): Seq[SuiteAnalysis.ModuleGroup] = {
      val project = new ProjectData(system, "workspace", path, path)
      val root = new DataNode(ProjectKeys.PROJECT, project, null)
      val projectId = s"payments [file://$path/]"
      val owner = root.createChild(ProjectKeys.MODULE, new ModuleData(projectId, system, "JAVA_MODULE", "payments", path, path + "/payments"))
      val sourceSets = Seq("main", "test").map { sourceSet =>
        val module = new ModuleData(s"payments:$sourceSet [file://$path/]", system, "JAVA_MODULE", s"workspace.payments.$sourceSet", path, path + "/payments")
        owner.createChild(ProjectKeys.MODULE, module)
        module
      }
      manager.updateExternalProjectData(new InternalExternalProjectInfo(system, path, root))
      sourceSets.map { module =>
        properties.setExternalOptions(system, module, project)
        properties.setExternalModuleType("sbtSourceSet")
        ScCucumber.inReadAction(SuiteAnalysis.moduleGroup(myFixture.getModule))
      }
    }
    val builds = Seq("/suite-first-build", "/suite-second-build")
    try {
      val first = registerBuild(builds.head)
      first.map(_.name) shouldBe Seq("payments", "payments")
      first.distinct.size shouldBe 1
      val second = registerBuild(builds(1))
      second.distinct.size shouldBe 1
      first.head.id should not be second.head.id
      myFixture.configureByText("payments.feature", "Feature: Payments\n Scenario: Account\n  Given account\n")
      ScCucumber.inReadAction {
        val report = SuiteAnalysis.scan(getProject, Seq(myFixture.getModule), GlobalSearchScope.projectScope(getProject))
        report.findings.find(_.category == "Scenarios").get.moduleGroup shouldBe second.head
      }
    } finally {
      properties.unlinkExternalOptions()
      builds.foreach(path => manager.forgetExternalProjectData(system, path))
    }
  }

  @Test
  def testHealthSeparatesUsagesDefinitionsAndScenarios(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"account {int}\") { (id: Int) => () }; Given(\"account {word}\") { (id: String) => () }; Then(\"unused\") { () => () } }")
    myFixture.addFileToProject("first.feature", "Feature: First\n  Scenario: First\n    Given account 42\n    Then missing\n")
    myFixture.configureByText("second.feature", "Feature: Second\n  Scenario: Second\n    Given account 42\n    Then missing\n")
    ScCucumber.inReadAction {
      val report = SuiteAnalysis.scan(getProject, Seq(myFixture.getModule), GlobalSearchScope.projectScope(getProject))
      report.features shouldBe 2
      report.scenarios shouldBe 2
      report.stepUsages shouldBe 4
      report.definitions shouldBe 3
      report.findings.count(_.category == "Undefined steps") shouldBe 2
      val ambiguous = report.findings.filter(_.category == "Ambiguous steps")
      ambiguous.size shouldBe 2
      ambiguous.map(_.source.location.split('/').last).toSet shouldBe Set("first.feature:3", "second.feature:3")
      ambiguous.foreach { finding =>
        finding.source.target.getContainingFile.getName should endWith(".feature")
        finding.related.map(_.label).toSet shouldBe Set("account {int}", "account {word}")
        finding.related.map(_.target).distinct.size shouldBe 2
        finding.related.foreach(_.location should endWith("Steps.scala:1"))
      }
      report.findings.count(_.category == "Unused definitions") shouldBe 1
      val definition = report.findings.find(f => f.category == "Step definitions" && f.source.label.startsWith("account {int}")).get
      definition.related.map(_.location.split('/').last).toSet shouldBe Set("first.feature:2", "second.feature:2")
      definition.keyword shouldBe Some("Given")
      report.findings.count(_.category == "Duplicate scenarios") shouldBe 2
    }
  }

  @Test
  def testCaseClassInferenceUsesEveryRowAndQuotesIdentifiers(): Unit = {
    ScalaTableClass.source("TransferRow", Seq(Seq("account", "amount", "active"), Seq("Alice", "10", "true"), Seq("Bob", "20", "false"))) shouldBe
      Right("case class TransferRow(account: String, amount: Int, active: Boolean)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("type", "mixed", "missing"), Seq("a", "1", ""), Seq("b", "text", "2"))) shouldBe
      Right("case class DataRow(`type`: String, mixed: String, missing: String)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("x", "x"), Seq("1", "2"))).isLeft shouldBe true
  }
}
