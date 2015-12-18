import com.dancingrobot84.sbtidea.{Keys => SbtIdeaPluginKeys, SbtIdeaPlugin}
import sbt.Keys._
import sbt._


object SbtIdeaPluginPimps extends sbt.AutoPlugin {

  import SbtIdeaPluginKeys._
  override def requires = SbtIdeaPlugin

  val autoImport = SbtIdeaPluginPimps.Keys

  override def projectSettings: Seq[Setting[_]] = Keys.projectSettings

  object Keys {
    lazy val runIdea = InputKey[Unit](
      "runIdea",
      "Runs the installed version of idea with this plugin")

    lazy val runIdeaClasspath = TaskKey[Classpath]("runIdeaClasspath", "classpath for idea at runtime")

    lazy val runIdeaConfigDirectory = SettingKey[File]("runIdeaConfigDirectory", "directory where runIdea stores its configuration")
    lazy val runIdeaSystemDirectory = SettingKey[File]("runIdeaSystemDirectory", "directory where runIdea stores its configuration")

    val projectSettings: Seq[Setting[_]] = Seq(

      (runIdeaConfigDirectory in runIdea) := (target.value / "idea" / "test-config"),
      (runIdeaSystemDirectory in runIdea) := (target.value / "idea" / "test-system"),

      (javaOptions in runIdea) := Seq(
        s"-Xbootclasspath/a:${ideaBaseDirectory.value}/lib/boot.jar",
        s"-Dplugin.path=${(artifactPath in packageBin in Compile).value}",

        s"-Didea.system.path=${(runIdeaSystemDirectory in runIdea).value}",
        s"-Didea.config.path=${(runIdeaConfigDirectory in runIdea).value}"
      ),
      (mainClass in runIdea) := Some("com.intellij.idea.Main"),


      (fork in runIdea) := true,
      (runIdeaClasspath in runIdea) := ideaMainJars.value ++ (file(System.getProperty("java.home")) / ".." / "lib" * "tools.jar").classpath,
      runIdea <<= Defaults.runTask(runIdeaClasspath in runIdea, mainClass in runIdea, runner in runIdea)
      //runIdea := { println((javaOptions in runIdea).value)   }
    )

  }




}

