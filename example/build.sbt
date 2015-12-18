import sbt.Keys._

scalaVersion :=  "2.11.2"

lazy val `cucumber-scala-example` = project.in(file( "."))
  .settings(
    name :=  "Example project Cucumber for Scala",
    version := "0.0.1",
    scalaVersion :=  "2.11.2"
  )
  .settings(
    libraryDependencies := Seq(
      "info.cukes" % "cucumber-scala_2.11" % "1.2.4" % "test"
    )
  )