lazy val commonSettings = Seq(
  fork := true,
  javaOptions ++= Seq(
    "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED",
    "--add-opens=java.desktop/com.sun.java.swing.platf.gtk=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
    "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"
  ),
  version := "2025.3",
  scalaVersion := "2.13.18",
  libraryDependencies ++= Seq(
    "junit" % "junit" % "4.13.2" % Test,
    "io.cucumber" %% "cucumber-scala" % "8.38.0",
    "io.cucumber" % "cucumber-junit" % "7.33.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "org.scalatestplus" %% "junit-4-13" % "3.2.19.1" % Test,
    "org.opentest4j" % "opentest4j" % "1.3.0" % Test
  ),
  //  https://github.com/JetBrains/sbt-idea-plugin/issues/137
  buildIntellijOptionsIndex := {
    streams.value.log.info("Skipping buildIntellijOptionsIndex.")
  }
)

lazy val `cucumber-scala` = project
        .enablePlugins(SbtIdeaPlugin)
        .settings(
          commonSettings,
          ThisBuild / intellijPluginName := "intellij-cucumber-scala",
          ThisBuild / intellijBuild := "253.28294.334",
          ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
          ThisBuild / autoRemoveOldCachedIntelliJSDK := true,
          Compile / javacOptions ++= "--release" :: "21" :: Nil,
          intellijPlugins ++= Seq(
            "org.intellij.scala:2025.3.23".toPlugin,
            "gherkin:253.28294.218".toPlugin,
            "com.intellij.java".toPlugin,
          ),
          intellijVMOptions := intellijVMOptions.value.copy(
            defaultOptions = Seq(
              "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
              "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
              "--add-opens=java.desktop/com.sun.java.swing.platf.gtk=ALL-UNNAMED",
              "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
              "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
              "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
              "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
              "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
              "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
              "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
              "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
              "--add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED",
              "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
              "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
              "--add-exports=java.desktop/sun.swing=ALL-UNNAMED",
              "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
              "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
              "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
              "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
              "--add-exports=java.base/sun.nio.fs=ALL-UNNAMED",
              "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
              "--add-exports=java.management/sun.management=ALL-UNNAMED",
              "--add-opens=java.base/java.lang=ALL-UNNAMED",
              "--add-opens=java.base/java.util=ALL-UNNAMED",
              "--add-opens=java.base/java.nio=ALL-UNNAMED",
              "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
              "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"
            )
          ),
          packageMethod := PackagingMethod.Standalone(),
          patchPluginXml := pluginXmlOptions { xml =>
            xml.version = version.value
            xml.sinceBuild = "253.28294"
            xml.untilBuild = "253.*"
          },
          signPluginOptions := signPluginOptions.value.copy(enabled = true)
        )

lazy val example = project.settings(commonSettings, packageMethod := PackagingMethod.Skip())

lazy val `example-stepdefs-lib` = project.settings(commonSettings, packageMethod := PackagingMethod.Skip())

lazy val root = (project in file("."))
        .aggregate(`cucumber-scala`, example, `example-stepdefs-lib`)
        .settings(
          name := "intellij-cucumber-scala"
        )
