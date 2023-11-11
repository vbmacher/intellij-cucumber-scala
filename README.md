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

To setup the development environment, follow the following steps:

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. Configure JDK 17
3. Open the project in IDEA

The plugin is using [sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin). Please refer to that plugin documentation
to get information about available tasks.

To start an IDE with the plugin installed in the example project, run `runIDE` task. Import the whole project
as gradle project in the sandbox-ide. Wait for indexing to finish.
Open `example/src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature`.

In order to build the plugin (and package the plugin ZIP), run: `sbt test && sbt cucumber-scala / packageArtifactZip`

## Contributing

Anyone can contribute. The best is to pick up some issue tagged with `help_wanted`, or bringing new ideas by creating new issues.

Git branch `development` is used for developing upcoming version. Upon releasing the plugin, the branch will be merged
into `master` branch, which is then used for the release. The release commit is tagged with version.
 
## Publishing

A "publish token" must be set up in order to publish the plugin to [JetBrains plugins portal](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala).
The token can be set to a system variable named `IJ_PLUGIN_REPO_TOKEN`, or in a file `~/.ij-plugin-repo-token`. Please
refer to [sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin#publishplugin-channel--inputkeystring) for more
information.

In order to sign the plugin, set system variables `PLUGIN_SIGN_KEY`, `PLUGIN_SIGN_CERT` and `PLUGIN_SIGN_KEY_PWD` (again,
please refer to [sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin#signplugin--taskkeyfile) for more information, or https://plugins.jetbrains.com/docs/intellij/plugin-signing.html#signing-methods).

Then, run `publishPlugin` task.

## License

This project is released under the __Apache License, Version 2.0__ (http://www.apache.org/licenses/LICENSE-2.0).
