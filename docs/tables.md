# Gherkin tables

Use **Editor context menu → Gherkin Table** while the caret or selection is in a step
DataTable or an Examples table. A selection acts on the rectangle between its first
and last cells; with no selection, cell operations use the caret cell. Structural
operations require a rectangular table and report ragged rows instead of guessing.

## Copy, paste and clear

| Action | Result |
| --- | --- |
| **Copy Cells as TSV** | Copies the selected rectangle as tab-separated values for a spreadsheet. |
| **Cut Cells as TSV** | Copies values and clears the selected cells, preserving the grid. |
| **Paste TSV (Preview)…** | Previews pasted spreadsheet values and extends the table when needed. |
| **Clear Cells** | Removes selected values without deleting rows or columns. |

TSV means tab-separated values. Quoted values can contain tabs or newlines; pasting
escapes values for Gherkin, including literal pipes. Inspect the paste preview before
applying it.

## Move, delete and transpose

| Action | Result or restriction |
| --- | --- |
| **Move Data Row Up** / **Move Data Row Down** | Moves a data row without crossing or moving the header. |
| **Move Column Left** / **Move Column Right** | Moves the whole column, including its header. |
| **Delete Rows (Preview)…** | Previews removal of selected rows; refuses to delete the header. |
| **Delete Columns (Preview)…** | Previews column removal; refuses to remove every column. |
| **Transpose DataTable (Preview)…** | Exchanges rows and columns in a step DataTable. Not available for Examples, whose headers define outline parameters. |

Use Undo to reverse an applied edit.

## Keyboard navigation

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

## Generate a case class

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

## Mapped headers

When a `DataTableType` explicitly maps table entries to case-class constructor fields,
the feature table offers mapped header completion and navigation to those fields.
The **Explicit DataTable header mapping** inspection reports unknown headers against
that mapping. A case class with similarly named fields alone is insufficient.
See the [explicit mapping example](navigation.md#table-headers).

