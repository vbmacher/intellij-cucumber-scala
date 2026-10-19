package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.run.{CucumberMessages, RunResults}
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class FailureContextSpec extends ScCucumberSpecBase {
  private val feature = """Feature: Payments
  Background:
    Given an account
  Scenario Outline: Transfer
    Then transfer <amount> succeeds
    Examples:
      | amount |
      | 10     |
      | 20     |
"""
  private def record(file: PsiFile, run: String = "run", row: Int = 8, status: String = "FAILED", step: Int = 5): RunResults.Decoration = {
    val store = RunResults.getInstance(getProject)
    val context = RunResults.Context(PsiModificationTracker.getInstance(getProject).getModificationCount, "revision", "local", Map.empty, 123L)
    store.record(CucumberMessages.StepResult(run, file.getVirtualFile.getUrl, step, file.getVirtualFile.getUrl + ":" + row,
      status, 1L, "AssertionError: expected 90, actual 100\n  at Steps.apply(Steps.scala:3)"), context)
    store.failures(file.getVirtualFile.getUrl).find(d => d.result.run == run && d.result.line == step).get
  }
  private def collect(result: RunResults.Decoration): FailureContext.Snapshot =
    inReadAction(FailureContext.collect(com.intellij.psi.PsiManager.getInstance(getProject).findFile(myFixture.findFileInTempDir("payments.feature")).asInstanceOf[GherkinFile], result)).fold(message => fail(message), identity)

  @Test
  def testSelectedExampleBackgroundFullScalaBodyAndConcreteErrorNavigation(): Unit = {
    val scala = myFixture.addFileToProject("Steps.scala", """class Steps {
  Then("transfer {int} succeeds") { amount: Int =>
    assert(amount == 90)
  }
}
""")
    val file = myFixture.addFileToProject("payments.feature", feature)
    val snapshot = collect(record(file))
    snapshot.text should include("Given an account")
    snapshot.text should include("| 10     |")
    snapshot.text should include("assert(amount == 90)")
    snapshot.facts should include("not proof of the runtime binding")
    snapshot.sources.find(_.label == "Static Scala match").get.text should include("assert(amount == 90)")
    val error = snapshot.sources.find(_.label.startsWith("Error location")).get
    error.file shouldBe scala.getVirtualFile
    error.line shouldBe 3
    error.offset shouldBe scala.getText.indexOf("    assert")
    error.open(getProject)
    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject).getSelectedTextEditor.getCaretModel.getOffset shouldBe error.offset
    snapshot.problem(getProject) shouldBe None
  }

  @Test
  def testUnresolvedAndAmbiguousBindingsAreExplicit(): Unit = {
    val file = myFixture.addFileToProject("payments.feature", feature)
    collect(record(file)).text should include("No static Scala definition")
    myFixture.addFileToProject("First.scala", """class First { Then("transfer {int} succeeds") { n: Int => assert(n == 90) } }""")
    myFixture.addFileToProject("Second.scala", """class Second { Then("transfer {int} succeeds") { n: Int => assert(n == 100) } }""")
    val snapshot = collect(record(file, "ambiguous"))
    snapshot.facts should include("Ambiguous: 2")
    snapshot.sources.count(_.label == "Static Scala match") shouldBe 2
    snapshot.text should include("None is selected as the executed binding")
  }

  @Test
  def testSelectionDoesNotMixExamplesOrRunsAndHooksClaimNoStepBinding(): Unit = {
    val file = myFixture.addFileToProject("payments.feature", feature).asInstanceOf[GherkinFile]
    record(file, "first", 8)
    record(file, "second", 9)
    record(file, "third", 8)
    val selected = inReadAction(FailureContext.candidates(file, feature.indexOf("| 10")))
    selected.map(_.result.run).toSet shouldBe Set("first", "third")
    val hook = collect(record(file, "hook", 8, step = 8))
    hook.text should include("No step binding is claimed")
    hook.sources.exists(_.label == "Static Scala match") shouldBe false
  }

  @Test
  def testDisabledDecorationsStillRetainOptedInFailuresAndRejectStaleOrClearedContext(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.failureExplanations = true
    settings.resultDecorations = false
    try {
      val file = myFixture.addFileToProject("payments.feature", feature)
      myFixture.configureFromExistingVirtualFile(file.getVirtualFile)
      val result = record(file)
      val snapshot = collect(result)
      RunResults.getInstance(getProject).clearDecorations()
      snapshot.problem(getProject).get should include("cleared or replaced")
      inReadAction(FailureContext.collect(com.intellij.psi.PsiManager.getInstance(getProject).findFile(myFixture.findFileInTempDir("payments.feature")).asInstanceOf[GherkinFile], result)).left.toOption.get should include("no longer available")
      val current = record(file, "current")
      val reviewed = collect(current)
      WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
        override def run(): Unit = myFixture.getEditor.getDocument.insertString(0, "# changed\n")
      })
      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
      reviewed.problem(getProject).get should include("Source changed")
      inReadAction(FailureContext.collect(file.asInstanceOf[GherkinFile], current)).left.toOption.get should include("Source changed")
    } finally { settings.failureExplanations = false; settings.resultDecorations = true }
  }
}
