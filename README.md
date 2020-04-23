# intellij-cucumber-scala

![Build for intellij-cucumber-scala](https://github.com/vbmacher/intellij-cucumber-scala/workflows/Build%20for%20intellij-cucumber-scala/badge.svg)

Enables Reference tracking of glue code when using cucumber-scala DSL.

## Features

- [x] Navigate from feature step to step definition
- [x] Find usages of step definitions in feature files
- [x] Wizard that creates step definitions for a step in a feature file


## Development

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. `./gradlew` will download the idea sdk to the SDK folder and all required plugins
3. Import the project as gradle project into IDEA.

Now you can build this plugin with `./gradlew buildPlugin`.

To start an IDE with the plugin installed in the example project just run `./gradlew runIde`. Import the whole project as gradle project in the sandbox-ide. Wait for indexing to finish. Open `example/src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature`.

## Contributing

Anyone can contribute. The best is to pick up some issue tagged with `help_wanted`, or bringing new ideas by creating new issues.

Git branch `development` is used for developing upcoming version. When the plugin is to be released, the
branch is merged into `master` branch, which is then used for the release. The release commit is tagged.
 
## Publishing

A "publish token" must be set up in order to publish the plugin to [JetBrains plugins portal](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala). The token can be set up either by system variable or Gradle property named `PUBLISH_TOKEN` (e.g. put it in `gradle.properties` file, but do not commit it!).

Then, run `./gradlew publishPlugin`

## License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).
