# Icons

The plugin uses IntelliJ IDEA and Gherkin icons for their usual meanings. The table
shows the light-theme assets from the plugin and its IntelliJ/Gherkin dependencies; their appearance can change with the IDE theme.

| Icon | Meaning | Rule |
| --- | --- | --- |
| <img src="images/icons/plugin.svg" width="24" height="24" alt="Plugin logo"> | Cucumber for Scala | Identifies this plugin in IntelliJ IDEA's Plugins page. |
| <img src="images/icons/cucumber.svg" width="16" height="16" alt="Cucumber"> | Matching scenarios | In the Scala editor gutter beside a recognized Cucumber step-definition keyword, after indexing. Click to open matching scenarios in the module's dependency scope; outlines count once and ambiguous matches are included. The icon can also offer an empty result when no scenario matches. |
| <img src="images/icons/cucumber.svg" width="16" height="16" alt="Cucumber"> | Scala Cucumber run configuration | Identifies the **Scala Cucumber** configuration type in Run/Debug configuration selectors. |
| <img src="images/icons/run.svg" width="16" height="16" alt="Run"> | Run or debug | On an Examples data row, opens actions for that row. The header row does not receive this action. Feature/scenario run gutters are supplied by Gherkin support. |
| <img src="images/icons/breakpoint.svg" width="16" height="16" alt="Breakpoint"> | Feature or Examples-row breakpoint | Marks a **Scala Cucumber Step / Example** breakpoint. It is available only where the selected step, or every step of the selected Examples row, has one exact Scala implementation. IDEA controls disabled and other breakpoint-state appearances. |
| <img src="images/icons/passed.svg" width="16" height="16" alt="Passed"> | Passed result | In a feature step or Examples data-row gutter when every recorded result at that location in the latest run is `PASSED` and is current. |
| <img src="images/icons/failed.svg" width="16" height="16" alt="Failed"> | Failed result | In the same result gutters when any result at that location in the latest run is `FAILED`, `AMBIGUOUS` or `UNDEFINED`. Click for result details. |
| <img src="images/icons/ignored.svg" width="16" height="16" alt="Ignored"> | Other outcome | In the same result gutters when no result at that location in the latest run failed, but at least one has another status, while the result is current. Read the tooltip or details for the actual status. |
| <img src="images/icons/stale.svg" width="16" height="16" alt="Warning"> | Stale result | Replaces the result icon when project source has changed since the run. Rerun to obtain a current result. |
| <img src="images/icons/refresh.svg" width="16" height="16" alt="Refresh"> | Refresh history | In **Run History**, reloads retained entries and rechecks source links. |
| <img src="images/icons/clear.svg" width="16" height="16" alt="Trash"> | Clear History | In **Run History**, deletes retained entries. Disabled when there are no entries. |
| <img src="images/icons/settings.svg" width="16" height="16" alt="Settings"> | History Settings | In **Run History**, opens **Scala Cucumber** settings. |
| <img src="images/icons/collapsed.svg" width="16" height="16" alt="Expand"> | Collapsed index options | In **Scenario Search**, on **Semantic index** when its model and index controls are hidden. |
| <img src="images/icons/expanded.svg" width="16" height="16" alt="Collapse"> | Expanded index options | On the same control when its model and index controls are visible. Selecting **Semantic** expands them. |
| <img src="images/icons/collapsed.svg" width="16" height="16" alt="Expand"> | Hidden request context | In **Suggest edge cases**, on **Context to send** when the context editor is hidden. Click to show it. |
| <img src="images/icons/expanded.svg" width="16" height="16" alt="Collapse"> | Visible request context | On **Context to send** when the context editor is shown. Click to hide it. |

Result markers require [editor result display](configuration.md#run-results-and-history).
They report observed executions, not scenario coverage. For actions and examples, see
[navigation](navigation.md), [running](running.md) and the [workspace](workspace.md).
