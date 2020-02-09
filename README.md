intellij-cucumber-scala
=======================

[![Build Status](https://travis-ci.com/vbmacher/intellij-cucumber-scala.svg?branch=master)](https://travis-ci.com/vbmacher/intellij-cucumber-scala)

Enables Reference tracking of glue code when using cucumber-scala DSL.

# Features

- [x] Navigate from feature step to step definition
- [ ] Find usages of step definitions in feature files
- [ ] Wizard that creates step definitions for a step in a feature file


# Development

1. `git clone git@github.com:danielwegener/intellij-cucumber-scala.git`
2. `./gradlew` will download the idea sdk to the SDK folder and all required plugins
3. Import the project as gradle project into IDEA.

Now you can build this plugin with `./gradlew buildPlugin`.

To start an IDE with the plugin installed in the example project just run `./gradlew runIde`. Import the whole project as gradle project in the sandbox-ide. Wait for indexing to finish. Open `example/src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature`.
 
# Publishing
s
1. Add your publish token to `publishPlugin.token` in `cucumber-scala/build.gradle` (make sure to not check them in!)
2. run `./gradlew publishPlugin`

# License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).
