import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._

name :=  "Cucumber for Scala"
normalizedName :=  "intellij-cucumber-scala"
version := "2017.2.0"
scalaVersion :=  "2.11.11"

lazy val `scala-plugin` = IdeaPlugin.Zip("scala-plugin", url("https://download.plugins.jetbrains.com/1347/36881/scala-intellij-bin-2017.2.4.zip"))
lazy val `cucumber-java` = IdeaPlugin.Zip("cucumber-java", url("https://plugins.jetbrains.com/files/7212/30792/cucumber-java.zip"))
lazy val gherkin = IdeaPlugin.Zip("gherkin", url("https://plugins.jetbrains.com/files/7211/29344/cucumber.zip"))

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
    ideaBuild in ThisBuild := "172.1909.2",
    ideaEdition in ThisBuild := IdeaEdition.Community,
    ideaPublishSettings := PublishSettings(pluginId = "com.github.danielwegener.cucumber-scala", username = "", password = "", channel = None),
    fork in Test := true,
    parallelExecution := true
)
