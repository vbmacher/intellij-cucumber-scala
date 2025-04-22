lazy val commonSettings = Seq(
  fork := true,
  javaOptions ++= Seq(
    "--add-opens", "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens", "java.desktop/com.sun.java.swing.platf.gtk=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing.text=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.font=ALL-UNNAMED",
    "--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
  ),
  version := "2025.1",
  scalaVersion := "2.13.16",
  libraryDependencies ++= Seq(
    "junit" % "junit" % "4.13.2" % Test,
    "io.cucumber" %% "cucumber-scala" % "8.27.0",
    "io.cucumber" % "cucumber-junit" % "7.22.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "org.scalatestplus" %% "junit-4-13" % "3.2.19.1" % Test,
    "org.opentest4j" % "opentest4j" % "1.3.0" % Test
  )
)

lazy val `cucumber-scala` = project
        .enablePlugins(SbtIdeaPlugin)
        .settings(
          commonSettings,
          ThisBuild / intellijPluginName := "intellij-cucumber-scala",
          ThisBuild / intellijBuild := "251.23774.435",
          ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
          Compile / javacOptions ++= "--release" :: "21" :: Nil,
          intellijPlugins ++= Seq(
            "org.intellij.scala:2025.1.20".toPlugin,
            "gherkin:251.23774.318".toPlugin
          ),
          intellijVMOptions := intellijVMOptions.value.copy(
            defaultOptions = Seq(
              "--add-opens", "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
              "--add-opens", "java.desktop/com.apple.laf=ALL-UNNAMED",
              "--add-opens", "java.desktop/com.sun.java.swing.platf.gtk=ALL-UNNAMED",
              "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
              "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED",
              "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED",
              "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
              "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED",
              "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
              "--add-opens", "java.desktop/javax.swing.text=ALL-UNNAMED",
              "--add-opens", "java.desktop/javax.swing.text.html=ALL-UNNAMED",
              "--add-opens", "java.desktop/sun.font=ALL-UNNAMED",
              "--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED",
              "--add-exports", "java.desktop/sun.swing=ALL-UNNAMED",
              "--add-exports", "java.desktop/sun.awt.image=ALL-UNNAMED",
              "--add-exports", "java.desktop/com.apple.eawt.event=ALL-UNNAMED",
              "--add-exports", "java.desktop/com.apple.eawt=ALL-UNNAMED",
              "--add-exports", "java.desktop/java.awt.peer=ALL-UNNAMED",
              "--add-exports", "java.base/sun.nio.fs=ALL-UNNAMED",
              "--add-exports", "java.management/sun.management=ALL-UNNAMED",
              "--add-opens", "java.base/java.lang=ALL-UNNAMED",
              "--add-opens", "java.base/java.util=ALL-UNNAMED",
            )
          ),
          packageMethod := PackagingMethod.Standalone(),
          patchPluginXml := pluginXmlOptions { xml =>
            xml.version = version.value
            xml.sinceBuild = "243.22562"
            xml.untilBuild = "243.*"
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
