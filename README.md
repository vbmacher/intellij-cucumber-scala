# intellij-cucumber-scala

[![Build](https://img.shields.io/github/actions/workflow/status/vbmacher/intellij-cucumber-scala/build.yml?style=flat-square)](https://github.com/vbmacher/intellij-cucumber-scala/actions?query=branch%3Amaster)
[![Version](https://img.shields.io/jetbrains/plugin/v/7460-cucumber-for-scala.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/7460-cucumber-for-scala?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)

A [plugin to IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala),
enabling navigation between step definitions and gherkin steps when using cucumber-scala DSL.

Step navigation is disabled while IntelliJ is indexing and becomes available when indexing finishes.

## Features

### Navigation from feature step to step definition (and back)
  - Indexing of step definitions for better performance
  - Finds step definitions also in libraries with attached sources
  - Supports anchored and slash-delimited regular expressions (e.g. `"^I add (\\d+)$"` or `"/I add [0-9]+/"`)
  - Supports "constant expressions" in step definition names (e.g. `"When("""I do 5 + 5, it's""" + (5+5))`)
  - Supports using [parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#parameter-types) in
    step definition names (e.g. `When("""I divide {int} by {int}""")`)
  - Supports navigation of [custom parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#custom-parameter-types)
    (using `ParameterType(name, regex)` definitions)
    declared in separate glue classes or traits in the step search scope
  - Supports [alternative text](https://cucumber.io/docs/cucumber/cucumber-expressions/#alternative-text)
    in step definition names (e.g. `When("""I/We divide (\d+) by (\d+)""")`)
  - Supports [optional text](https://cucumber.io/docs/cucumber/cucumber-expressions/#optional-text)
    in step definition names (e.g. `When("""I do some nop(s)""")`)
  - Supports [escaping](https://cucumber.io/docs/cucumber/cucumber-expressions/#escaping)
    (e.g. `When("""I have 42 \{int} cucumbers in my belly \(amazing!)""")`)

### Support of automated creation of a step definition

  - Supports template filling wizard
  - Includes trailing arguments for data tables and doc strings (`String`)
  - Generates wildcard (`*`) definitions using the preceding `Given`, `When` or `Then`
    in the same scenario or background; uses `Given` when none precedes it
  - Existing destination types need a body (`{ ... }`) for insertion

### Authoring and navigation

- Completion retains native matching, shows source/module/tags and distinct matching-scenario counts,
  and optionally ranks by reuse and context. Feature-only wording is labelled undefined.
- Parameterized completion starts an editable live template with runtime-valid example values.
  Complex custom expressions without a usable example ask for matching wording. Scala expression
  strings complete built-in and indexed custom parameter names after `{`.
- Quick Documentation shows exact candidates, captures, trailing arguments, deprecation metadata,
  usage counts and a navigable scenario preview. Ambiguous matches remain ambiguous.
- Native Rename previews changes to simple literal Cucumber Expressions and their feature usages,
  preserving arguments and keywords. It rejects invalid expressions and conflicting wording.
  Dynamic, regex, optional/alternative and ambiguous cases require manual review.
- **Create Missing Scala Steps…** previews and deduplicates unresolved steps from the current feature
  or selection before inserting into an existing writable Scala type. Localized feature keywords
  generate the corresponding Scala `Given`, `When` or `Then`; custom parameter return types are
  inferred from the transformer. Conflicting choices require an explicit selection.
- **Use existing Scala step wording…** offers bounded nearby wording suggestions for undefined steps,
  preserves captures and previews the replacement. It never changes exact resolution.
- **Go to Scala Hooks…** lists indexed hooks with runtime tag-expression filtering. Dynamic conditions
  are explicitly unknown, rather than silently excluded.

### Suite tool window

Open **Tools → Scala Cucumber Suite**. Select project contents or a module, then search expressions
or scenarios. Filter step kind, group by module/tag/configured category, navigate or preview sources,
insert editable step wording, and run selected scenarios from one module. Scenario filters accept
Cucumber tag expressions. Category mappings in **Settings → Scala Cucumber** use
`category name => tag expression`, one per line.

The health categories list undefined/ambiguous usages, scoped unused definitions, identical
expressions and duplicate ordered scenario bodies. Counts distinguish feature files, scenarios
(outlines once), example rows, step usages and definitions. Background usages expand to their
applicable scenarios. Ambiguous matches count for each candidate. Static counts do not measure
execution or business coverage; external consumers of an apparently unused definition are unknown.
Refresh is cancellable; cancelled results are labelled incomplete.

The **Implementation** tab optionally follows the feature caret, lets you select exact Scala
candidates and pin the view. Following is off initially and does not move the feature caret.

### Run, debug and results

Use native Run/Debug actions on a feature, scenario, outline or individual Examples data row.
The **Scala Cucumber** run configuration uses the owning module's test classpath and JDK, discovers
visible packages containing steps, hooks and type/transformer registrations, and exposes glue,
working directory, VM, environment and reporter overrides. Saved configurations retain scenario
selections.
Missing runtime/JDK and ambiguous content roots are reported before launch. Tags are completed
from the module, validated with Cucumber `tag-expressions`, persisted and passed as literal arguments.
The preview lists executable scenarios/example rows, including inherited Examples tags.

The native test tree uses Cucumber message identities for features, rules, outlines, rows, steps and
hooks. Failed-test reruns select scenario/example locations, so duplicate titles do not select
unrelated scenarios. Feature gutter results and Quick Documentation show run identity, status,
duration and errors. Source changes mark results stale. Disable or clear editor decorations in
settings without removing the native console results.

Feature-step and Examples-row breakpoints have their own debugger requests and preserve existing
Scala breakpoint conditions. A step needs one exact Scala definition; a row needs all its steps
resolved uniquely. Requests filter by feature URI, step line and selected example line on the
suspended runtime thread. Active steps/rows are highlighted in open feature editors and cleared
on resume/termination. The debugger adapter targets the pinned Cucumber Scala/core runtime;
unsupported runtime context is left unmatched.

Optional **Run History** stores workspace-local outcomes with scenario/example fingerprints,
revision, runtime/environment context, timestamps and attempts. Retention is configurable; history
can be disabled or cleared. Comparisons show initial-attempt denominators, retries and interruptions
separately. Mixed outcomes are investigation signals, not root-cause claims. Renamed, changed or
deleted scenario identities are retained separately and are not assigned to another scenario.

### Tables and inspections

Enable cell navigation in **Settings → Scala Cucumber**. Tab/Shift-Tab move between cells (the first
cell stays first); Tab past the last cell or Enter below the last row adds an aligned row. Enter
moves down in the same column. An unescaped pipe navigates to the next cell; `\|` remains literal
content. Delete clears selected cells. These options leave ordinary typing outside tables alone.

**Editor context menu → Gherkin Table** contains rectangular TSV copy/cut/paste, clear, explicit
row/column deletion, movement and transpose actions. Assign keyboard shortcuts through Keymap.
Paste previews overwrite from the upper-left selected cell and expands as needed, preserving cells
outside the pasted rectangle. Quoted TSV preserves tabs/newlines; Gherkin escapes are round-tripped.
Moving columns includes the header; Examples headers stay distinct from movable data rows.
Transpose requires a rectangular step DataTable and a preview; Examples cannot be transposed.

**Generate Scala Case Class…** infers `Int`, `Long`, `Boolean` or `String` across all rows, lets you
edit source/package/types and choose a destination, and checks identifiers/conflicts. Mixed/missing
values use `String`. Generation adds no runtime mapping dependency.

Header navigation/completion and unknown-header diagnostics require one explicit, resolved mapping:

```scala
case class TransferRow(account: String, amount: Int)
DataTableType { (entry: Map[String, String]) =>
  TransferRow(entry("owner"), entry("amount").toInt)
}
Given("transfers") { (rows: Seq[TransferRow]) => () }
```

Here `owner` navigates to `TransferRow.account`. Supported forms are direct constructor calls with
positional arguments and literal entry-map keys (including conversions such as `.toInt`). One
transformer must match the trailing `Seq`/`List` element type. Dynamic keys, named/reordered
constructor arguments, conflicting registrations and unmapped tables receive no inferred mapping.

The Scala lambda-signature inspection checks known Cucumber Expression types and observed capture
arity, including trailing DataTable/DocString arguments. Regex conversion types and independently
registered structured conversions are left unclassified. Outline diagnostics supplement native
checks with duplicate/empty headers and missing columns across all Examples blocks and arguments.
Optional **Project Gherkin style** inspections use the native inspection profile, severity and
suppression controls for title, tag, step-count and semantic ordering conventions.

### Preview, export and requirement links

Feature editors offer **Source / Preview / Split** using local, escaped Gherkin content. Source
links return to their PSI locations. **Export Features…** previews current/module/project selections
and tag-filtered example rows, then writes a self-contained HTML file. PDF uses the IDE's optional
bundled JCEF plugin and requires a JCEF-capable JetBrains Runtime. Cancelling preserves the existing
destination. No cloud export service is used.

Explicit requirement mappings in settings use `tag regex => relative Markdown path#id` or an HTTP(S)
URL template, for example `@REQ-(.*) => docs/requirements.md#$1`. Local IDs are explicit `{#id}`,
HTML `id` attributes or exact heading text. Duplicate IDs offer all targets; missing mapped IDs
are diagnosed. URL navigation previews the mapped URL before opening it. Ordinary tags are untouched.
**Number Scenario Titles…** previews explicit scope, start, width and prefix; it is undoable and
never runs automatically on save.

### Supported runtime and editor baseline

The pinned Cucumber Scala 8.39.7 public `ScalaDsl` combines step, hook, parameter, DataTable,
DocString and default-transformer registrations. It exposes no public API for executing embedded
Gherkin step strings. Internal backend `execute` methods are not such an API. Unrelated Scala
strings therefore receive no embedded-step injection, references or completion.

Fixture coverage includes Scala completion/insertion, exact reference ranges, undefined/ambiguous
feedback, native creation actions, scenario-to-outline/missing-Examples transformations, localized
keywords, formatting/folding and JSON/XML DocString injection. Runtime message fixtures cover
outline identity and interleaved/interrupted results. The JDI runtime probe exercises thread-specific row context with one/two Cucumber threads;
the optional JBR fixture renders a real PDF and checks cancellation. Interactive debugger lifecycle
and PDF layout/pagination remain sandbox checks; these automated checks do not establish manual results.
AI, semantic-search and cloud integrations are outside the current scope.

See the [ticket implementation map](IMPLEMENTATION.md) for scope and verification boundaries.

## Development

To setup the development environment, follow the following steps:

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. Configure JDK 25 (see https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-jdk-and-intellij-platform-plugin-sdk)
3. Open the project in IDEA

The plugin depends on:
- [Gherkin plugin](https://plugins.jetbrains.com/plugin/9164-gherkin)
- [Scala plugin](https://plugins.jetbrains.com/plugin/1347-scala)
- Optional bundled JCEF plugin (PDF rendering)
- Cucumber `tag-expressions` (runtime tag filtering)
- [cucumber-expressions](https://github.com/cucumber/cucumber-expressions) (for step snippets and built-in parameter matching)

To start an IDE with the plugin installed in the example project, run `runIDE` task. Import the whole project
as an sbt project in the sandbox-ide. Wait for indexing to finish.
See the [example guide](example/README.md) for feature files showcasing navigation, expression syntax,
custom parameter types and step generation.

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
| `suite` | Suite tool window, catalog, analysis and paired step viewer |
| `publish` | Feature preview, HTML and PDF export |
| `settings` | Project settings and their configurable |
| `util` | Shared dialogs and Gherkin file action base |

Tests are grouped by user-visible behavior under `cucumber-scala/src/test/scala`.

In order to build the plugin (and package the plugin ZIP), run: `sbt --batch test` followed by `sbt --batch 'cucumber-scala / packageArtifactZip'`

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
