intellij-cucumber-scala
=======================

[![Build Status](https://travis-ci.org/danielwegener/intellij-cucumber-scala.svg)](https://travis-ci.org/danielwegener/intellij-cucumber-scala)

Enables Reference tracking of glue code when using cucumber-scala DSL.

# Features

- [x] Navigate from feature step to step definition
- [ ] Find usages of step definitions in feature files
- [ ] Wizard that creates step definitions for a step in a feature file


# Development

1. `git clone git@github.com:danielwegener/intellij-cucumber-scala.git`
2. `sbt updateIdea` will download the idea sdk to the SDK folder and all required plugins
3. Import the project as sbt project into IDEA.

Now you can build this plugin with `sbt package`

> IntelliJ Plugin Development is too hard :/ Please provide a public (maybe non-oss, non-free) repo for Idea artifacts. Or maybe a sbt plugin.

To start an IDE with the plugin installed in the example project just run `sbt "runIdea example"` (_todo: does not work yet_)


# Using a run configuration
(set it to 'single instance only' so it kills the current version when restarting)

This is all reverse engineered (in reality playing a game of spot the difference)
with the scala plugin run configurations.

https://github.com/JetBrains/intellij-scala/blob/8030aef21f8f726796f7230d5f29669e35ebeec9/.idea/runConfigurations/IDEA.xml#L7

It is quite important that scala library does not get added to the classpath etc. If there are
problems run the scala plugin by

1. Checkout https://github.com/JetBrains/intellij-scala
2. Open in Intellij
3. git checkout idea as that will demangle the run configuration so it can be ran easily

When running a configuration to analyse the output of the run window replace : with new lines
so it is diffable.

## Main class
```
com.intellij.idea.Main
```

## VM Options (Note this is using build id 181.4445.78)
``` 
-Xmx800m
-XX:ReservedCodeCacheSize=240m
-XX:+HeapDumpOnOutOfMemoryError
-ea
-Didea.is.internal=true
-Didea.debug.mode=true
-Dapple.laf.useScreenMenuBar=true
-Dplugin.path=target/scala-2.12/intellij-cucumber-scala_2.12-2018.1.0.jar
-Didea.platform.prefix=Idea
-Didea.system.path=idea/system
-Didea.config.path=idea/config
-Didea.plugins.path=idea/181.4445.78/externalPlugins
```

If system and config outside of extraction directory then it is easier to
purge when a new version is out. You don't have to go through the whole
running for the first time wizard.


## Use classpath of module
```
runner-cucumber-scala-idea
```

## Optional but useful
Under run configuration create additional before launch external tool
1. Name: sbt_package
2. Program: sbt
3. Arguments: package

This will mean that a fresh plugin jar is created each time the test community
edition launches from the run configuration.

# Publishing


1. Add your credentials to `ideaPublishSettings` in `build.sbt` (make sure to not check them in!)
2 run `sbt publishPlugin`

# License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).
