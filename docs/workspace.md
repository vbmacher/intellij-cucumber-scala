# Scala Cucumber workspace

Open **Tools → Scala Cucumber Suite** to show the **Scala Cucumber** tool window.
Its four tabs cover browsing, observed run outcomes, step implementations and scenario search.

## Suite

The tab browses the whole project. **View** starts on **Scenarios**; each view
explains what its results mean and what to do next.

| View | What it shows |
| --- | --- |
| **Scenarios** | Scenarios; select one to see its steps, including undefined or ambiguous bindings |
| **Step definitions** | Scala expressions; select one to see matching scenarios, with outlines counted once |
| **Undefined steps** | Feature steps with no matching Scala definition |
| **Ambiguous steps** | Feature steps matching several Scala definitions; select one to see its competing definitions |
| **Unused definitions** | Definitions with no matching feature step in this project |
| **Repeated expressions** | Definitions declaring the same expression; select one to compare other declarations |
| **Duplicate scenarios** | Scenarios with the same ordered step bodies |

Results show a file path and line number beneath each entry. **Filter** searches
names, expressions, module names and file paths. In **Step definitions**, use **Step kind** for
**Given**, **When** or **Then** definitions. In **Scenarios**, **Tags** accepts an
expression such as `@smoke and not @ignored`.

Use the **Group by** checkboxes independently or together. Selected groups nest
in the order **Module → Tag → Category**; clear all three for a flat list.

- **Module** groups by sbt project, combining its `main` and `test` source sets.
  Other builds use their IDE modules. If a group says **Unresolved sbt project**,
  reload the sbt project and click **Refresh**.
- **Tag** puts each scenario or feature step under its tags, with **Untagged**
  for entries without tags.
- **Category** uses [category mappings](configuration.md#scenario-categories),
  with **Uncategorized** for entries that match none.

Tag and category groups are available for scenarios and feature steps. Expand or
collapse a group to browse its entries. Counts show distinct entries within each
group; an entry can appear in several tag or category groups.

An ambiguous step needs a unique binding: compare the competing definitions and
change the expressions or feature wording so only one matches. The competing
expressions can differ; **Repeated expressions** finds declarations with identical
expression text.

Select an entry and use:

- **Source** to open its definition or feature location.
- **Preview** to inspect its source in a dialog.
- **Insert** to insert a selected definition into the active feature editor,
  with editable sample arguments.
- **Run** to run selected scenarios or groups from one runnable module. You can
  select several entries or groups; overlapping groups run each scenario once.

The related-source list shows matching scenarios, competing definitions or other
duplicates for the selected entry. Select a related source and use **Open related
source**. Enter or a double-click opens a source entry in either the tree or the
related-source list.

Edits to feature and Scala files refresh the view automatically; the current results
and selection stay visible until the update finishes. **Refresh** scans the project
again. **Cancel** stops a pending scan; if it was an automatic update, the previous
results stay but may be outdated.
Findings describe the available project source. An unused library step may
have consumers outside the project, and matching or duplicate wording does not establish
execution coverage or business equivalence.

## Run History

The tab shows retained outcomes from Scala Cucumber runs and updates automatically
as scenarios finish. When recording is off, click **Enable History** to retain future
runs. **History Settings** opens the
[history settings](configuration.md#run-results-and-history); a limit of 0 retains
no entries.

The table shows each scenario's latest outcome, attempt counts, last run time,
source revision and runtime environment. Select a row and click **Attempts** for
details or **Open Scenario** to open its source. **Refresh** rechecks the source
links; **Clear History** removes the saved entries.

Results are grouped by scenario, source revision and runtime environment so that
different contexts remain separate. Details include the run time, outcome, attempt,
retry and error. Initial attempts and retries are counted separately. Mixed passing
and failing outcomes describe what happened; they do not identify the cause.
Renamed, changed or deleted scenarios do not transfer their old outcomes to a new
scenario at the same location.

## Step Implementation

Place the caret on a step in a `.feature` file. The tab automatically shows the
selected step and its matching Scala definition in a read-only preview while focus
stays in the feature editor.

The status distinguishes a single match, an undefined step and an ambiguous step.
For an ambiguous step, choose a **Definition** to compare the matching sources.
**Pin preview** keeps the current step and definition visible while you move
elsewhere; uncheck it to follow the current feature caret again. **Open definition**
opens the selected Scala source. These controls are available when a definition matches.

## Scenario Search

### Exact text

1. Choose **Scope**: **Project contents** or a module.
2. Leave **Match** as **Exact text**.
3. Enter words from a scenario title or body and click **Search**, or press Enter
   in the query field. An empty query lists the scenarios in that scope.
4. Select a result to reveal its preview. Use **Go to source**, Enter in the result
   list, or a double-click to open the feature location.

Each result shows the scenario title and its file and line. Exact text search needs
no model or semantic index.

![Scenario Search with results and a selected scenario preview](images/scenario-search.png)

### Semantic search

Semantic search finds related wording using an embedding model. First complete the
[optional model setup](installation.md#prepare-optional-semantic-search) and
[provider configuration](configuration.md#scenario-semantic-search).

1. Choose the scope and set **Match** to **Semantic**. The **Semantic index** controls expand.
2. Enter a query and click **Search**. If needed, the index builds automatically by
   sending the scope's scenario text to the configured server. The query is sent
   to the same server for comparison.
3. Inspect the returned scenarios and their similarity scores before opening the source.

In a feature editor, right-click a feature title, scenario title or step expression
and choose **Find similar...**. This opens **Scenario Search** in **Semantic** mode,
fills the query with the title or step text and starts the search. Selected text
is used when present.

![Semantic search and index controls](images/semantic-search.png)

The search returns up to 50 related scenarios. Similarity suggests wording to inspect;
it does not prove step binding or coverage. The index is not saved to disk. Feature
edits or deletions, a scope change, or a provider/model change invalidate it; it rebuilds
automatically on the next semantic search. **Rebuild index** forces a rebuild;
**Delete index** removes it from memory. **Cancel** stops a pending
search or rebuild. If the model server is unavailable, **Exact text** remains usable.

