package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.{ScalaCucumberConfigurable, ScalaCucumberSettings}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.Container
import javax.swing.JTextField

@RunWith(classOf[JUnit4])
class ScenarioSearchSpec extends ScCucumberSpecBase {
  private def documents = inReadAction(ScenarioSearch.documents(getProject, GlobalSearchScope.projectScope(getProject)))
  private def provider(name: String = "test-model")(f: String => Vector[Double]): ScenarioSearch.Embeddings = new ScenarioSearch.Embeddings {
    override val id: String = name
    override def embed(text: String, checkCanceled: () => Unit): Vector[Double] = { checkCanceled(); f(text) }
  }

  @Test
  def testExactSearchIncludesBodiesAndOutlinesWithoutAnyModel(): Unit = {
    myFixture.addFileToProject("cash.feature", "Feature: Cash\n  Background:\n    Given logged in\n  Scenario: Withdrawal\n    Then insufficient funds\n  Scenario Outline: Transfer <amount>\n    Then rejected <amount>\n")
    val docs = documents
    docs.map(_.title).toSet shouldBe Set("Withdrawal", "Transfer <amount>")
    ScenarioSearch.exact(docs, "INSUFFICIENT FUNDS").map(_.document.title) shouldBe Seq("Withdrawal")
    ScenarioSearch.exact(docs, "no such wording") shouldBe empty
    ScenarioSearch.exact(docs, "").size shouldBe 2
    inReadAction {
      val result = ScenarioSearch.exact(docs, "Withdrawal").head.document
      result.text should include("Then insufficient funds")
      result.line shouldBe 4
      val navigation = result.navigation(getProject).get
      navigation.getFile.getName shouldBe "cash.feature"
      navigation.getOffset shouldBe myFixture.findFileInTempDir("cash.feature").contentsToByteArray().map(_.toChar).mkString.indexOf("Scenario: Withdrawal")
      navigation.navigate(true)
      FileEditorManager.getInstance(getProject).getSelectedTextEditor.getCaretModel.getOffset shouldBe navigation.getOffset
    }
  }

  @Test
  def testSemanticRankingKeepsTiedCandidatesAndSourceLocationsDistinct(): Unit = {
    myFixture.addFileToProject("payments.feature", "Feature: Payments\n  Scenario: Transfer rejected for insufficient funds\n    Then transfer declined\n")
    myFixture.addFileToProject("cash.feature", "Feature: Cash\n  Scenario: Withdrawal exceeds available balance\n    Then withdrawal declined\n")
    myFixture.addFileToProject("login.feature", "Feature: Login\n  Scenario: Login succeeds\n    Then welcome\n")
    val embedding = provider() { text => if (text.contains("Login")) Vector(0, 1) else Vector(1, 0) }
    val docs = documents
    val index = ScenarioSearch.build(docs, embedding)
    val hits = ScenarioSearch.semantic(docs, "prevent spending more money than is available", embedding, index)
    hits.map(_.document.title).toSet shouldBe Set("Transfer rejected for insufficient funds", "Withdrawal exceeds available balance")
    hits.map(_.document.path).distinct.size shouldBe 2
    hits.foreach(_.similarity shouldBe Some(1.0))
    inReadAction { hits.foreach(_.document.navigation(getProject).isDefined shouldBe true) }
  }

  @Test
  def testChangedDeletedAndExcludedFilesCannotUseOldVectorsOrNavigation(): Unit = {
    val first = myFixture.addFileToProject("first.feature", "Feature: First\n  Scenario: Original\n    Given original\n")
    val second = myFixture.addFileToProject("second.feature", "Feature: Second\n  Scenario: Other\n    Given other\n")
    val embedding = provider()(_ => Vector(1, 0))
    val before = documents
    val index = ScenarioSearch.build(before, embedding)
    val scoped = inReadAction(ScenarioSearch.documents(getProject, GlobalSearchScope.fileScope(first)))
    scoped.map(_.title) shouldBe Seq("Original")
    intercept[IllegalArgumentException](ScenarioSearch.semantic(scoped, "query", embedding, index))
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable { override def run(): Unit = {
      val document = PsiDocumentManager.getInstance(getProject).getDocument(first)
      document.setText("Feature: First\n  Scenario: Changed\n    Given changed\n")
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
      second.delete()
    } })
    val after = documents
    after.map(_.title) shouldBe Seq("Changed")
    ScenarioSearch.exact(after, "original") shouldBe empty
    inReadAction { before.foreach(_.navigation(getProject) shouldBe None) }
    intercept[IllegalArgumentException](ScenarioSearch.semantic(after, "query", embedding, index))
    val rebuilt = ScenarioSearch.build(after, embedding)
    ScenarioSearch.semantic(after, "query", embedding, rebuilt).map(_.document.title) shouldBe Seq("Changed")
  }

  @Test
  def testCancellationModelChangesAndInvalidVectorsLeaveExactSearchUsable(): Unit = {
    myFixture.addFileToProject("test.feature", "Feature: Test\n  Scenario: Searchable\n    Given text\n")
    val docs = documents
    val embedding = provider()(_ => Vector(1, 0))
    val index = ScenarioSearch.build(docs, embedding)
    intercept[ProcessCanceledException](ScenarioSearch.build(docs, embedding, () => throw new ProcessCanceledException()))
    intercept[ProcessCanceledException](ScenarioSearch.semantic(docs, "query", embedding, index, () => throw new ProcessCanceledException()))
    intercept[IllegalArgumentException](ScenarioSearch.semantic(docs, "query", provider("changed")(_ => Vector(1, 0)), index))
    intercept[IllegalArgumentException](ScenarioSearch.build(docs, provider()(_ => Vector(Double.NaN, 1))))
    intercept[IllegalArgumentException](ScenarioSearch.build(docs, provider()(_ => Vector(0, 0))))
    intercept[IllegalArgumentException](ScenarioSearch.semantic(docs, "query", provider()(_ => Vector(1, 0, 0)), index))
    intercept[java.io.IOException](ScenarioSearch.build(docs, provider()(_ => throw new java.io.IOException("model unavailable"))))
    ScenarioSearch.exact(docs, "searchable").size shouldBe 1
  }

  @Test
  def testSettingsPersistTheSelectedOllamaModelAndUrl(): Unit = {
    val configurable = new ScalaCucumberConfigurable(getProject)
    val config = ScalaCucumberSettings.getInstance(getProject)
    val oldModel = config.embeddingModel
    val oldUrl = config.ollamaUrl
    def fields(container: Container): Seq[JTextField] = container.getComponents.toSeq.flatMap {
      case field: JTextField => Seq(field)
      case child: Container => fields(child)
      case _ => Seq.empty
    }
    try {
      val inputs = fields(configurable.createComponent())
      configurable.reset()
      inputs.find(_.getText == oldModel).get.setText("custom-embedding-model")
      inputs.find(_.getText == oldUrl).get.setText("http://localhost:11435")
      configurable.isModified shouldBe true
      configurable.apply()
      config.embeddingModel shouldBe "custom-embedding-model"
      config.ollamaUrl shouldBe "http://localhost:11435"
      val restored = new ScalaCucumberSettings()
      restored.loadState(config.getState)
      restored.embeddingModel shouldBe "custom-embedding-model"
      restored.ollamaUrl shouldBe "http://localhost:11435"
      configurable.reset()
      configurable.isModified shouldBe false
    } finally { config.embeddingModel = oldModel; config.ollamaUrl = oldUrl }
  }
}
