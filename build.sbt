import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._

name :=  "Cucumber for Scala"
normalizedName :=  "intellij-cucumber-scala"
version := "2018.1.0"
scalaVersion :=  "2.12.4"

lazy val `scala-plugin` = IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/plugin/download?updateId=44474"))
lazy val `cucumber-java` = IdeaPlugin.Zip("cucumber-java", url("https://plugins.jetbrains.com/plugin/download?updateId=43535"))
lazy val gherkin = IdeaPlugin.Zip("gherkin", url("https://plugins.jetbrains.com/plugin/download?updateId=43534"))

lazy val `cucumber-scala` = project.in(file( "."))
  .enablePlugins(SbtIdeaPlugin)
  .enablePlugins(SbtIdeaPluginPimps)
  .settings(scalariformSettings)
  .settings(
    autoScalaLibrary := false,
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    ideaExternalPlugins ++= Seq(`scala-plugin`, gherkin, `cucumber-java`),
    // check https://s3-eu-west-1.amazonaws.com/intellij-releases/ for valid builds
    ideaBuild in ThisBuild := "181.4203.550",
    ideaEdition in ThisBuild := IdeaEdition.Community,
    ideaPublishSettings := PublishSettings(pluginId = "com.github.danielwegener.cucumber-scala", username = "", password = "", channel = None),
    fork in Test := true,
    parallelExecution := true
)
