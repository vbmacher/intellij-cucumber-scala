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

# Documentation

Write documentation in plain, consolidate, direct language focused on what users can do, how to do it, and what to expect. Use concrete actions, exact UI labels, and named files instead of vague abstractions or implementation jargon; briefly explain unfamiliar technical terms. State prerequisites and limitations precisely, including their practical consequences, and distinguish separate capabilities such as checking argument counts and checking parameter types. Keep headings aligned with the content, use small examples when helpful, and omit filler and irrelevant caveats. Verify behavior before describing it; flag missing information rather than inventing details.

Never use historical narrative and never document statistics like how many tests passed or what is tested or not.

The user documentation should have a structure/organization:

- installation.md: Documentation of how the user can install the plugin, or prepare the development environment with all the caveats. Also should include information which other plugins should be enabled in order to make sure some functionality works (e.g. TextMate). No other document should mention any installation procedure but this one.
- getting_started.md: Describes what this plugin does briefly, only list of all features along with links to other documents and paragraphs with detailed documentation. This document can include example screenshots of using the features.
- navigation.md: Describes all features in navigation. It should never explain implementation details and should not explain/document what is already considered as "standard" navigation features in IntelliJ IDEA as such. It should contain examples + screenshots.
- icons.md: description (table) of all icons in the project - Icon,Meaning,Rule - with descriptive and exact listing of places and rules when the icon appears and its meaning. The icons must be idiomatic.
- workspace.md: Describes the "Scala Cucumber" tool window. 
- configuration.md: Describes plugin configuration file and settings UI. No other document should describe them, but if applicable, other documents should only reference configuration.md file.
- others - if there are more "categories" to be documented which do not fall into previous ones above. Need to be consolidated. Every documented feature is from user perspective - and should be documented ONLY if it's visible and controllable by the user.
