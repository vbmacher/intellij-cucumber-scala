package cucumber.examples.scalacalculator

import io.cucumber.scala.ScalaDsl

trait CustomTypes extends ScalaDsl {
  // Regexes are written as-is: no extra escaping for the plugin.
  ParameterType("speed", """\d+km/h""") { text: String =>
    Speed(text.stripSuffix("km/h").toDouble)
  }
  ParameterType("time", """\d+h""") { text: String =>
    Time(text.stripSuffix("h").toDouble)
  }

  ParameterType("speed-m-s", """\d+m/s""") { text: String =>
    Speed(text.stripSuffix("m/s").toDouble * 3.6)
  }
  ParameterType("time-s", """\d+s""") { text: String =>
    Time(text.stripSuffix("s").toDouble / 3600)
  }
}

final case class Speed(kmPerHour: Double)
final case class Time(hours: Double)
