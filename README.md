# intellij-cucumber-scala

![Build for intellij-cucumber-scala](https://github.com/vbmacher/intellij-cucumber-scala/workflows/Build%20for%20intellij-cucumber-scala/badge.svg)

A [plugin to IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala),
enabling navigation between step definitions and gherkin steps when using cucumber-scala DSL.

## Features

### Navigation from feature step to step definition (and back)
  - Indexing of step definitions for better performance
  - Finds step definitions also in libraries with attached sources
  - Supports "constant expressions" in step definition names (e.g. `"When("""I do 5 + 5, it's""" + (5+5))`)
  - Supports using [parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#parameter-types) in 
    step definition names (e.g. `When("""I divide {int} by {int}""")`)  
  - Supports navigation of [custom parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#custom-parameter-types)
    (using `ParameterType(name, regex)` definitions)
  - Supports [alternative text](https://cucumber.io/docs/cucumber/cucumber-expressions/#alternative-text)
    in step definition names (e.g. `When("""I/We divide (\d+) by (\d+)""")`)  
  - Supports [optional text](https://cucumber.io/docs/cucumber/cucumber-expressions/#optional-text)
    in step definition names (e.g. `When("""I do some nop(s)""")`)
  - Supports [escaping](https://cucumber.io/docs/cucumber/cucumber-expressions/#escaping)
    (e.g. `When("""I have 42 \{int} cucumbers in my belly \(amazing!)""")`)

### Support of automated creation of a step definition

  - Supports template filling wizard

## Development

The plugin depends on:
- [Gherkin plugin](https://plugins.jetbrains.com/plugin/9164-gherkin)
- [Scala plugin](https://plugins.jetbrains.com/plugin/1347-scala)
- project [cucumber-jvm-scala](https://github.com/cucumber/cucumber-jvm-scala)

To setup the development environment, follow these steps:

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. Configure JDK 11
3. Open the project in IDEA
4. Wait until `gradle-intellij-plugin` downloads IntelliJ SDK and required plugins.

Now you can build this plugin with `build` task.

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

Then, run `publishPlugin` task.

## License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).

<a href="https://www.buymeacoffee.com/vbmacher" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>
