package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.editor.ScStepDocumentation
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.github.vbmacher.intellij.cucumber.scala.{LibraryLightProjectDescriptor, LocalDependency, ScCucumberSpecBase}
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class DeprecationCompletionSpec extends ScCucumberSpecBase {
  override val DESCRIPTOR = new LibraryLightProjectDescriptor(LocalDependency("scala-library",
    classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation, classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation))
  @Test
  def testActualAnnotationMetadataDecoratesCompletionAndDocumentation(): Unit = {
    myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}")
    myFixture.addFileToProject("Old.scala", "@deprecated(\"Use account current\", \"2\") class Old { Given(\"account old\") { () => () } }")
    myFixture.addFileToProject("Current.scala", "class Current { Given(\"account current\") { () => () } }")
    myFixture.configureByText("deprecated.feature", "Feature: Deprecation\n  Scenario: Deprecation\n    Given acc<caret>\n")
    val items = myFixture.completeBasic()
    val old = items.find(_.getLookupString == "account old").get
    val presentation = new LookupElementPresentation
    old.renderElement(presentation)
    presentation.isStrikeout shouldBe true
    presentation.getTailText should include("Use account current")
    items.indexWhere(_.getLookupString == "account current") should be < items.indexWhere(_.getLookupString == "account old")
    com.intellij.codeInsight.lookup.LookupManager.getInstance(getProject).hideActiveLookup()
    myFixture.configureByText("deprecated.feature", "Feature: Deprecation\n  Scenario: Deprecation\n    Given account old<caret>\n")
    ScCucumber.inReadAction {
      val step = myFixture.findElementByText("Given account old", classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep])
      new ScStepDocumentation().generateDoc(step, step) should include("Use account current")
      ScCucumber.matchingSteps(step).size shouldBe 1
    }
  }
}
