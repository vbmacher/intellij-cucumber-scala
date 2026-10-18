# intellij-cucumber-scala instructions

## Workflow

- Plan briefly before editing. Keep diffs minimal and preserve unrelated changes.
- Ask before large refactors or adding dependencies.
- Report what changed and how it was verified, separating automated tests, packaging and
  manual IDE checks.

## Code discovery

- Prefer codebase-memory-mcp tools; run `index_repository` first if the repository is not indexed.
  Use `search_graph` (definitions), `trace_path` (calls), `get_code_snippet` (source),
  `query_graph` (complex queries), `get_architecture` (overview) and `search_code` (text).
- Fall back to filesystem search for non-code files, string literals, errors, configuration
  values or insufficient graph results.

## Project layout and build

- `cucumber-scala/` contains the plugin, its resources and tests. Sources are grouped into
  feature packages (see `README.md#development`); shared PSI helpers live in
  `steps.ScCucumber`, shared UI helpers in `util`.
- `example/` and `example-stepdefs-lib/` are projects for manual testing.
- `build.sbt` is the source of truth for Scala, IntelliJ SDK, plugin dependencies and
  packaging. Keep plugin registrations and supported IntelliJ build ranges aligned with it.
- Use the JDK from `.github/workflows/build.yml`. Follow `.editorconfig`.

## Implementation

- Follow DRY, KISS and YAGNI. Reuse existing helpers, the step index and resolution logic
  instead of adding abstractions or duplicating scans and matching rules.
- Remove superseded APIs and update callers; no shims, aliases or fallback paths.
- Use IntelliJ, Scala and Gherkin PSI, resolution APIs and extension points. Respect
  read/write actions and PSI validity.
- Keep Cucumber expression matching, custom parameter types and generated Scala snippets
  consistent with the supported Cucumber Scala DSL.

## Tests

- Test user-visible behavior using the existing fixtures and test data in
  `cucumber-scala/src/test/`. For navigation, assert resolved targets and reference ranges;
  for step generation, assert the resulting Scala source. Cover unresolved and ambiguous cases.
- Focused: `sbt 'cucumber-scala / testOnly <suite>'`. Full (shared behavior):
  `sbt --batch test`. Packaging: `sbt 'cucumber-scala / packageArtifactZip'`.
- Manual: `sbt 'cucumber-scala / runIDE'`, import the example projects and wait for indexing.
- Documentation-only changes need only a formatting check.

## Documentation

- Write plain, concise, direct user documentation: what users can do, how to do it and what
  to expect. Use exact UI labels, concrete actions and named files; briefly explain
  unfamiliar terms. Use small examples when helpful.
- State prerequisites and limitations precisely, with their practical consequences. Keep
  separate capabilities separate (for example, argument-count versus parameter-type checks).
- Document only user-visible, user-controllable behavior, verified before describing it.
  Flag missing information instead of inventing details. Never add history, test statistics
  or coverage inventories. Do not document standard IntelliJ IDEA behavior or implementation
  details.
- Describe each feature in exactly one place; elsewhere, link to it. Keep `README.md`, the
  example guide and these guides aligned with behavior changes.

User guides live in `docs/`, with images in `docs/images/`:

| File | Contents |
| --- | --- |
| `getting_started.md` | Brief overview: every feature with a link to its detailed section; may include screenshots. |
| `installation.md` | The only place for installation and setup: IDE builds, required plugins, project prerequisites, optional semantic search and the development environment. |
| `navigation.md` | Plugin navigation features with examples and screenshots. |
| `editing.md` | Feature and step editing: completion, Quick Documentation, generation, rename, numbering, tables, inspections, preview and export. |
| `workspace.md` | The **Scala Cucumber** tool window and its tabs. |
| `running.md` | Run/Debug, feature breakpoints and editor results. |
| `configuration.md` | The only place describing settings UI, `.idea/scala-cucumber.xml`, inspection options and run-configuration fields. Other guides link here. |
| `icons.md` | Table of all icons (Icon, Meaning, Rule) with exact places and conditions. Icons must be idiomatic. |

Add a new guide only for a user-facing category that fits none of these; otherwise extend
the closest existing guide. Example-project instructions, such as running the examples
from a terminal, belong in `example/README.md`.
