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

The calculator and glue use the existing dependencies. To execute all implemented
scenarios from the repository root:

```sh
sbt --batch 'example/Test/runMain io.cucumber.core.cli.Main --glue cucumber.examples.scalacalculator --tags "not @undefined" classpath:cucumber/examples/scalacalculator'
```

The `@undefined` scenario is an editing exercise. After implementing its missing
step, remove that tag to include it in the run.
