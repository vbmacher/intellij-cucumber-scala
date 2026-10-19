package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.DumbModeTestUtils
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class DraftContextSpec extends ScCucumberSpecBase {
  private def context(source: String, selection: Option[String] = None): DraftContext.Snapshot = {
    val file = myFixture.addFileToProject(s"draft-${System.nanoTime()}.feature", source.replace("<caret>", ""))
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile)
    val caret = source.indexOf("<caret>").max(0)
    val range = selection.map { text => val start = file.getText.indexOf(text); start -> (start + text.length) }
    inReadAction(DraftContext.collect(file.asInstanceOf[GherkinFile], myFixture.getEditor.getDocument, caret, range))
      .fold(fail(_), identity)
  }
  private def draft(text: String): ScenarioDraft = ScenarioDraft("Boundary", "Lower boundary; confirm expected behavior.", text)

  @Test
  def testWholeFileSelectionIncludingTrailingNewlineUsesLastScenario(): Unit = {
    val source = "Feature: Payments\n  Scenario: First\n    Given one\n  Scenario: Last\n    Given two\n"
    val snapshot = context(source, Some(source))
    snapshot.text should include(source)
    snapshot.offset should be >= source.indexOf("Given two")
    inReadAction(snapshot.validate(Seq(draft("Scenario: Zero\n  Given zero")))).errors shouldBe empty
  }

  @Test
  def testSelectionIncludesApplicableDescriptionsAndBackgroundsOnly(): Unit = {
    val source = """Feature: Payments
                   |  Money transfer rules
                   |  Background:
                   |    Given an account
                   |  Rule: Transfers
                   |    No overdrafts
                   |    Background:
                   |      Given a verified account
                   |    Scenario Outline: Transfer <amount>
                   |      When transfer <amount>
                   |      Then accepted
                   |      Examples:
                   |        | amount |
                   |        | 10     |
                   |  Rule: Other
                   |    Scenario: Unrelated
                   |      Given private unrelated context
                   |""".stripMargin
    val snapshot = context(source, Some("When transfer <amount>"))
    snapshot.text should include("Money transfer rules")
    snapshot.text should include("No overdrafts")
    snapshot.text should include("Given an account")
    snapshot.text should include("Given a verified account")
    snapshot.text should include("When transfer <amount>")
    snapshot.text should not include "private unrelated context"
    snapshot.text should not include "Then accepted"
    snapshot.indent shouldBe "    "
    val repeated = context(source.replace("Given a verified account", "Given an account"), Some("When transfer <amount>"))
    "Given an account".r.findAllIn(repeated.text).size shouldBe 2
    val outline = context(source.replace("When transfer", "<caret>When transfer"))
    outline.text should include("Examples:")
    outline.text should include("| 10")
  }

  @Test
  def testPreviewResolvesOriginalModuleAndReportsAmbiguousAndUnresolvedSteps(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"account\") { () => () }; When(\"transfer {int}\") { (i: Int) => () } }")
    myFixture.addFileToProject("OtherSteps.scala", "class OtherSteps { When(\"transfer {int}\") { (i: Int) => () } }")
    val snapshot = context("Feature: Payments\n  Scenario: <caret>Existing\n    Given account\n")
    val result = inReadAction(snapshot.validate(Seq(draft("Scenario: Zero\n  Given account\n  When transfer 0\n  Then rejected"))))
    result.errors shouldBe empty
    result.summary should include("Step definition found: account")
    result.summary should include("Ambiguous: transfer 0")
    result.summary should include("Steps.scala:1")
    result.summary should include("OtherSteps.scala:1")
    result.summary should include("Unresolved: rejected")
    result.summary should include("Not executed")
    snapshot.document.getText should not include "Scenario: Zero"
  }

  @Test
  def testInvalidGherkinAndIncompleteOutlinesCannotBeInserted(): Unit = {
    val snapshot = context("Feature: Payments\n  Scenario: <caret>Existing\n    Given account\n")
    val original = snapshot.document.getText
    Seq("not Gherkin", "Scenario: Empty", "Scenario: \n  Given account", "Background: Unexpected\n  Given account",
      "Feature: Another\n  Scenario: Injected\n    Given account", "Rule: Another\n  Scenario: Injected\n    Given account",
      "Scenario Outline: Missing\n  Given account <id>", "Scenario: One\n  Given account\nScenario: Two\n  Given account",
      "Scenario: Broken\n  Given account\n  NotAStep account", "Scenario: Broken docstring\n  Given account\n    \"\"\"\n    unfinished",
      "Scenario: Bad table\n  Given account\n    | a | b |\n    | c |").foreach { text =>
      val value = draft(text)
      withClue(text) {
        inReadAction(snapshot.validate(Seq(value))).errors should not be empty
        snapshot.insert(Seq(value)).isLeft shouldBe true
        snapshot.document.getText shouldBe original
      }
    }
  }

  @Test
  def testInsertionWaitsForIndexesAndPreservesFollowingTags(): Unit = {
    val snapshot = context("Feature: Payments\n  Scenario: <caret>Existing\n    Given account\n  @keep\n  Scenario: Tagged\n    Given retained\n")
    val original = snapshot.document.getText
    val value = draft("Scenario: Zero\n  Given account")
    DumbModeTestUtils.runInDumbModeSynchronously(getProject, () => {
      snapshot.insert(Seq(value)).left.toOption.get should include("Wait for indexing")
      snapshot.document.getText shouldBe original
    })
    val injected = draft("Scenario: Zero\n  Given account\n  @injected")
    inReadAction(snapshot.validate(Seq(injected))).errors should not be empty
    snapshot.insert(Seq(value)) shouldBe Right(())
    snapshot.document.getText should include("@keep\n  Scenario: Tagged")
  }

  @Test
  def testInsertOnlySelectedDraftsInTheRuleAndUndoAsOneEdit(): Unit = {
    val snapshot = context("Feature: Payments\n  Rule: Transfers\n    Scenario: <caret>Existing\n      Given account\n  Rule: Other\n    Scenario: Keep\n      Given retained\n")
    val original = snapshot.document.getText
    val selected = draft("Scenario: Zero\n  When transfer 0\n  Then rejected")
    val skipped = draft("Scenario: Duplicate\n  When transfer 10")
    inReadAction(snapshot.validate(Seq(selected, skipped))).errors shouldBe empty
    snapshot.insert(Seq(selected)) shouldBe Right(())
    val changed = snapshot.document.getText
    changed should include("    Scenario: Zero\n      When transfer 0\n      Then rejected")
    changed should not include "Duplicate"
    changed.indexOf("Scenario: Zero") should be < changed.indexOf("Rule: Other")
    val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor)
    UndoManager.getInstance(getProject).isUndoAvailable(editor) shouldBe true
    UndoManager.getInstance(getProject).undo(editor)
    snapshot.document.getText shouldBe original
  }

  @Test
  def testLocalizedOutlineAndStaleDocument(): Unit = {
    val snapshot = context("# language: fr\nFonctionnalité: Paiements\n  Scénario: <caret>Existant\n    Soit compte\n")
    val value = draft("Plan du scénario: Limite <montant>\n  Quand transfert <montant>\n  Alors refusé\n  Exemples:\n    | montant |\n    | 0       |")
    inReadAction(snapshot.validate(Seq(value))).errors shouldBe empty
    snapshot.insert(Seq(value)) shouldBe Right(())
    snapshot.document.getText should include("  Plan du scénario: Limite <montant>")
    val stale = context("Feature: Payments\n  Scenario: <caret>Existing\n    Given account\n")
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable { override def run(): Unit = stale.document.insertString(0, "# changed\n") })
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    stale.insert(Seq(draft("Scenario: Zero\n  Given account"))).left.toOption.get should include("feature changed")
    stale.document.getText should not include "Scenario: Zero"
  }
}
