
import scala.collection.mutable
import scala.language.implicitConversions

sealed trait Arg

case class Op(value: String) extends Arg
case class Val(value: Double) extends Arg

object Arg{
  implicit def op(s:String):Op = Op(s)
  implicit def value(v:Double):Val = Val(v)
}

class Calculator {
  private val stack = new mutable.Stack[Double]

  private def op(f: (Double, Double) => Double) =
    stack push f(stack.pop(), stack.pop())

  def push(arg: Arg): Unit = {
    arg match {
      case Op("+") => op(_ + _)
      case Op("-") => op(_ - _)
      case Op("*") => op(_ * _)
      case Op("/") => op(_ / _)
      case Val(value) => stack push value
      case _ => throw new RuntimeException("should not happen!")
    }
  }

  def value: Double = stack.head
}