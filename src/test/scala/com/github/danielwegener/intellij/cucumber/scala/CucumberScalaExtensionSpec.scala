package com.github.danielwegener.intellij.cucumber.scala

import org.scalatest._
import org.scalatest.Matchers._

class CucumberScalaExtensionSpec extends FlatSpec with Matchers {
  "uniqueShortestPath" should "keep the shortest path in the list when it's surrounded by longer" in {
    val res = CucumberScalaExtension.uniqueShortestPath(List("a.b.c", "a.b", "a.b.c.d"))
    res should contain theSameElementsAs List("a.b")
  }

  "uniqueShortestPath" should "keep the shortest with a mix of scenariosl" in {
    val res = CucumberScalaExtension.uniqueShortestPath(List("a.b.c", "a.b", "a.d", "b.a.b"))
    res should contain theSameElementsAs List("a.b", "a.d", "b.a.b")
  }

  "uniqueShortestPath" should "keep all when they have different beginnings but are substrings" in {
    val res = CucumberScalaExtension.uniqueShortestPath(List("a.b", "b.a.b"))
    res should contain theSameElementsAs List("a.b", "b.a.b")
  }
}
