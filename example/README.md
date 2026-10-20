# Plugin examples

The example project contains small features for exploring Cucumber for Scala.
Use the [project and sandbox setup](../docs/installation.md#development-environment)
before opening them in IntelliJ IDEA.

The [capability specifications](../features/README.md) include an example and
screenshot for every plugin feature.

| Feature file                                                                                                          | What to try                                                                                                                |
|-----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [basic_arithmetic.feature](src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature)             | Navigate regex captures, outlines, constant expressions and alternative text. Find feature usages from a Scala definition. |
| [expression_syntax.feature](src/test/resources/cucumber/examples/scalacalculator/expression_syntax.feature)           | Navigate optional text, quoted strings and literal braces or parentheses.                                                  |
| [custom_parameters.feature](src/test/resources/cucumber/examples/scalacalculator/custom_parameters.feature)           | Navigate different unit formats to their matching custom-parameter definitions.                                            |
| [create_step_definition.feature](src/test/resources/cucumber/examples/scalacalculator/create_step_definition.feature) | Create the undefined save step individually or with batch generation.                                                      |
| [scenario_interactions.feature](src/test/resources/cucumber/examples/scalacalculator/scenario_interactions.feature) | Compare Background usages, tagged Examples selection and outlines with identical step bodies. |

The shared assertion is in
[StepDefinitionsTrait.scala](src/test/scala/StepDefinitionsTrait.scala).
Definitions supplied as a separate library live in [example-stepdefs-lib](../example-stepdefs-lib).

Use the topic guides for the corresponding workflows:

- [Navigation, custom parameters and mapped headers](../docs/navigation.md)
- [Completion, generation, edge-case drafts, tables, preview and HTML/PDF export](../docs/editing.md)
- [Suite browsing, implementation viewing and scenario search](../docs/workspace.md)
- [Run/Debug](../docs/running.md)
- [Project settings and run configuration fields](../docs/configuration.md)

Write executable scenarios in `.feature` files and register their Scala definitions.
Gherkin text inside a Scala multiline string does not receive feature-file support.

## Explore feature combinations

Open `scenario_interactions.feature` and try these checks:

1. Navigate from the Feature Background to `I clear the operand(s)`, then inspect
   its usages. Both outlines use it, with each outline counted once. The Rule
   Background `I enter 10` applies only to the first outline.
2. In **Scala Cucumber → Suite**, select **Scenarios** and filter with
   `@interactions and @small`. The first outline appears once. Use the same filter
   in the run-configuration selection preview and **Export Features…**: only its
   small Examples row is selected. `@small and @large` selects nothing.
3. Select **Duplicate scenarios**. Both outlines appear because their own step
   bodies match; compare their Backgrounds and Examples before deciding what to change.
4. Enable history, run a row, and clear saved history. Editor observations remain.
   Run again and clear editor results: saved history remains. For a failure exercise,
   temporarily change an expected result, run that row, inspect the recorded failure,
   then undo the change. Optional failure explanations follow the
   [review workflow](../docs/running.md#explain-a-failure).

## Run the examples from a terminal

From the repository root, run the implemented calculator scenarios:

```sh
sbt --batch 'example/Test/runMain io.cucumber.core.cli.Main --glue cucumber.examples.scalacalculator --tags "not @undefined" classpath:cucumber/examples/scalacalculator'
```

The `@undefined` scenario is an editing exercise. After implementing its missing
step, remove that tag to include it in the run.
