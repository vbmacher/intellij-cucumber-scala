# Feature preview and export

## Preview a feature

Open a `.feature` file and switch between **Source**, **Preview** and **Split** in
the editor. The preview presents the feature description, rules, scenarios, steps,
DocStrings and tables as a document. It refreshes after source edits and follows the
IDE's light or dark theme. Click a preview source link to return to its feature location.

![Compact feature document preview](images/feature-preview.png)

## Export HTML or PDF

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

