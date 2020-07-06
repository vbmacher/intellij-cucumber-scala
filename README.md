# intellij-cucumber-scala

![Build for intellij-cucumber-scala](https://github.com/vbmacher/intellij-cucumber-scala/workflows/Build%20for%20intellij-cucumber-scala/badge.svg)

A [plugin to IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala),
enabling navigation between step definitions and gherkin steps when using cucumber-scala DSL.

The plugin depends on:
- [Gherkin plugin](https://plugins.jetbrains.com/plugin/9164-gherkin)
- [Scala plugin](https://plugins.jetbrains.com/plugin/1347-scala)
- project [cucumber-jvm-scala](https://github.com/cucumber/cucumber-jvm-scala)

## Features

- [x] Navigate from feature step to step definition
- [x] Find usages of step definitions in feature files
- [x] Automated step definition creation
- [x] Wizard for step definition creation (templating)
- [x] Indexing of step definitions for better performance
- [x] Support "constant expressions" in step definition names
- [x] Find step definitions in libraries with attached sources
- [ ] Support for Java annotations

## Development

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. `./gradlew` will download the idea sdk to the SDK folder and all required plugins
3. Import the project as gradle project into IDEA.

Now you can build this plugin with `./gradlew buildPlugin`.

To start an IDE with the plugin installed in the example project, run `runIde` task. Import the whole project
as gradle project in the sandbox-ide. Wait for indexing to finish.
Open `example/src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature`.

## Contributing

Anyone can contribute. The best is to pick up some issue tagged with `help_wanted`, or bringing new ideas by creating new issues.

Git branch `development` is used for developing upcoming version. Upon releasing the plugin, the branch will be merged
into `master` branch, which is then used for the release. The release commit is tagged with version.
 
## Publishing

A "publish token" must be set up in order to publish the plugin to [JetBrains plugins portal](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala).
The token can be set up either by system variable or Gradle property named `PUBLISH_TOKEN` (e.g. put it in `gradle.properties` file, but do not commit it!).

Then, run `./gradlew publishPlugin`

## License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).

<a href="https://www.buymeacoffee.com/vbmacher" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>
