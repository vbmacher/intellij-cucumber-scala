# intellij-cucumber-scala

[![Build](https://img.shields.io/github/actions/workflow/status/vbmacher/intellij-cucumber-scala/build.yml?style=flat-square)](https://github.com/vbmacher/intellij-cucumber-scala/actions?query=branch%3Amaster)
[![Version](https://img.shields.io/jetbrains/plugin/v/7460-cucumber-for-scala.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/7460-cucumber-for-scala?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)

A [plugin for IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala) that
connects Gherkin feature files with step definitions written in the
[cucumber-scala](https://github.com/cucumber/cucumber-jvm-scala) DSL.

## Features

See the [example guide](example/README.md) for hands-on examples of each feature.

### Navigation

- Jump between feature steps and Scala step definitions, including definitions in libraries with
  attached sources. Available once indexing finishes.
- Supported step definition names:
  - anchored and slash-delimited regular expressions, e.g. `"^I add (\\d+)$"` or `"/I add [0-9]+/"`
  - constant expressions, e.g. `When("""I do 5 + 5, it's""" + (5+5))`
  - [parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#parameter-types),
    e.g. `When("""I divide {int} by {int}""")`
  - [custom parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#custom-parameter-types)
    defined with `ParameterType(name, regex)`
  - [alternative text](https://cucumber.io/docs/cucumber/cucumber-expressions/#alternative-text),
    e.g. `When("""I/We divide (\d+) by (\d+)""")`
  - [optional text](https://cucumber.io/docs/cucumber/cucumber-expressions/#optional-text),
    e.g. `When("""I do some nop(s)""")`
  - [escaping](https://cucumber.io/docs/cucumber/cucumber-expressions/#escaping),
    e.g. `When("""I have 42 \{int} cucumbers in my belly \(amazing!)""")`
- **Go to Scala Hooks…** lists hooks, filterable by tag expression.

### Writing steps

- Create a step definition for an undefined step, or all missing ones at once with
  **Create Missing Scala Steps…**.
- Step completion with live templates, usage counts and ranking options.
- Quick Documentation shows matching definitions, parameters and usages.
- Rename a step definition together with its feature usages.
- **Use existing Scala step wording…** suggests similar defined steps for an undefined one.

### Suite tool window

**Tools → Scala Cucumber Suite** gives an overview of a project or module:

- **Suite** – browse and filter steps and scenarios, group them by module, tag or category, and
  find undefined, ambiguous, unused and duplicate steps or scenarios.
- **Run History** – compare outcomes of previous runs.
- **Implementation** – show the Scala definition of the step under the caret.
- **Scenario Search** – find scenarios by exact text or, optionally, by meaning using a local
  [Ollama](https://ollama.com) embedding model (configured in **Settings → Scala Cucumber**;
  default `http://localhost:11434`, model `embeddinggemma`).

Categories are configured in **Settings → Scala Cucumber** as `category name => tag expression`,
one per line.

### Run and debug

- Run or debug a feature, scenario, outline or a single Examples row from the gutter.
- Filter runs by tag expression and rerun only failed scenarios.
- See run results in the feature editor gutter.
- Set breakpoints on feature steps and Examples rows.

### Tables

- Optional spreadsheet-like editing in **Settings → Scala Cucumber**: Tab/Enter to move between
  cells and add rows.
- **Editor context menu → Gherkin Table**: copy/paste as TSV, move, delete and transpose rows and
  columns.
- **Generate Scala Case Class…** from a table.
- Navigate and complete table headers mapped to case-class fields by a `DataTableType`. Here,
  header `owner` navigates to `TransferRow.account`:

  ```scala
  case class TransferRow(account: String, amount: Int)
  DataTableType { (entry: Map[String, String]) =>
    TransferRow(entry("owner"), entry("amount").toInt)
  }
  Given("transfers") { (rows: Seq[TransferRow]) => () }
  ```

### Inspections

- Step definition parameters not matching the step expression.
- Inconsistent Examples headers and placeholders in scenario outlines.
- Optional project style checks for titles, tags and step order.

### Preview, export and requirements

- **Source / Preview / Split** view of feature files.
- **Export Features…** to HTML or PDF.
- Link tags to requirements in **Settings → Scala Cucumber**, e.g.
  `@REQ-(.*) => docs/requirements.md#$1` or a URL template.
- **Number Scenario Titles…** numbers scenario titles on demand.

## Development

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. [Configure JDK 25](https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-jdk-and-intellij-platform-plugin-sdk).
3. Open the project in IntelliJ IDEA.

Commands:

- Test: `sbt --batch test`
- Package the plugin ZIP: `sbt --batch 'cucumber-scala / packageArtifactZip'`
- Run a sandbox IDE: `sbt 'cucumber-scala / runIDE'`, then import the project as an sbt project.

Plugin sources live under `cucumber-scala/src/main/scala/com/github/vbmacher/intellij/cucumber/scala/`,
grouped by feature:

| Package | Contents |
| --- | --- |
| `steps` | Scala DSL model, step index, step definitions, parameter types, usages and the Cucumber extension |
| `completion` | Step, template and custom-parameter completion |
| `creation` | Single and batch step definition generation |
| `refactoring` | Step rename and existing-wording suggestions |
| `editor` | Quick Documentation, hooks, requirement links and scenario numbering |
| `inspections` | Gherkin and Scala step inspections |
| `tables` | Gherkin table editing, navigation, DataTable mappings and case-class generation |
| `run` | Run configurations, producers, console, Cucumber messages and run results/history |
| `debug` | Feature breakpoints and debugger context |
| `suite` | Suite tool window, catalog, analysis, scenario search and paired step viewer |
| `publish` | Feature preview, HTML and PDF export |
| `settings` | Project settings and their configurable |
| `util` | Shared dialogs and Gherkin file action base |

Tests live under `cucumber-scala/src/test/scala`. See the [implementation map](IMPLEMENTATION.md)
for test coverage and manual verification.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Publishing

Publishing to the [JetBrains plugins portal](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)
requires a publish token in the `IJ_PLUGIN_REPO_TOKEN` environment variable or the
`~/.ij-plugin-repo-token` file (see
[sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin#publishplugin-channel--inputkeystring)).

Signing requires the `PLUGIN_SIGN_KEY`, `PLUGIN_SIGN_CERT` and `PLUGIN_SIGN_KEY_PWD` environment
variables (see [sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin#signplugin--taskkeyfile)
and [plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html#signing-methods)).

Then run the `publishPlugin` task.

## License

This project is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
