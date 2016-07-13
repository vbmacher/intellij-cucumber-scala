import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._

name :=  "Cucumber for Scala"
normalizedName :=  "intellij-cucumber-scala"
version := "0.3.5"
scalaVersion :=  "2.11.8"

lazy val `scala-plugin` = IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/files/1347/27087/scala-intellij-bin-2016.2.0.zip"))
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
    ideaBuild in ThisBuild := "162.1121.10",
    ideaEdition in ThisBuild := IdeaEdition.Community,
    fork in Test := true,
    parallelExecution := true
)
