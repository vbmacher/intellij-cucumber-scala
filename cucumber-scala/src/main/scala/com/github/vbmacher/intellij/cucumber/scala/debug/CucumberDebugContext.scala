package com.github.vbmacher.intellij.cucumber.scala.debug

import com.sun.jdi.{IntegerValue, ObjectReference, StringReference, ThreadReference, Value}
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Reads the current thread's pinned Cucumber runner context, never a process-global "current scenario". */
object CucumberDebugContext {
  final case class Location(uri: String, step: Int, example: Int)
  def read(thread: ThreadReference, invoke: (ObjectReference, String) => Option[Value]): Option[Location] = Try {
    def field(value: ObjectReference, name: String): Option[ObjectReference] = Option(value.referenceType().fieldByName(name))
      .flatMap(f => Option(value.getValue(f))).collect { case o: ObjectReference => o }
    def integer(value: Value): Option[Int] = value match {
      case i: IntegerValue => Some(i.value())
      case o: ObjectReference => Option(o.referenceType().fieldByName("value")).flatMap(f => Option(o.getValue(f))).collect { case i: IntegerValue => i.value() }
      case _ => None
    }
    val frames = thread.frames().asScala
    // Capture object references before invoking any accessor: JDI invocations invalidate stack-frame mirrors.
    val matchObject = frames.find(_.location().declaringType().name() == "io.cucumber.core.runner.PickleStepDefinitionMatch").flatMap(f => Option(f.thisObject()))
    val testCase = frames.find(_.location().declaringType().name() == "io.cucumber.core.runner.TestCase").flatMap(f => Option(f.thisObject()))
    for {
      current <- matchObject
      step <- field(current, "step")
      scenario <- testCase
      stepLine <- invoke(step, "getLine").flatMap(integer)
      exampleLine <- invoke(scenario, "getLine").flatMap(integer)
      uri <- invoke(scenario, "getUri").collect { case o: ObjectReference => o }
      uriText <- invoke(uri, "toString").collect { case s: StringReference => s.value() }
    } yield Location(uriText, stepLine, exampleLine)
  }.toOption.flatten
}
