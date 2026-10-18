# intellij-cucumber-scala

[![Build](https://img.shields.io/github/actions/workflow/status/vbmacher/intellij-cucumber-scala/build.yml?style=flat-square)](https://github.com/vbmacher/intellij-cucumber-scala/actions?query=branch%3Amaster)
[![Version](https://img.shields.io/jetbrains/plugin/v/7460-cucumber-for-scala.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/7460-cucumber-for-scala?style=flat-square)](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala)

A [plugin for IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7460-cucumber-for-scala) that
connects Gherkin feature files with step definitions written in the cucumber-scala DSL. The
[example guide](example/README.md) shows each feature on ready-made feature files.

## Features

### Step navigation

Navigate from a feature step to its Scala step definition and back. Step definitions are indexed,
including those in libraries with attached sources. Navigation is disabled while IntelliJ is
indexing and becomes available when indexing finishes.

Supported step definition names:

- Anchored and slash-delimited regular expressions, e.g. `"^I add (\\d+)$"` or `"/I add [0-9]+/"`.
- Constant expressions, e.g. `When("""I do 5 + 5, it's""" + (5+5))`.
- [Cucumber Expressions](https://cucumber.io/docs/cucumber/cucumber-expressions/) with
  [parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#parameter-types)
  (`"""I divide {int} by {int}"""`),
  [alternative text](https://cucumber.io/docs/cucumber/cucumber-expressions/#alternative-text)
  (`"""I/We divide (\d+) by (\d+)"""`),
  [optional text](https://cucumber.io/docs/cucumber/cucumber-expressions/#optional-text)
  (`"""I do some nop(s)"""`) and
  [escaping](https://cucumber.io/docs/cucumber/cucumber-expressions/#escaping)
  (`"""I have 42 \{int} cucumbers in my belly \(amazing!)"""`).
- [Custom parameter types](https://cucumber.io/docs/cucumber/cucumber-expressions/#custom-parameter-types)
  defined with `ParameterType(name, regex)` in any glue class or trait in the step search scope.
  Their names are navigable.

### Step creation

The native create-step action fills a template wizard. Generated definitions include trailing
`String` arguments for data tables and doc strings. Wildcard (`*`) steps use the preceding `Given`,
`When` or `Then` in the same scenario or background, or `Given` when none precedes them. Existing
destination types need a body (`{ ... }`) for insertion.

**Create Missing Scala Steps…** previews and deduplicates unresolved steps from the current feature
or selection before inserting them into an existing writable Scala type. Localized feature keywords
generate the corresponding Scala `Given`, `When` or `Then`; custom parameter return types are
inferred from the transformer. Conflicting choices require an explicit selection.

### Authoring

- **Completion** retains native matching, shows source/module/tags and distinct matching-scenario
  counts, and optionally ranks by reuse and context. Feature-only wording is labelled undefined.
  Parameterized completion starts an editable live template with runtime-valid example values.
  Complex custom expressions without a usable example ask for matching wording. Scala expression
  strings complete built-in and indexed custom parameter names after `{`.
- **Quick Documentation** shows exact candidates, captures, trailing arguments, deprecation
  metadata, usage counts and a navigable scenario preview. Ambiguous matches remain ambiguous.
- **Rename** previews changes to simple literal Cucumber Expressions and their feature usages,
  preserving arguments and keywords. It rejects invalid expressions and conflicting wording.
  Dynamic, regex, optional/alternative and ambiguous cases require manual review.
- **Use existing Scala step wording…** offers bounded nearby wording suggestions for undefined
  steps, preserves captures and previews the replacement. It never changes exact resolution.
- **Go to Scala Hooks…** lists indexed hooks with runtime tag-expression filtering. Dynamic
  conditions are shown as unknown rather than silently excluded.

### Suite tool window

Open **Tools → Scala Cucumber Suite** and select project contents or a module.

- **Suite:** search expressions or scenarios, filter by step kind and group by module, tag or
  configured category. Navigate to or preview sources, insert editable step wording and run
  selected scenarios from one module. Scenario filters accept Cucumber tag expressions. Category
  mappings in **Settings → Scala Cucumber** use `category name => tag expression`, one per line.
  Health categories list undefined/ambiguous usages, scoped unused definitions, identical
  expressions and duplicate ordered scenario bodies. Counts distinguish feature files, scenarios
  (outlines once), example rows, step usages and definitions. Background usages expand to their
  applicable scenarios; ambiguous matches count for each candidate. Static counts do not measure
  execution or business coverage, and external consumers of an apparently unused definition are
  unknown. Refresh is cancellable; cancelled results are labelled incomplete.
- **Run History:** see [Run, debug and results](#run-debug-and-results).
- **Implementation:** optionally follows the feature caret, lets you select exact Scala candidates
  and pin the view. Following is off initially and does not move the feature caret.
- **Scenario Search:** searches scenario titles and bodies in the selected scope. **Exact text**
  is the default and needs no model. Select a result to see its snippet and use **Go to source**
  to open the scenario.

**Semantic** scenario search needs a local [Ollama](https://docs.ollama.com/api/embed) server with
an installed embedding model. Set its URL and model in **Settings → Scala Cucumber** (defaults:
`http://localhost:11434`, `embeddinggemma`). **Rebuild semantic index** sends scenario text in the
selected scope to that server; **Search** sends the query and returns up to 50 positive-similarity
results. The tab shows the provider, model and scope. Vectors are held only in memory for the
tab's lifetime; **Delete semantic index** clears them, and source edits, deletion, scope changes
and model changes require a rebuild. Requests are cancellable background work with a 30-second
timeout each. An unavailable server or model leaves exact search usable. The plugin never
downloads or starts a model. Similarity is a discovery aid, never binding or coverage evidence.

### Run, debug and results

Use native Run/Debug actions on a feature, scenario, outline or individual Examples data row.
The **Scala Cucumber** run configuration uses the owning module's test classpath and JDK, discovers
visible packages containing steps, hooks and type/transformer registrations, and exposes glue,
working directory, VM, environment and reporter overrides. Saved configurations retain scenario
selections. Missing runtime/JDK and ambiguous content roots are reported before launch. Tags are
completed from the module, validated with Cucumber `tag-expressions`, persisted and passed as
literal arguments. The preview lists executable scenarios/example rows, including inherited
Examples tags.

The native test tree uses Cucumber message identities for features, rules, outlines, rows, steps
and hooks. Failed-test reruns select scenario/example locations, so duplicate titles do not select
unrelated scenarios. Feature gutter results and Quick Documentation show run identity, status,
duration and errors. Source changes mark results stale. Editor decorations can be disabled or
cleared in settings without removing the native console results.

Feature-step and Examples-row breakpoints have their own debugger requests and preserve existing
Scala breakpoint conditions. A step needs one exact Scala definition; a row needs all its steps
resolved uniquely. Requests filter by feature URI, step line and selected example line on the
suspended runtime thread. Active steps/rows are highlighted in open feature editors and cleared on
resume/termination. The debugger adapter targets the pinned Cucumber Scala/core runtime;
unsupported runtime context is left unmatched.

The optional **Run History** tab stores workspace-local outcomes with scenario/example fingerprints,
revision, runtime/environment context, timestamps and attempts. Retention is configurable; history
can be disabled or cleared. Comparisons show initial-attempt denominators, retries and
interruptions separately. Mixed outcomes are investigation signals, not root-cause claims.
Renamed, changed or deleted scenario identities are retained separately and are not assigned to
another scenario.

### Tables

Enable cell navigation in **Settings → Scala Cucumber**. Tab/Shift-Tab move between cells (the
first cell stays first); Tab past the last cell or Enter below the last row adds an aligned row.
Enter moves down in the same column. An unescaped pipe moves to the next cell; `\|` remains
literal content. Delete clears selected cells. Typing outside tables is unaffected.

**Editor context menu → Gherkin Table** contains rectangular TSV copy/cut/paste, clear, explicit
row/column deletion, movement and transpose actions; assign shortcuts in Keymap. Paste previews
overwriting from the upper-left selected cell, expands the table as needed and preserves cells
outside the pasted rectangle. Quoted TSV preserves tabs/newlines; Gherkin escapes are
round-tripped. Moving columns includes the header; Examples headers stay distinct from movable
data rows. Transpose requires a rectangular step DataTable and a preview; Examples cannot be
transposed.

**Generate Scala Case Class…** infers `Int`, `Long`, `Boolean` or `String` across all rows (mixed
or missing values use `String`), lets you edit source/package/types and choose a destination, and
checks identifiers and conflicts. Generation adds no runtime mapping dependency.

Header navigation/completion and unknown-header diagnostics require one explicit, resolved
mapping:

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

### Inspections

- The Scala lambda-signature inspection checks known Cucumber Expression types and observed capture
  arity, including trailing DataTable/DocString arguments. Regex conversion types and independently
  registered structured conversions are left unclassified.
- Outline diagnostics supplement native checks with duplicate/empty headers and missing columns
  across all Examples blocks and arguments.
- Optional **Project Gherkin style** inspections check title, tag, step-count and semantic ordering
  conventions using the native inspection profile, severity and suppression controls.

### Preview, export and requirement links

Feature editors offer **Source / Preview / Split** using local, escaped Gherkin content; source
links return to their PSI locations. **Export Features…** previews current/module/project
selections and tag-filtered example rows, then writes a self-contained HTML file. PDF export uses
the IDE's optional bundled JCEF plugin and requires a JCEF-capable JetBrains Runtime. Cancelling
preserves the existing destination. No cloud export service is used.

Requirement mappings in settings use `tag regex => relative Markdown path#id` or an HTTP(S) URL
template, e.g. `@REQ-(.*) => docs/requirements.md#$1`. Local IDs are explicit `{#id}`, HTML `id`
attributes or exact heading text. Duplicate IDs offer all targets; missing mapped IDs are
diagnosed. URL navigation previews the mapped URL before opening it. Unmapped tags are untouched.

**Number Scenario Titles…** previews explicit scope, start, width and prefix. It is undoable and
never runs automatically on save.

### Scope

The pinned Cucumber Scala 8.39.7 public `ScalaDsl` combines step, hook, parameter, DataTable,
DocString and default-transformer registrations. It exposes no public API for executing embedded
Gherkin step strings (internal backend `execute` methods are not such an API), so ordinary Scala
strings receive no embedded-step injection, references or completion. AI generation and cloud
integrations are outside the current scope.

## Development

1. `git clone https://github.com/vbmacher/intellij-cucumber-scala.git`
2. [Configure JDK 25](https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-jdk-and-intellij-platform-plugin-sdk).
3. Open the project in IntelliJ IDEA.

The plugin depends on the [Gherkin](https://plugins.jetbrains.com/plugin/9164-gherkin) and
[Scala](https://plugins.jetbrains.com/plugin/1347-scala) plugins, the optional bundled JCEF plugin
(PDF rendering), Cucumber `tag-expressions` (runtime tag filtering) and
[cucumber-expressions](https://github.com/cucumber/cucumber-expressions) (step snippets and
built-in parameter matching). `build.sbt` defines the exact versions.

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

Tests are grouped by user-visible behavior under `cucumber-scala/src/test/scala`.

- Test: `sbt --batch test`
- Package the plugin ZIP: `sbt --batch 'cucumber-scala / packageArtifactZip'`
- Start a sandbox IDE with the plugin: `sbt 'cucumber-scala / runIDE'`, then import the whole
  project as an sbt project and wait for indexing to finish.

Tests use the IntelliJ SDK's bundled JetBrains Runtime, including its native JCEF PDF renderer,
even when sbt is launched with a standard JDK 25. Fixtures cover editor, navigation, generation,
run-message and table behavior; the JDI runtime probe and optional PDF fixture cover debugger
context and real PDF rendering. Interactive debugger lifecycle and PDF layout/pagination remain
manual sandbox checks. See the [implementation map](IMPLEMENTATION.md) for per-ticket evidence and
verification boundaries.

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
