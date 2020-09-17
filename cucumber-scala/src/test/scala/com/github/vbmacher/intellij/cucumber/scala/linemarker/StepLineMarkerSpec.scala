package com.github.vbmacher.intellij.cucumber.scala.linemarker

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepLineMarkerSpec extends ScCucumberSpecBase {

  @Test
  def testLineMarker(): Unit = {
    loadTestCase("lineMarker/StepDefinition.scala")
    val lineMarkers = findLineMarkers()

    assert(lineMarkers.size === 1)
  }

  @Test
  def testLineMarkerNegative(): Unit = {
    loadTestCase("lineMarker/NotAStepDefinition.scala")
    val lineMarkers = findLineMarkers()

    assert(lineMarkers.isEmpty)
  }
}
