# intellij-cucumber-scala instructions

## Workflow

- Start with a short plan before editing. Prefer minimal diffs and preserve unrelated changes.
- Ask before large refactors or adding dependencies.
- Run relevant tests after changes. Documentation-only changes need a formatting check.
- Explain what changed and how it was verified in concise, plain language.

## Code discovery

- Prefer codebase-memory-mcp tools over grep, glob or file searches for code discovery.
- If the repository is not indexed, run `index_repository` first.
- Use `search_graph` to find definitions, `trace_path` for call relationships,
  `get_code_snippet` for source, `query_graph` for complex queries,
  `get_architecture` for an overview and `search_code` for code text searches.
- Fall back to filesystem searches for non-code files, string literals, errors,
  configuration values or insufficient graph results.

## Project layout and build

- `cucumber-scala/` contains the IntelliJ plugin, its resources and automated tests.
- `example/` and `example-stepdefs-lib/` provide projects for manual plugin testing.
- Treat `build.sbt` as the source of truth for Scala, IntelliJ SDK, plugin dependencies
  and packaging settings. Follow `.editorconfig` for formatting.
- Use the JDK configured in `.github/workflows/build.yml` when building and testing.
- Keep plugin registrations and supported IntelliJ build ranges aligned with the build.

## Implementation

- Keep code simple and consolidated. Follow DRY, KISS and YAGNI.
- Reuse existing helpers and conventions before adding abstractions. Extract shared
  behavior only for concrete use cases within the task's scope.
- Never preserve backward compatibility for superseded interfaces. Remove obsolete
  APIs and update callers instead of adding shims, aliases or fallback paths.
- Use IntelliJ, Scala and Gherkin PSI, resolution APIs and extension points for
  navigation, step discovery and step generation.
- Reuse the existing step index and resolution helpers instead of duplicating scans
  or matching rules. Respect IntelliJ read/write actions and PSI validity.
- Keep Cucumber expression matching, custom parameter types and generated Scala
  snippets consistent with the supported Cucumber Scala DSL.

## Tests and validation

- Test expected user-visible behavior rather than mirroring implementation details.
- Reuse the existing IntelliJ test fixtures and test data under
  `cucumber-scala/src/test/`.
- For navigation changes, assert the resolved targets and reference ranges. For step
  generation, assert the resulting Scala source. Cover relevant unresolved and
  ambiguous cases as well as successful matches.
- Run focused suites with `sbt 'cucumber-scala / testOnly <suite>'`; run the complete
  suite with `sbt --batch test` when changes affect shared plugin behavior.
- Verify packaging changes with `sbt 'cucumber-scala / packageArtifactZip'`.
- For manual IDE checks, use `sbt 'cucumber-scala / runIDE'`, import the example
  projects in the sandbox IDE and wait for indexing to finish.
- Distinguish automated test results from packaging and manual IDE verification.

## Documentation

- Document current behavior, supported syntax and usage directly and concisely.
- Keep `README.md`, the example guide and relevant code documentation aligned with
  changes to navigation, parameter types and step generation.
- Explain non-obvious PSI, indexing and platform constraints near the relevant code.
- Describe editor features by their UI surface, trigger and expected result.
