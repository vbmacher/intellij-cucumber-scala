package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._

class OutlineConsistencyInspection extends GherkinInspection {
  override def getShortName: String = "ScalaCucumberOutlineConsistency"
  override def buildVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor = new GherkinElementVisitor {
    override def visitScenarioOutline(outline: GherkinScenarioOutline): Unit = {
      val tables = outline.getExamplesBlocks.asScala.flatMap(b => Option(b.getTable)).toSeq
      tables.foreach { table =>
        Option(table.getHeaderRow).foreach { row =>
          val headers = row.getPsiCells.asScala.toSeq
          val names = headers.map(_.getText.trim)
          headers.zipWithIndex.foreach { case (cell, index) =>
            val name = names(index)
            if (name.isEmpty) holder.registerProblem(row, "Examples header names must not be empty")
            else if (names.take(index).contains(name)) holder.registerProblem(cell, s"Duplicate Examples header '$name'")
          }
        }
      }
      if (tables.nonEmpty) outline.getSteps.foreach { step =>
        "<([^<>]+)>".r.findAllMatchIn(step.getText).foreach { placeholder =>
          val name = placeholder.group(1)
          val missing = tables.filterNot(table => Option(table.getHeaderRow).exists(_.getPsiCells.asScala.exists(_.getText.trim == name)))
          if (missing.nonEmpty) holder.registerProblem(step, TextRange.from(placeholder.start, placeholder.end - placeholder.start),
            s"No Examples column '$name' in ${missing.size} Examples block(s)")
        }
      }
    }
  }
}
