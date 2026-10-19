# Plugin examples

The example project contains small features for exploring Cucumber for Scala.
Use the [project and sandbox setup](../docs/installation.md#development-environment)
before opening them in IntelliJ IDEA.

| Feature file                                                                                                          | What to try                                                                                                                |
|-----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [basic_arithmetic.feature](src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature)             | Navigate regex captures, outlines, constant expressions and alternative text. Find feature usages from a Scala definition. |
| [expression_syntax.feature](src/test/resources/cucumber/examples/scalacalculator/expression_syntax.feature)           | Navigate optional text, quoted strings and literal braces or parentheses.                                                  |
| [custom_parameters.feature](src/test/resources/cucumber/examples/scalacalculator/custom_parameters.feature)           | Navigate different unit formats to their matching custom-parameter definitions.                                            |
| [create_step_definition.feature](src/test/resources/cucumber/examples/scalacalculator/create_step_definition.feature) | Create the undefined save step individually or with batch generation.                                                      |

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

## Run the examples from a terminal

From the repository root, run the implemented calculator scenarios:

```sh
sbt --batch 'example/Test/runMain io.cucumber.core.cli.Main --glue cucumber.examples.scalacalculator --tags "not @undefined" classpath:cucumber/examples/scalacalculator'
```

The `@undefined` scenario is an editing exercise. After implementing its missing
step, remove that tag to include it in the run.
