# Editing features and steps

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

## Create step definitions

For one undefined feature step, invoke **Create step definition**. Choose the Scala
destination and edit the generated template.

For several missing steps, choose **Generate…** (**Alt+Insert**) → **Create Missing
Scala Steps…** in the feature editor. Select a region first to limit the operation;
otherwise it uses the current file. The action collects and deduplicates its missing
steps, then opens the same destination chooser as **Create step definition**. Choose
an existing step-definition file or **Create new file**, then choose any parameter
mappings offered. Cancelling the destination chooser or new-file dialog adds nothing.
Each definition is added to the first type in the chosen file, which must have a body.

Generation uses the step's semantic keyword, including localized keywords. For a `*`
step it uses the last **Given**, **When** or **Then** in that scenario or background,
skipping **And**, **But** and other wildcards. A leading wildcard generates **Given**.
Custom parameter registrations can provide the generated parameter types; where
several conversions are possible, choose the intended candidate.

The [creation example](../example/src/test/resources/cucumber/examples/scalacalculator/create_step_definition.feature)
contains an undefined step to try.

## Suggest edge-case scenarios

After [setup](installation.md#prepare-optional-chat-models), enable
[edge-case drafts](configuration.md#edge-case-drafts). Select specification text in a
feature file, or place the caret in a scenario, Rule or feature, then choose
**Suggest edge cases…** from the editor context menu.

Review **Context to send**, including the applicable descriptions and Backgrounds.
Remove anything you do not want to send. The window shows the destination server
and model; nothing is sent until you click **Generate drafts**. Context must fit
within 32,000 characters.

Click a draft in the list to review its Gherkin, reason and step checks alongside it.
The context collapses when drafts arrive; click **Context to send** to reopen it.
Its chevron points right when hidden and down when shown. Drag the horizontal
divider below the context editor to change its height; hiding and reopening it
preserves the chosen size within the window.
Check the drafts you want, or use arrow keys to preview and **Space** to toggle
selection. The footer shows how many you selected. Click **Insert selected** to add
them after the current scenario, or at the end of the selected feature or Rule, as one undoable
edit. Syntax errors block selection. Unresolved steps need implementations; ambiguous
steps list their matching definitions. **Step definition found** describes source
resolution. Every draft is **Not executed**: review its assumptions and expected
outcomes before running it. For outlines, step resolution uses the example
substitution used by editor navigation.

**Cancel request**, closing the window or a provider error leaves source unchanged.
If the feature or model settings change during review, reopen the action for a new
review. Step validation waits for indexing; insertion also requires indexes to be ready.

## Rename and reuse wording

Use **Rename** on a Scala step definition to change its expression and corresponding
feature usages. Review the refactoring preview; captured argument values are preserved.
Keep parameter names, types and order unchanged. Rename supports literal Cucumber
expressions; regex, optional, alternative, escaped, dynamic and concatenated patterns
require manual editing. Conflicting or ambiguous replacements are rejected.

For an undefined feature step, invoke **Use existing Scala step wording…** to inspect
similar defined wording. Review the proposed capture-preserving replacement before
applying it. Similar text still needs a human check that the step does what the scenario requires.

## Number scenario titles

Choose **Number Scenario Titles…** from a feature editor's context menu. Choose the
starting number, minimum width, prefix and scope, then review the preview. The scope
selector offers **Current file / editor selection** and **Selected feature files**.
The action changes scenario titles without changing their steps.

## Tables

Use **Editor context menu → Gherkin Table** while the caret or selection is in a step
DataTable or an Examples table. A selection acts on the rectangle between its first
and last cells; with no selection, cell operations use the caret cell. Structural
operations require a rectangular table and report ragged rows instead of guessing.
Use Undo to reverse an applied edit.

### Copy, paste and clear

| Action | Result |
| --- | --- |
| **Copy Cells as TSV** | Copies the selected rectangle as tab-separated values for a spreadsheet. |
| **Cut Cells as TSV** | Copies values and clears the selected cells, preserving the grid. |
| **Paste TSV (Preview)…** | Previews pasted spreadsheet values and extends the table when needed. |
| **Clear Cells** | Removes selected values without deleting rows or columns. |

TSV means tab-separated values. Quoted values can contain tabs or newlines; pasting
escapes values for Gherkin, including literal pipes. Inspect the paste preview before
applying it.

### Move, delete and transpose

| Action | Result or restriction |
| --- | --- |
| **Move Data Row Up** / **Move Data Row Down** | Moves a data row without crossing or moving the header. |
| **Move Column Left** / **Move Column Right** | Moves the whole column, including its header. |
| **Delete Rows (Preview)…** | Previews removal of selected rows; refuses to delete the header. |
| **Delete Columns (Preview)…** | Previews column removal; refuses to remove every column. |
| **Transpose DataTable (Preview)…** | Exchanges rows and columns in a step DataTable. Not available for Examples, whose headers define outline parameters. |

### Keyboard navigation

Enable [table keyboard navigation](configuration.md#editor-and-completion) to use these
keys in a rectangular table with one caret:

| Key | Action |
| --- | --- |
| Tab | Next cell, continuing into the next row. |
| Shift-Tab | Previous cell; stays in the table before its first cell. |
| Enter | Same column in the next row. |
| Unescaped `\|` | Advances to the next cell; an escaped pipe stays in the value. |
| Delete with a cell selection | Clears selected values while preserving the grid. |

Moving past the last row adds a row. Use the explicit delete actions above when you
want to remove rows or columns.

### Generate a case class

Choose **Generate Scala Case Class…** on a step DataTable and enter the class name.
The table needs unique, non-empty headers without backticks or newlines. The action
infers field types from all data rows, using Boolean, Int or Long when consistent
and String otherwise; a column with empty values uses String. Headers that require
escaped Scala names, including keywords, are backticked. Generation is not offered
for Examples tables.

For example:

```gherkin
| account | amount | enabled |
| primary | 20     | true    |
| reserve | 40     | false   |
```

Review and edit the proposed Scala source, then choose a writable destination folder.
The action creates a new `<class name>.scala` file. Invalid source and existing class
or filename conflicts are reported before insertion.

Header completion and navigation for tables read by an explicit `DataTableType` are
described in [table-header navigation](navigation.md#table-headers).

## Inspections

The plugin reports these problems in the editor. Enable and configure them through
[inspection settings](configuration.md#inspections).

| Inspection | What it checks |
| --- | --- |
| **Cucumber step lambda signature** | Argument counts, and separately the known parameter types described below. |
| **Examples header consistency** | Missing, empty and duplicate Examples headers for outline placeholders. |
| **Explicit DataTable header mapping** | Table headers that an explicit Scala table-to-case-class mapping does not read. See [table headers](navigation.md#table-headers). |
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

## Preview and export

### Preview a feature

Open a `.feature` file and switch between **Source**, **Preview** and **Split** in
the editor. The preview presents the feature description, rules, scenarios, steps,
DocStrings and tables as a document. It refreshes after source edits and follows the
IDE's light or dark theme. Click a preview source link to return to its feature location.

![Compact feature document preview](images/feature-preview.png)

### Export HTML or PDF

1. Open a feature file and choose **Export Features…** from the editor context menu.
2. Choose **Scope**: **Current feature**, **Owning module** or **Project contents**.
3. Optionally enter a **Tag expression**, such as `@smoke and not @ignored`.
4. Choose **Format**: **HTML** or **PDF**.
5. Review the selected executable scenarios and Examples rows, then confirm.
6. Choose the destination in the save dialog; the proposed name is `features.html`
   or `features.pdf`.

Exports combine the selected features into one document. The tag filter selects
executable scenarios and Examples rows; a selection matching nothing is reported
explicitly. HTML is self-contained, and feature text is escaped rather than treated
as markup. Export uses a light document style suitable for printing.

PDF is offered only when the IDE provides the optional browser component described
in [installation prerequisites](installation.md#ide-and-plugin-requirements).
If **PDF** is absent, export **HTML** and use a browser's print-to-PDF option.
