# Plugin examples

Open the root project as an **sbt project** in an IntelliJ sandbox with this plugin,
Scala and Gherkin enabled. Start with the feature files in
`src/test/resources/cucumber/examples/scalacalculator`.

| Feature file | What to try in IntelliJ |
| --- | --- |
| `basic_arithmetic.feature` | Go to Declaration from regex captures, scenario outlines, concatenated constants and alternative text. On the Scala step string, use the gutter icon or Find Usages to navigate back. The result assertion lives in `StepDefinitionsTrait`, which inherits `ScalaDsl` indirectly. |
| `expression_syntax.feature` | Navigate both singular/plural variants to one definition; check quoted strings and literal braces/parentheses. |
| `custom_parameters.feature` | Navigate the two unit formats to their matching definitions; inspect the `ParameterType` declarations in `CustomTypes.scala`. |
| `create_step_definition.feature` | On the undefined save step, use the Create step definition quick-fix and fill in the generated template. |

For a wildcard (`*`) step, Create step definition uses the last `Given`, `When` or
`Then` in that scenario or background, skipping `And`, `But` and other wildcards.
A leading wildcard generates `Given`.

The calculator and glue use the existing dependencies. To execute all implemented
scenarios from the repository root:

```sh
sbt --batch 'example/Test/runMain io.cucumber.core.cli.Main --glue cucumber.examples.scalacalculator --tags "not @undefined" classpath:cucumber/examples/scalacalculator'
```

The `@undefined` scenario is an editing exercise. After implementing its missing
step, remove that tag to include it in the run.

## Editor and suite workflows

- Complete a step prefix, inspect its source/module/tags/count, and accept a parameterized expression.
  Tab through its editable, quoted sample arguments. Quick Documentation previews a matching scenario.
- Use **Create Missing Scala Steps…** on a selected region of `create_step_definition.feature` and
  inspect the generated source before applying. Cancel and verify the Scala file is unchanged.
- Open **Tools → Scala Cucumber Suite**. Search expressions, filter semantic kind, navigate back to
  Scala, inspect undefined/ambiguous/unused findings, then switch to scenarios and group by tags.
- Enable **Implementation → Follow feature caret**, select a step with multiple candidates, then pin
  the selected implementation. The feature editor's caret should stay in place.
- Enable table navigation in **Settings → Scala Cucumber**. Try Tab, Shift-Tab, Enter, escaped pipes,
  Delete, rectangular TSV paste and row/column movement. Preview transpose on a step DataTable;
  the same action must reject an Examples table. Undo structural edits.
- Switch the feature editor between Source, Preview and Split. Use a preview source link, change
  source and confirm the preview refreshes. Export a tag-filtered selection as HTML or PDF.

## Run and debugger checks

The following are interactive sandbox checks; they are separate from the automated fixture suite.

1. Import the root sbt project and wait for indexing and dependency import.
2. Run and Debug `basic_arithmetic.feature` from its native gutter actions. Check the owning example
   test module, test classpath/JDK and detected glue in the **Scala Cucumber** configuration.
3. Run an individual Examples row; verify that only that row executes. Edit a tag expression and use
   **Preview Selected Scenarios / Examples**, including an expression selecting no rows.
4. Temporarily make one assertion fail. Check the feature/rule/outline/row/step result hierarchy,
   source navigation, failed-test rerun, gutter error details and stale markers after a source edit.
   Restore the assertion after the check.
5. Set a feature-step breakpoint used by several scenarios. Verify the pause is in its Scala body
   only for that feature occurrence. Set a row breakpoint and verify the selected row context.
   Exercise enable/disable/removal, a pre-existing conditional Scala breakpoint, two concurrent
   scenarios and two debug sessions. Active feature highlights must disappear on resume/termination.
6. Enable local history, run passing/failing/interrupted attempts, inspect denominators and runtime
   context, then clear/disable history. Rename a scenario and verify old outcomes are separate.
7. Run PDF export using the bundled JetBrains Runtime with JCEF enabled. Check local descriptions,
   Examples rows, escaping and pagination; cancel an overwrite and verify the existing PDF survives.

The supported Scala DSL has no public embedded-step execution API. Use normal feature files and
registered Scala steps; arbitrary multiline Scala strings intentionally remain ordinary strings.
