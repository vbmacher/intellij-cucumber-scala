package com.github.vbmacher.intellij.cucumber.scala.steps

import java.util

import CucumberStep.DT
import io.cucumber.core.gherkin
import io.cucumber.core.gherkin.{DataTableArgument, Step, StepType}
import io.cucumber.gherkin.GherkinDialectProvider
import io.cucumber.plugin.event.Location
import org.jetbrains.plugins.cucumber.psi.{GherkinStep, GherkinTable}

import scala.jdk.CollectionConverters._

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

  override def getType: StepType = {
    val keyword = getKeyword.trim
    if (StepType.isAstrix(keyword)) StepType.OTHER
    else {
      keywordStepType.getOrElse(
        keyword,
        throw new IllegalStateException(s"Keyword $getKeyword was neither given, when, then, and, but nor *")
      )
    }
  }

  override def getText: String = step.getSubstitutedName

  override def getId: String = step.getName

  override def getLine: Int = 0

  override val getPreviousGivenWhenThenKeyword: String = null

  override val getKeyword: String = step.getKeyword.getText

  override val getLocation: Location = null
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
