package com.github.vbmacher.intellij.cucumber.scala.suite

sealed abstract class SuiteView(val label: String, val description: String, val relatedTitle: String,
                                val hasFeatureTags: Boolean) extends Product with Serializable {
  override final def toString: String = label
}

object SuiteView {
  case object Scenarios extends SuiteView("Scenarios",
    "Browse all project scenarios. Select a scenario to inspect its steps.",
    "Scenario steps", hasFeatureTags = true)
  case object Definitions extends SuiteView("Step definitions",
    "Scala expressions and the scenarios whose steps match them. Select a definition to see those scenarios. Matches include ambiguous bindings; outlines count once.",
    "Matching scenarios", hasFeatureTags = false)
  case object Undefined extends SuiteView("Undefined steps",
    "Feature steps with no matching Scala definition. Open a step to add or correct its implementation.",
    "Related sources", hasFeatureTags = true)
  case object Ambiguous extends SuiteView("Ambiguous steps",
    "Feature steps matching more than one Scala definition. Select a step to see the competing definitions; make its binding unique before running.",
    "Competing definitions", hasFeatureTags = true)
  case object Unused extends SuiteView("Unused definitions",
    "Scala definitions with no matching feature step in this project. They may have consumers outside this project.",
    "Matching scenarios", hasFeatureTags = false)
  case object Repeated extends SuiteView("Repeated expressions",
    "Scala definitions declaring the same expression. Select a definition to compare the other declarations; different expressions can also cause ambiguous steps.",
    "Other declarations", hasFeatureTags = false)
  case object Duplicates extends SuiteView("Duplicate scenarios",
    "Scenarios with the same ordered step bodies and arguments, excluding Backgrounds and Examples data. Select a scenario to compare the others; matching wording does not prove business equivalence.",
    "Other scenarios", hasFeatureTags = true)

  val values: Seq[SuiteView] = Seq(Scenarios, Definitions, Undefined, Ambiguous, Unused, Repeated, Duplicates)
}
