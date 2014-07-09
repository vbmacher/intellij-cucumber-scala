intellij-cucumber-scala
=======================

Enables Reference tracking of glue code when using cucumber-scala DSL.

#Features

- [x] Navigate from feature step to step definition
- [ ] Find usages of step definitions in feature files
- [ ] Wizard that creates step definitions for a step in a feature file


#Development

1. `git clone git@github.com:danielwegener/intellij-cucumber-scala.git`
2. Download and unpack IDEA-CE or UE
 - _Scala_ Plugin
 - _cucumber_ plugin
 - _IntelliJ Plugin development with Maven_ plugin
4. Import the project as maven project into IDEA. Make sure to use your prepared IDEA version as plugin sdk.
5. Make sure that _Project Structure -> Module -> intellij-cucumber-scala -> Plugin Deployment -> Path to META-INF/plugin.xml ends with `intellij-cucumber-scala/src/main/resources`_

Now you can build this plugin with `mvn -Didea.home=PATH_TO_IDEA clean install` or use a IDEA plugin run configuration to debug it.

> IntelliJ Plugin Development is too hard :/ Please provide a public (maybe non-oss, non-free) repo for Idea artifacts.

#License
This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).
