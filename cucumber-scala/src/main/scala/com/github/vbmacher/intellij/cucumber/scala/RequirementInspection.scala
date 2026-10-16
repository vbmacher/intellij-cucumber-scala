package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.{GherkinElementVisitor, GherkinTag}

class RequirementInspection extends GherkinInspection {
  override def getShortName: String = "ScalaCucumberRequirements"
  override def buildVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor = new GherkinElementVisitor {
    override def visitTag(tag: org.jetbrains.plugins.cucumber.psi.impl.GherkinTagImpl): Unit = RequirementReferences.destination(tag).foreach { target =>
      if (!target.startsWith("https://") && !target.startsWith("http://") && RequirementReferences.targets(tag).isEmpty)
        holder.registerProblem(tag, "The configured local requirement target was not found")
    }
  }
}
