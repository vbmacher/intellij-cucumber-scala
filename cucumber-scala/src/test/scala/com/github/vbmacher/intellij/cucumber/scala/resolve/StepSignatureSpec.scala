package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.inspections.StepSignatureInspection
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.{LibraryLightProjectDescriptor, LocalDependency, ScCucumberSpecBase}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class StepSignatureSpec extends ScCucumberSpecBase {
  override val DESCRIPTOR = new LibraryLightProjectDescriptor(LocalDependency("scala-library",
    classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation, classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation))

  @Test
  def testBuiltinTypeAndObservedDocStringArity(): Unit = {
    myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}")
    val file = myFixture.addFileToProject("Signatures.scala", """class Signatures {
      | When("transfer {int}") { (amount: String) => () }
      | Then("receipt") { () => () }
      | Given("valid {int}") { (amount: Int) => () }
      |}
      |""".stripMargin)
    myFixture.configureByText("receipt.feature", "Feature: Receipt\n  Scenario: Receipt\n    Then receipt\n      \"\"\"\n      body\n      \"\"\"\n")
    inReadAction {
      val calls = PsiTreeUtil.findChildrenOfType(file, classOf[ScMethodCall]).asScala.filter(_.definition.nonEmpty).toSeq
      StepSignatureInspection.problems(calls.find(_.pattern.contains("transfer {int}")).get).map(_._2).mkString should include("expects scala.Int")
      StepSignatureInspection.problems(calls.find(_.pattern.contains("receipt")).get).map(_._2).mkString should include("supplies 1 arguments")
      StepSignatureInspection.problems(calls.find(_.pattern.contains("valid {int}")).get) shouldBe empty
    }
  }

  @Test
  def testRegexNestedAndNonCapturingGroupsUseRuntimeArgumentCount(): Unit = {
    val file = myFixture.addFileToProject("RegexSignature.scala", """class RegexSignature {
      | When("^amount ((?:[0-9]+)(?:\\.[0-9]+)?)$") { (amount: String) => () }
      |}
      |""".stripMargin)
    myFixture.configureByText("regex.feature", "Feature: Regex\n  Scenario: Regex\n    When amount 1.25\n")
    inReadAction {
      val call = PsiTreeUtil.findChildrenOfType(file, classOf[ScMethodCall]).asScala.find(_.definition.nonEmpty).get
      StepSignatureInspection.problems(call) shouldBe empty
    }
  }
}
