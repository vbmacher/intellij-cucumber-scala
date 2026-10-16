import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils

val intellijVersion = "262.9437.185"
val intellijArtifact = IntellijVersionUtils.detectArtifactLocation(BuildInfo(intellijVersion, IntelliJPlatform.IdeaCommunity), ".zip")

val jvmOpens = Seq(
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/java.text=ALL-UNNAMED",
  "--add-opens=java.base/java.time=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
  "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
  "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
  "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-exports=java.base/sun.nio.fs=ALL-UNNAMED",
  "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
  "--add-opens=java.desktop/com.sun.java.swing.platf.gtk=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
  "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
  "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
  "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
  "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
  "--add-exports=java.desktop/sun.swing=ALL-UNNAMED",
  "--add-exports=java.management/sun.management=ALL-UNNAMED",
)

lazy val commonSettings = Seq(
  version := "2026.2",
  scalaVersion := "2.13.18",
)

// Example projects for manual testing of the plugin
lazy val exampleSettings = commonSettings ++ Seq(
  packageMethod := PackagingMethod.Skip(),
  libraryDependencies ++= Seq(
    "io.cucumber" %% "cucumber-scala" % "8.39.7",
    "junit" % "junit" % "4.13.2" % Test,
  ),
)

lazy val `cucumber-scala` = project
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    commonSettings,
    ThisBuild / intellijPluginName := "intellij-cucumber-scala",
    ThisBuild / intellijBuild := intellijVersion,
    ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
    ThisBuild / autoRemoveOldCachedIntelliJSDK := true,
    ThisBuild / autoRemoveOldCachedDownloads := true,
    Compile / javacOptions ++= "--release" :: "21" :: Nil,
    fork := true,
    javaOptions ++= jvmOpens,
    resolvers += intellijArtifact.repository,
    libraryDependencies ++= Seq(
      // Step snippet generation and built-in parameter regexes
      "io.cucumber" % "cucumber-expressions" % "18.1.0",
      "io.cucumber" % "tag-expressions" % "8.1.0",
      "junit" % "junit" % "4.13.2" % Test,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalatestplus" %% "junit-4-13" % "3.2.20.0" % Test,
      "org.opentest4j" % "opentest4j" % "1.3.0" % Test,
    ) ++ Seq("test-framework-core", "test-framework-common", "test-framework").map { artifact =>
      ("com.jetbrains.intellij.platform" % artifact % intellijArtifact.artifactVersion).notTransitive() % Test
    },
    intellijPlugins ++= Seq(
      "org.intellij.scala:2026.2.16".toPlugin,
      "gherkin:262.8665.173".toPlugin,
      "com.intellij.modules.jcef".toPlugin,
      // In 2026.2 several platform modules moved out of the platform lib into bundled plugins.
      // They are not picked up unless declared, and a missing one cascades into the plugins we
      // depend on: lucene.common -> spellchecker -> gherkin, aetherDependencyResolver and
      // structuralSearch -> java -> scala. The result is that gherkin never loads and its
      // cucumberJvmExtensionPoint is not registered at all.
      "intellij.libraries.misc.plugin".toPlugin,
      "intellij.java.aetherDependencyResolver.plugin".toPlugin,
      "intellij.structureView.plugin".toPlugin,
      "intellij.structuralSearch.plugin".toPlugin,
      "intellij.testRunner.plugin".toPlugin,
      "intellij.todo.plugin".toPlugin,
      // intellij.java.backend is an embedded module inside com.intellij.java, not a separate
      // marketplace plugin. sbt-idea-plugin incorrectly tries to download it as a transitive
      // dependency, resulting in a 404. Excluding it from transitive resolution fixes the issue.
      "com.intellij.java:262.9437.185".toPlugin(excludedIds = Set("intellij.java.backend")),
    ),
    customIntellijVMOptions := customIntellijVMOptions.value.copy(extraOptions = jvmOpens),
    //  https://github.com/JetBrains/sbt-idea-plugin/issues/137
    buildIntellijOptionsIndex := streams.value.log.info("Skipping buildIntellijOptionsIndex."),
    packageMethod := PackagingMethod.Standalone(),
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version = version.value
      xml.sinceBuild = "262.8665"
      xml.untilBuild = "262.*"
    },
    signPluginOptions := signPluginOptions.value.copy(enabled = true))

lazy val example = project.settings(exampleSettings)

lazy val `example-stepdefs-lib` = project.settings(exampleSettings)

lazy val root = (project in file("."))
  .aggregate(`cucumber-scala`, example, `example-stepdefs-lib`)
  .settings(
    name := "intellij-cucumber-scala",
    packageMethod := PackagingMethod.Skip()
  )
