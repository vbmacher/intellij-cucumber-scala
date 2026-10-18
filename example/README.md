# Plugin examples

The example project contains small features for exploring Cucumber for Scala.
Use the [project and sandbox setup](../docs/installation.md#development-environment)
before opening them in IntelliJ IDEA.

| Feature file | What to try |
| --- | --- |
| [basic_arithmetic.feature](src/test/resources/cucumber/examples/scalacalculator/basic_arithmetic.feature) | Navigate regex captures, outlines, constant expressions and alternative text. Find feature usages from a Scala definition. |
| [expression_syntax.feature](src/test/resources/cucumber/examples/scalacalculator/expression_syntax.feature) | Navigate optional text, quoted strings and literal braces or parentheses. |
| [custom_parameters.feature](src/test/resources/cucumber/examples/scalacalculator/custom_parameters.feature) | Navigate different unit formats to their matching custom-parameter definitions. |
| [create_step_definition.feature](src/test/resources/cucumber/examples/scalacalculator/create_step_definition.feature) | Create the undefined save step individually or with batch generation. |

The shared assertion is in
[StepDefinitionsTrait.scala](src/test/scala/StepDefinitionsTrait.scala).
Definitions supplied as a separate library live in [example-stepdefs-lib](../example-stepdefs-lib).

Use the topic guides for the corresponding workflows:

- [Navigation, custom parameters and mapped headers](../docs/navigation.md)
- [Completion, documentation and generation](../docs/writing_steps.md)
- [Table editing](../docs/tables.md)
- [Suite browsing, implementation viewing and scenario search](../docs/workspace.md)
- [Run/Debug and the example CLI command](../docs/running.md)
- [Preview and HTML/PDF export](../docs/preview_and_export.md)
- [Project settings and run configuration fields](../docs/configuration.md)

Write executable scenarios in `.feature` files and register their Scala definitions.
Gherkin text inside a Scala multiline string does not receive feature-file support.
