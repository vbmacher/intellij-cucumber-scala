import Keys.{`package` => pack}

name :=  "Cucumber for Scala"

version := "0.3.0"

scalaVersion :=  "2.11.2"

//libraryDependencies +=  "org.scala-lang" % "scala-reflect" % scalaVersion.value

//libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

//unmanagedSourceDirectories in Compile += baseDirectory.value /  "src"

//unmanagedSourceDirectories in Test += baseDirectory.value /  "test"

//unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions in Global += "-target:jvm-1.6"

ideaVersion := "139.224.1"

scalariformSettings

//  "http://plugins.jetbrains.com/pluginManager/?action=download&id=org.intellij.scala&build=IU-139.224&uuid=81756afd-dc0b-426a-b7c1-72ed100d35bf"

//libraryDependencies +=  "com.jetbrains.plugins" % "org.intellij.scala" % "current" from s"http://plugins.jetbrains.com/pluginManager/?action=download&id=org.intellij.scala&build=$ideaRepoVersion&uuid=81756afd-dc0b-426a-b7c1-72ed100d35bf"

libraryDependencies +=  "com.jetbrains.plugins" % "org.intellij.scala" % "current" from s"file:///opt/idea-IC-139.224.1/plugins/Scala/lib/scala-plugin.jar"

libraryDependencies +=  "com.jetbrains.plugins" % "cucumber" % "current" from s"file:///opt/idea-IC-139.224.1/plugins/cucumber/lib/cucumber.jar"

ideaBasePath in Global := baseDirectory.value / "SDK" / "ideaSDK" / s"idea-${ideaVersion.value}"

//ideaPluginBasePath in Global := baseDirectory.value / "SDK" / "plugins" / s"idea-${ideaVersion.value}"

ideaBaseJars in Global := (ideaBasePath.value  / "lib" * "*.jar").classpath

unmanagedJars in Compile := ideaBaseJars.value

unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

lazy val `cucumber-scala` = project.in(file( "."))



ideaResolver := {
  val ideaVer = ideaVersion.value
  val ideaSDKPath = ideaBasePath.value.getParentFile
  val ideaArchiveName = ideaSDKPath.getAbsolutePath + s"/ideaSDK${ideaVersion.value}.arc"
  def renameFun = (ideaSDKPath.listFiles sortWith { _.lastModified > _.lastModified }).head.renameTo(ideaBasePath.value)
  val s = ideaVer.substring(0, ideaVer.indexOf('.'))
  IdeaResolver(
    teamcityURL = "https://teamcity.jetbrains.com",
    buildTypes = Seq("bt410"),
    branch = s"idea/${ideaVersion.value}",
    artifacts = Seq(
      System.getProperty("os.name") match {
        case r"^Linux"     => (s"/ideaIC-$s.SNAPSHOT.tar.gz", ideaArchiveName, Some({ _: File => s"tar xvfz $ideaArchiveName -C ${ideaSDKPath.getAbsolutePath}".!; renameFun}))
        case r"^Mac OS.*"  => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => s"unzip $ideaArchiveName -d ${ideaBasePath.value}".!; renameFun}))
        case r"^Windows.*" => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => IO.unzip(file(ideaArchiveName), ideaBasePath.value); renameFun}))
        case other => throw new IllegalStateException(s"OS $other is not supported")
      },
      ("/sources.zip",  ideaBasePath.value.getAbsolutePath + "/sources.zip")
    )
  )
}



//downloadPlugins := {
//  val log = streams.value.log
//  val pluginsPath = ideaPluginBasePath.value.getParentFile
//  val resolver = (ideaResolver in Compile).value
//  val buildId = getBuildId(resolver).getOrElse("")
//  def artifactUrl(pluginId:String) = resolver.teamcityURL + s"https://plugins.jetbrains.com/pluginManager/?action=download&id=$pluginId&build=$buildId&uuid=81756afd-dc0b-426a-b7c1-72ed100d35bf"
//  if (!pluginsPath.exists) pluginsPath.mkdirs
//  def downloadDep(art: TCArtifact): Unit = {
//    val fileTo = file(art.to)
//    if (!fileTo.exists() || art.overwrite) {
//      log.info(s"downloading${if (art.overwrite) "(overwriting)" else ""}: ${art.from} -> ${fileTo.getAbsolutePath}")
//      IO.download(url(artifactBaseUrl + art.from), fileTo)
//      log.success(s"download of ${fileTo.getName} finished")
//      art.extractFun foreach { func =>
//        log.info(s"extracting from archive ${fileTo.getName}")
//        func(fileTo)
//        log.success("extract finished")
//      }
//    } else log.info(s"$fileTo already exists, skipping")
//  }
//  resolver.artifacts foreach downloadDep
//}

downloadIdea := {
  val log = streams.value.log
  val ideaSDKPath = ideaBasePath.value.getParentFile
  val resolver = (ideaResolver in Compile).value
  val buildId = getBuildId(resolver).getOrElse("")
  val artifactBaseUrl = resolver.teamcityURL + s"/guestAuth/app/rest/builds/id:$buildId/artifacts/content"
  if (!ideaSDKPath.exists) ideaSDKPath.mkdirs
  def downloadDep(art: TCArtifact): Unit = {
    val fileTo = file(art.to)
    if (!fileTo.exists() || art.overwrite) {
      log.info(s"downloading${if (art.overwrite) "(overwriting)" else ""}: ${art.from} -> ${fileTo.getAbsolutePath}")
      IO.download(url(artifactBaseUrl + art.from), fileTo)
      log.success(s"download of ${fileTo.getName} finished")
      art.extractFun foreach { func =>
        log.info(s"extracting from archive ${fileTo.getName}")
        func(fileTo)
        log.success("extract finished")
      }
    } else log.info(s"$fileTo already exists, skipping")
  }
  resolver.artifacts foreach downloadDep
}

// tests

fork in Test := true

parallelExecution := true

javaOptions in Test := Seq(
//  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
  "-Xms128m",
  "-Xmx1024m",
  "-XX:MaxPermSize=350m",
  "-ea",
  s"-Didea.system.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-system",
  s"-Didea.config.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-config",
  s"-Dplugin.path=${baseDirectory.value}/out/plugin/Scala"
)

//fullClasspath in Test := (fullClasspath in (SBT, Test)).value

baseDirectory in Test := baseDirectory.value.getParentFile

// packaging

pack in Compile <<= (pack in Compile) dependsOn (
  pack in (`cucumber-scala`, Compile)
  )


packageStructure in Compile := Seq(
    (artifactPath in (`cucumber-scala`, Compile, packageBin)).value -> "lib/")

packagePlugin in Compile := {
  val (dirs, files) = (packageStructure in Compile).value.partition(_._1.isDirectory)
  val base = baseDirectory.value / "out" / "plugin" / "Scala"
  IO.delete(base.getParentFile)
  dirs  foreach { case (from, to) => IO.copyDirectory(from, base / to, overwrite = true) }
  files foreach { case (from, to) => IO.copyFile(from, base / to)}
}

packagePlugin in Compile <<= (packagePlugin in Compile) dependsOn (pack in Compile)

packagePluginZip in Compile := {
  val base = baseDirectory.value / "out" / "plugin"
  val zipFile = baseDirectory.value / "out" / "scala-plugin.zip"
  IO.zip((base ***) pair (relativeTo(base), false), zipFile)
}

packagePluginZip in Compile <<= (packagePluginZip in Compile) dependsOn (packagePlugin in Compile)

