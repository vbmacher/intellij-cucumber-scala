# Contributing

Choose an issue labelled `help_wanted`, report a reproducible problem, or propose a
feature in a GitHub issue. Follow [AGENTS.md](AGENTS.md) for changes in this repository.
Development prerequisites, build commands and publishing credentials belong in the
[installation guide](docs/installation.md#development-environment).

Use the `development` branch for upcoming work. Releases merge it into `master`
and tag the release commit with the version.

## Source layout

Plugin sources live under
`cucumber-scala/src/main/scala/com/github/vbmacher/intellij/cucumber/scala/`:

| Package | Contents |
| --- | --- |
| `steps` | Scala DSL, step index, definitions, parameter types and usages |
| `scenarios` | Scenario declarations, executable rows, inherited tags and applicable Backgrounds |
| `completion` | Step wording, templates and custom-parameter completion |
| `creation` | Single and batch step generation |
| `drafts` | Reviewed model-assisted scenario drafts and insertion |
| `refactoring` | Rename and existing-wording suggestions |
| `editor` | Quick Documentation, hooks, requirements and numbering |
| `inspections` | Gherkin and Scala step inspections |
| `tables` | Table editing, mappings and case-class generation |
| `run` | Run configurations, console, results and history |
| `debug` | Feature breakpoints and debugger context |
| `suite` | Tool window, catalog, analysis, search and implementation viewer |
| `publish` | Feature preview and HTML/PDF export |
| `settings` | Project settings and settings UI |
| `util` | Shared dialogs and Gherkin file actions |

`cucumber-scala/src/main/resources/META-INF/plugin.xml` registers plugin extensions.
`build.sbt` defines the supported SDK, dependencies and packaging.
Fixtures and test data live under `cucumber-scala/src/test/`. The `example/` and
`example-stepdefs-lib/` projects provide feature files and Scala definitions to explore.

## Documentation

Start at [getting started](docs/getting_started.md) and keep detailed behavior in the
corresponding topic guide. Keep all installation procedures in
[installation.md](docs/installation.md) and all settings/file-format descriptions in
[configuration.md](docs/configuration.md). Describe current user-visible behavior
with exact action labels, small examples and explicit prerequisites. Do not add ticket
histories, test statistics or coverage inventories to the guides.
