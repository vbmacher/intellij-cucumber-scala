import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._

name :=  "Cucumber for Scala"
version := "0.3.3"
scalaVersion :=  "2.11.4"

lazy val `scala-plugin` = IdeaPlugin.Zip("scala-plugin", url("http://plugins.jetbrains.com/files/1347/22559/scala-intellij-bin-2.0.4.zip"))
lazy val `cucumber-java` = IdeaPlugin.Zip("cucumber-java", url("https://plugins.jetbrains.com/files/7212/22138/cucumber-java.zip"))
lazy val cucumber = IdeaPlugin.Zip("cucumber", url("https://plugins.jetbrains.com/files/7211/22137/cucumber.zip"))

lazy val `cucumber-scala` = project.in(file( "."))
  .enablePlugins(SbtIdeaPlugin)
  .enablePlugins(SbtIdeaPluginPimps)
  .settings(scalariformSettings)
  .settings(
    autoScalaLibrary := false,
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    ideaExternalPlugins ++= Seq(`scala-plugin`, cucumber, `cucumber-java`),
    ideaBuild in ThisBuild := "143.1184.17",
    ideaEdition in ThisBuild := IdeaEdition.Community,
    fork in Test := true,
    parallelExecution := true
)
