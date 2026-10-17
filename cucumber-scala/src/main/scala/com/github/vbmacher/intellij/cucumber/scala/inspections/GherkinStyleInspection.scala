package com.github.vbmacher.intellij.cucumber.scala.inspections

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElementVisitor, PsiWhiteSpace}
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi._
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

import scala.beans.BeanProperty
import scala.util.Try

class GherkinStyleInspection extends GherkinInspection {
  @BeanProperty var requireTitle: Boolean = true
  @BeanProperty var duplicateTags: Boolean = true
  @BeanProperty var maxSteps: Int = 12
  @BeanProperty var checkOrder: Boolean = false
  @BeanProperty var tagPattern: String = ""
  override def getShortName: String = "ScalaCucumberStyle"
  override def isEnabledByDefault: Boolean = false
  override def getOptionsPane: OptPane = OptPane.pane(
    OptPane.checkbox("requireTitle", "Require scenario titles"),
    OptPane.checkbox("duplicateTags", "Report duplicate tags"),
    OptPane.number("maxSteps", "Maximum steps per scenario", 1, 10000),
    OptPane.checkbox("checkOrder", "Require Given / When / Then order"),
    OptPane.string("tagPattern", "Tag convention (regular expression; blank disables)"))

  override def buildVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor = new GherkinElementVisitor {
    private def check(scenario: GherkinStepsHolder): Unit = {
      if (requireTitle && scenario.getScenarioName.trim.isEmpty) holder.registerProblem(scenario, "Style: scenario title is required")
      if (maxSteps > 0 && scenario.getSteps.length > maxSteps) holder.registerProblem(scenario, s"Style: ${scenario.getSteps.length} steps exceeds the configured maximum of $maxSteps")
      if (checkOrder) {
        val file = scenario.getContainingFile.asInstanceOf[GherkinFile]
        val provider = JsonGherkinKeywordProvider.getKeywordProvider(scenario)
        var previous = -1
        scenario.getSteps.foreach { step =>
          val rank = Option(provider.getBaseKeyword(file.getLocaleLanguage, step.getKeyword.getText.trim)).map(_.toLowerCase(java.util.Locale.ROOT)) match {
            case Some("given") => 0
            case Some("when") => 1
            case Some("then") => 2
            case _ => previous
          }
          if (rank < previous) holder.registerProblem(step, "Style: step kind appears after a later Given / When / Then section")
          previous = math.max(previous, rank)
        }
      }
    }
    override def visitScenario(scenario: GherkinScenario): Unit = if (!scenario.isBackground) check(scenario)
    override def visitScenarioOutline(scenario: GherkinScenarioOutline): Unit = check(scenario)
    override def visitTag(tag: org.jetbrains.plugins.cucumber.psi.impl.GherkinTagImpl): Unit = {
      if (duplicateTags) {
        val prior = Iterator.iterate(tag.getPrevSibling)(e => if (e == null) null else e.getPrevSibling)
          .takeWhile(e => e != null && (e.isInstanceOf[GherkinTag] || e.isInstanceOf[PsiWhiteSpace] || e.isInstanceOf[com.intellij.psi.PsiComment]))
          .collect { case t: GherkinTag => t.getText }.toSeq
        if (prior.contains(tag.getText)) holder.registerProblem(tag, "Style: duplicate tag has no additional effect", new LocalQuickFix {
          override def getFamilyName: String = "Remove duplicate tag"
          override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = descriptor.getPsiElement.delete()
        })
      }
      if (tagPattern.nonEmpty && Try(tagPattern.r.pattern.matcher(tag.getText).matches()).toOption.contains(false))
        holder.registerProblem(tag, "Style: tag does not match the project's configured convention")
    }
  }
}
