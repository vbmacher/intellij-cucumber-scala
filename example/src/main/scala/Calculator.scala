package cucumber.examples.scalacalculator

import scala.collection.mutable

sealed trait Arg

final case class Op(value: String) extends Arg
final case class Val(value: Double) extends Arg

final class Calculator {
  private val stack = mutable.Stack.empty[Double]

  private def op(f: (Double, Double) => Double): Unit = {
    require(stack.size >= 2, "A binary operation needs two operands")
    val right = stack.pop()
    val left = stack.pop()
    stack.push(f(left, right))
  }

  def push(arg: Arg): Unit = {
    arg match {
      case Op("+") => op(_ + _)
      case Op("-") => op(_ - _)
      case Op("*") => op(_ * _)
      case Op("/") => op(_ / _)
      case Val(value) => stack.push(value)
      case _ => throw new IllegalArgumentException("Unknown calculator operation")
    }
  }

  def clear(): Unit = stack.clear()

  def isEmpty: Boolean = stack.isEmpty

  def value: Double = stack.head
}
