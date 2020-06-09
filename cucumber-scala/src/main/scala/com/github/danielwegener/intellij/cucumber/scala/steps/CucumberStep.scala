package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util

import com.github.danielwegener.intellij.cucumber.scala.steps.CucumberStep.DT
import io.cucumber.core.gherkin
import io.cucumber.core.gherkin.{DataTableArgument, Step, StepType}
import io.cucumber.core.internal.gherkin.GherkinDialectProvider
import org.jetbrains.plugins.cucumber.psi.{GherkinStep, GherkinTable}

import scala.collection.JavaConverters._

class CucumberStep(step: GherkinStep) extends Step {
  private val dialect = new GherkinDialectProvider().getDefaultDialect
  private val keywordStepType = {
    Seq(
      dialect.getGivenKeywords.asScala.map(_.trim -> StepType.GIVEN),
      dialect.getWhenKeywords.asScala.map(_.trim -> StepType.WHEN),
      dialect.getThenKeywords.asScala.map(_.trim -> StepType.THEN),
      dialect.getAndKeywords.asScala.map(_.trim -> StepType.AND),
      dialect.getButKeywords.asScala.map(_.trim -> StepType.BUT)
    ).flatten.toMap
  }

  override val getArgument: gherkin.Argument = {
    val table = Option(step.getTable)
    if (table.nonEmpty) new DT(table.get) else null
  }

  override val getKeyWord: String = step.getKeyword.getText

  override def getType: StepType = {
    val keyword = getKeyWord.trim
    if (StepType.isAstrix(keyword)) StepType.OTHER
    else {
      keywordStepType.getOrElse(
        keyword,
        throw new IllegalStateException("Keyword " + getKeyWord + " was neither given, when, then, and, but nor *")
      )
    }
  }

  override def getPreviousGivenWhenThenKeyWord: String = null

  override def getText: String = step.getText

  override def getId: String = step.getName

  override def getLine: Int = 0
}
object CucumberStep {

  def apply(step: GherkinStep): CucumberStep = new CucumberStep(step)


  class DT(table: GherkinTable) extends DataTableArgument {

    def cells(): util.List[util.List[String]] = {
      val rows = Seq(
        Seq(Option(table.getHeaderRow)).flatten,
        table.getDataRows.asScala
      ).flatten

      rows.map(_.getPsiCells.asScala).map(_.map(_.getText).asJava).asJava
    }

    def getLine: Int = 1
  }
}
