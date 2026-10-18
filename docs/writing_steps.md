# Writing Scala steps

## Completion

In a feature step, invoke completion after entering part of its wording. Suggestions
show their Scala source, module, tags and matching-scenario count. Outlines count once,
and ambiguous matches are included. These are source usages, not execution counts.

Accepting a parameterized suggestion inserts sample arguments into an editable
template. Use Tab to move between arguments; quoted string arguments keep their quotes.
Samples can come from existing feature usages. Feature-only wording is marked because
it may have no Scala definition. Deprecated Scala definitions are struck through and
show their deprecation annotation.

For example, completing `When I transfer` from `When("I transfer {int} credits")`
inserts wording such as `When I transfer 20 credits`, with the number editable.
In a Scala step expression, completion inside `{...}` also offers custom parameter names.

The [completion settings](configuration.md#editor-and-completion) control ranking and
feature-only suggestions.

## Create step definitions

For one undefined feature step, invoke **Create step definition**. Choose the Scala
destination and edit the generated template.

For several missing steps, choose **Create Missing Scala Steps…** from the feature
editor's context menu. Select a region first to limit the operation; otherwise it uses
the current file. The action collects and deduplicates its missing steps. Choose any
parameter mappings offered, then choose a writable Scala file whose first type has a
body. Review the generated source before applying it. Cancelling leaves the destination
unchanged.

Generation uses the step's semantic keyword, including localized keywords. For a `*`
step it uses the last **Given**, **When** or **Then** in that scenario or background,
skipping **And**, **But** and other wildcards. A leading wildcard generates **Given**.
Custom parameter registrations can provide the generated parameter types; where
several conversions are possible, choose the intended candidate.

The [creation example](../example/src/test/resources/cucumber/examples/scalacalculator/create_step_definition.feature)
contains an undefined step to try.

## Quick Documentation

On a feature step, **Quick Documentation** shows the matching Scala expression,
source links, lambda parameters and captured values. Multiple matches are identified
as ambiguous. It includes a trailing DocString or DataTable when present; unresolved
values are labelled unknown. For outlines, the shown values represent one example
and can differ in other rows.

Documentation also shows the matching-scenario count and a scenario preview with
tags and a source link. On a Scala definition, it shows the expression and its
scenario usages. Observed run results appear when
[result display is enabled](configuration.md#run-results-and-history).

## Rename and reuse wording

Use **Rename** on a Scala step definition to change its expression and corresponding
feature usages. Review the refactoring preview; captured argument values are preserved.
Conflicting or ambiguous replacements are rejected.

For an undefined feature step, invoke **Use existing Scala step wording…** to inspect
similar defined wording. Review the proposed capture-preserving replacement before
applying it. Similar text still needs a human check that the step does what the scenario requires.

## Inspections

The plugin reports these problems in the editor. Enable and configure them through
[inspection settings](configuration.md#inspections).

| Inspection | What it checks |
| --- | --- |
| **Cucumber step lambda signature** | Argument counts, and separately the known parameter types described below. |
| **Examples header consistency** | Missing, empty and duplicate Examples headers for outline placeholders. |
| **Explicit DataTable header mapping** | Headers read by an explicit Scala table-to-case-class mapping. See [mapped headers](tables.md#mapped-headers). |
| **Missing configured requirement** | Local destinations referenced by mapped requirement tags. |
| **Project Gherkin style** | Scenario titles, duplicate tags, scenario length, step order and tag naming, according to the inspection profile. **Remove duplicate tag** removes a reported duplicate. |

### Argument counts and parameter types

Argument counts and type compatibility are separate checks. For example:

```scala
When("I transfer {int} credits") { () => () }          // one argument required
When("I transfer {int} credits") { (n: String) => () } // Int expected
```

For named Cucumber parameters, the inspection checks types it can determine from
built-in parameters and custom registrations with one known return type. Matching
feature text supplies observed capture counts; a trailing DocString or DataTable
adds one argument. Regular-expression conversions and DocString/DataTable converters
do not establish a reliable parameter type here, so those types remain unchecked.
No warning is not proof that every runtime conversion is compatible.

## Number scenario titles

Choose **Number Scenario Titles…** from a feature editor's context menu. Choose the
starting number, minimum width, prefix and scope, then review the preview. The scope
selector offers **Current file / editor selection** and **Selected feature files**.
The action changes scenario titles without changing their steps.

