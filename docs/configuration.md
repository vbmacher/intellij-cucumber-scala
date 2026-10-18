# Configuration

Open **Settings → Scala Cucumber** for project settings. Changes take effect when you
click **Apply** or **OK**; the two clear buttons act immediately.

![Scala Cucumber settings](images/settings.png)

## Editor and completion

| Setting | Default | Effect |
| --- | --- | --- |
| **Prefer steps used by more scenarios** | On | Ranks completion candidates using their matching-scenario counts. Outlines count once; ambiguous matches are included. |
| **Prefer steps from the same module and with shared tags** | On | Ranks candidates using the current module and scenario tags. |
| **Suggest wording from feature files in the current module** | On | Adds marked wording suggestions even when they have no Scala definition. |
| **Navigate and extend Gherkin tables with Tab, Shift-Tab and Enter** | Off | Enables [cell navigation and keyboard clearing](editing.md#keyboard-navigation). |

Ranking changes suggestion order. It does not decide which definition binds to a
step at runtime.

## Run results and history

| Setting or action | Default | Effect |
| --- | --- | --- |
| **Show run results in feature gutters and documentation** | On | Displays the latest recorded results in the editor and Quick Documentation. |
| **Keep scenario run history in this workspace** | Off | Retains scenario outcomes locally for the [Run History tab](workspace.md#run-history). |
| **Maximum history entries:** | 200 | Limits retained entries, removing the oldest first. Accepts 0–10000; 0 retains none. Available when history is enabled. |
| **Clear Editor Results** | — | Removes the current editor result decorations immediately. |
| **Clear Run History** | — | Deletes retained history immediately. |

History is local to the IDE workspace, stored in `.idea/workspace.xml` for a
directory-based project. Clearing editor results and clearing saved history are
separate actions. Turning history off stops retention; use **Clear Run History**
when you also want to remove existing entries.

## Scenario categories

Under **Tags and requirements → Scenario categories:**, enter one mapping per line:

```text
Smoke => @smoke and not @ignored
Payments => @payments
```

The left side is the category name; the right side is a Cucumber tag expression.
The **Suite** tab uses these mappings when grouping scenarios by **Category**.
Unmatched scenarios appear as **Uncategorized**. Blank lines and invalid mappings
do not create a category.

## Requirement mappings

Under **Tags and requirements → Requirement mappings:**, map a tag regular expression
to a relative Markdown path with an anchor, or to a URL template:

```text
@REQ-(.*) => docs/requirements.md#$1
@ISSUE-(\d+) => https://tracker.example.com/issues/$1
```

The expression must match the entire tag, including `@`. The first matching mapping
wins. `$1`, `$2` and later references insert captured parts of the tag. Blank lines
and lines beginning with `#` are ignored.

For the first mapping, `@REQ-PAY-12` points to `docs/requirements.md#PAY-12`.
Local paths are resolved from the project and content roots. A local destination
requires `#id`; the ID can match a Markdown heading's text, a `{#PAY-12}` heading
anchor or an HTML `id="PAY-12"` anchor. If several anchors match, navigation offers
the candidates. A missing local destination is reported by **Missing configured requirement**.
URL navigation asks for confirmation in **Open Requirement URL** before opening a browser.

Use [requirement navigation](navigation.md#requirements) from the feature tag.

## Scenario semantic search

| Field | Default | Meaning |
| --- | --- | --- |
| **Ollama URL:** | `http://localhost:11434` | Server receiving scenario text during index rebuilding and query text during semantic searches. |
| **Embedding model:** | `embeddinggemma` | Name of an embedding model already installed in that Ollama server. |

An embedding model represents text numerically so related wording can be compared.
The plugin does not download models. Prepare the server and model using
[installation](installation.md#prepare-optional-semantic-search), then follow the
[search workflow](workspace.md#semantic-search). Requests occur when you explicitly
rebuild or perform a semantic search. Changing the server or model requires a new index.

## Project configuration file

For directory-based projects, the settings above are stored in
**`.idea/scala-cucumber.xml`**, in the **`ScalaCucumberSettings`** component.
Use the settings page for normal editing. For example, the following enables table
navigation and changes the search server:

```xml
<project version="4">
  <component name="ScalaCucumberSettings">
    <option name="tableNavigation" value="true" />
    <option name="ollamaUrl" value="http://localhost:11434" />
    <option name="embeddingModel" value="embeddinggemma" />
  </component>
</project>
```

| XML option | Settings control |
| --- | --- |
| `rankByReuse` | Prefer steps used by more scenarios |
| `rankByContext` | Prefer steps from the same module and with shared tags |
| `featureWording` | Suggest wording from feature files in the current module |
| `tableNavigation` | Navigate and extend Gherkin tables |
| `resultDecorations` | Show run results in feature gutters and documentation |
| `runHistory` | Keep scenario run history in this workspace |
| `historyRetention` | Maximum history entries |
| `tagCategories` | Scenario categories, as newline-separated mappings |
| `requirementMappings` | Requirement mappings, as newline-separated mappings |
| `ollamaUrl` | Ollama URL |
| `embeddingModel` | Embedding model |

## Inspections

Open **Settings → Editor → Inspections** to enable or disable the plugin's inspections:

| Inspection | Default | Options |
| --- | --- | --- |
| **Cucumber step lambda signature** | On | None |
| **Examples header consistency** | On | None |
| **Explicit DataTable header mapping** | On | None |
| **Missing configured requirement** | On | Uses the requirement mappings above. |
| **Project Gherkin style** | Off | Controls below. |

**Project Gherkin style** provides:

| Option | Default |
| --- | --- |
| **Require scenario titles** | On |
| **Report duplicate tags** | On |
| **Maximum steps per scenario** | 12; accepts 1–10000 |
| **Require Given / When / Then order** | Off |
| **Tag convention (regular expression; blank disables)** | Blank |

These options belong to the selected IDEA inspection profile, not to
`scala-cucumber.xml`. See [inspection behavior](editing.md#inspections) for the
distinction between argument-count and parameter-type checks.

## Run configurations

Open **Run → Edit Configurations…** and select a **Scala Cucumber** configuration.
Gutter Run/Debug actions create one for the selected feature, scenario or Examples row.

| Field | Meaning |
| --- | --- |
| **Module** | Module supplying the JDK and test runtime classpath. |
| **Feature URL** | Feature-file location to run. |
| **Line (0 = whole feature)** | Scenario or Examples-row selection; 0 selects the whole feature. |
| **Glue packages (comma separated; blank = runtime default)** | Packages containing step definitions and hooks. When blank, the plugin derives packages from the module's available Scala glue; if none can be found, Cucumber uses its default. |
| **Tag expression** | Cucumber tag filter, for example `@smoke and not @ignored`. |
| **VM options** | JVM arguments for the launched process. |
| **Runtime/reporter options** | Additional Cucumber CLI options, such as `--threads 2`. |
| **Working directory (blank = module root)** | Process working directory. A module with multiple content roots requires an explicit directory. |
| **Environment (NAME=value per line)** | Environment variables for the process. |

**Preview Selected Scenarios / Examples** lists the executable scenarios or Examples
rows selected by the module, location and tag filter before a run. An empty selection
is shown explicitly. Runtime prerequisites are listed in
[installation](installation.md#prepare-a-cucumber-scala-project).

