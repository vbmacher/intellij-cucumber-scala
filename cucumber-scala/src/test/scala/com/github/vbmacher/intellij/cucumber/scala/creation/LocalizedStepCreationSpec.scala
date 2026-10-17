package com.github.vbmacher.intellij.cucumber.scala.creation

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class LocalizedStepCreationSpec extends StepCreationSpecBase {
  private val quotes = "\"\"\""

  private def checkLocalized(language: String, feature: String, scenario: String, steps: String,
                             keyword: String, wording: String, arguments: String = ""): Unit = {
    val destination = loadTestCase("creation/StepDefinitions.scala")(0)
    val source = myFixture.configureByText("localized.feature",
      s"# language: $language\n$feature: Localized\n  $scenario: Localized\n$steps\n")
    checkStepCreation(source, destination,
      s"""import io.cucumber.scala.ScalaDsl
         |
         |class StepDefinitions extends ScalaDsl {
         |
         |  $keyword($quotes$wording$quotes) { ($arguments) =>
         |    // Write code here that turns the phrase above into concrete actions
         |    throw new io.cucumber.scala.PendingException()
         |  }
         |}""".stripMargin)
  }

  @Test
  def testFrenchGivenPreservesAccents(): Unit =
    checkLocalized("fr", "Fonctionnalité", "Scénario", "    Soit un co<caret>mpte vérifié existe",
      "Given", "un compte vérifié existe")

  @Test
  def testFrenchWhenAndContinuation(): Unit =
    checkLocalized("fr", "Fonctionnalité", "Scénario", "    Quand je paie\n    Et je re<caret>çois 42 crédits",
      "When", "je reçois {int} crédits", "int1: Int")

  @Test
  def testGermanThen(): Unit =
    checkLocalized("de", "Funktionalität", "Szenario", "    Dann ist das Ko<caret>nto leer",
      "Then", "ist das Konto leer")

  @Test
  def testFrenchWildcard(): Unit =
    checkLocalized("fr", "Fonctionnalité", "Scénario", "    Alors je vérifie\n    * je re<caret>çois 42 crédits",
      "Then", "je reçois {int} crédits", "int1: Int")
}
