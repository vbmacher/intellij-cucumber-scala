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
    "--add-opens", "java.desktop/sun.font=ALL-UNNAMED",
    "--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED",
  ),
  version := "2023.3.2",
  scalaVersion := "2.13.10",
  libraryDependencies ++= Seq(
    "junit" % "junit" % "4.13.2" % Test,
    "io.cucumber" %% "cucumber-scala" % "8.20.0",
    "io.cucumber" % "cucumber-junit" % "7.15.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    "org.scalatestplus" %% "junit-4-13" % "3.2.17.0" % Test,
    "org.slf4j" % "slf4j-reload4j" % "2.0.9"
  )
)

lazy val `cucumber-scala` = project
        .enablePlugins(SbtIdeaPlugin)
        .settings(
          commonSettings,
          ThisBuild / intellijPluginName := "intellij-cucumber-scala",
          ThisBuild / intellijBuild := "233.13135.103",
          ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
          Compile / javacOptions ++= "--release" :: "17" :: Nil,
          intellijPlugins ++= Seq(
            "org.intellij.scala:2023.3.19".toPlugin,
            "gherkin:233.11799.165".toPlugin
          ),
          packageMethod := PackagingMethod.Standalone(),
          patchPluginXml := pluginXmlOptions { xml =>
            xml.version = version.value
            xml.sinceBuild = "233.11799"
            xml.untilBuild = "233.*"
          },
          signPluginOptions := signPluginOptions.value.copy(enabled = true)
        )

lazy val example = project.settings(commonSettings)

lazy val `example-stepdefs-lib` = project.settings(commonSettings)

lazy val root = (project in file("."))
        .aggregate(`cucumber-scala`, example, `example-stepdefs-lib`)
        .settings(
          name := "intellij-cucumber-scala"
        )
