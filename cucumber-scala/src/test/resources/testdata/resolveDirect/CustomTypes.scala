import io.cucumber.scala.ScalaDsl

trait CustomTypes extends ScalaDsl {
  ParameterType("speed", "\\\\d+km/h") { speed: String => Speed(speed.split(' ').head.toDouble) }
  ParameterType("time", "\\\\d+h") { time: String => Time(time.split(' ').head.toDouble) }
}

case class Speed(kmPerHour: Double)
case class Time(hours: Double)
