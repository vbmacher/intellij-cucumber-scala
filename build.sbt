import SbtIdeaPluginPimps._
import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._

name :=  "Cucumber for Scala"
version := "0.3.2"
scalaVersion :=  "2.11.2"

lazy val `scala-plugin` = IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/files/1347/22150/scala-intellij-bin-2.0.1.zip"))
lazy val `cucumber-java` = IdeaPlugin.Zip("cucumber-java", url("https://plugins.jetbrains.com/files/7212/22138/cucumber-java.zip"))
lazy val cucumber = IdeaPlugin.Zip("cucumber", url("https://plugins.jetbrains.com/files/7211/22137/cucumber.zip"))

lazy val `cucumber-scala` = project.in(file( "."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(SbtIdeaPluginPimps.projectSettings)
  .settings(scalariformSettings)
  .settings(
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    ideaExternalPlugins ++= Seq(`scala-plugin`, cucumber, `cucumber-java`),
    ideaBuild in ThisBuild := "143.381.42",
    fork in Test := true,
    parallelExecution := true,
    javaOptions in Test := Seq(
      //  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      "-Xms128m",
      "-Xmx1024m",
      "-XX:MaxPermSize=350m",
      "-ea",
      s"-Didea.system.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-system",
      s"-Didea.config.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-config",
      s"-Dplugin.path=${baseDirectory.value}/out/plugin/Scala"
    ),
    ideaFullJars := ideaMainJars.value ++ ideaInternalPluginsJars.value ++ ideaExternalPluginsJarsPimp.value
)
