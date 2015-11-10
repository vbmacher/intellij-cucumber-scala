import com.dancingrobot84.sbtidea.Keys._
import sbt.Keys._
import sbt._

object SbtIdeaPluginPimps extends Build {

  lazy val ideaExternalPluginsJarsPimp = TaskKey[Classpath](
    "idea-external-plugins-jars-pimp",
    "Classpath containing jars of external IDEA plugins used in this project")


  def createPluginsClasspathPimp(pluginsBase: File, pluginsUsed: Seq[String]): Classpath = {
    val pluginsDirs = pluginsUsed.foldLeft(PathFinder.empty) { (paths, plugin) =>
      paths +++ (pluginsBase / plugin) +++ (pluginsBase / plugin / "lib") +++ (pluginsBase / plugin / plugin / "lib")
    }
    (pluginsDirs * (globFilter("*.jar") -- "asm*.jar")).classpath
  }

  def projectSettings : Seq[Setting[_]] = Seq(
    ideaExternalPluginsJarsPimp <<= (ideaBaseDirectory, ideaExternalPlugins).map {
      (baseDir, pluginsUsed) => createPluginsClasspathPimp(baseDir / "externalPlugins", pluginsUsed.map(_.name))
    }
  )

}

