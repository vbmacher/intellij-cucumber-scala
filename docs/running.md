# Run and debug

Native **Scala Cucumber** runs use the selected module's JDK and test runtime.
Prepare the module as described in [installation](installation.md#prepare-a-cucumber-scala-project).

## Run a feature, scenario or Examples row

Use the feature or scenario gutter's **Run** or **Debug** action. A scenario outline
runs its Examples rows. For one row, use the run icon beside that Examples data row;
the header has no row-run action.

The plugin creates a **Scala Cucumber** run configuration for the selected location.
For tag filters, selection preview, glue packages and runtime options, see
[run configurations](configuration.md#run-configurations).
To run several scenarios from one module, select them in the
[Suite tab](workspace.md#suite) and click **Run selection**.

The Run/Debug tool window shows features, rules, scenarios, Examples rows, steps and
their outcomes. Open a result to navigate to its feature location. Use its failed-test
rerun action to rerun failed scenarios or rows by their locations.

## Debug a feature step or Examples row

Add a breakpoint on a feature step or an Examples data row, using breakpoint type
**Scala Cucumber Step / Example**, then start **Debug** from the relevant gutter.
A step must have exactly one matching Scala implementation; for an Examples-row
breakpoint, every selected step must have one. Resolve undefined or ambiguous steps
before using these breakpoints.

The debugger pauses inside the matching Scala implementation for that feature
occurrence. A row breakpoint selects that Examples row's steps, even when the same
definition is used by other rows. The feature editor highlights the current step and
row while paused, then clears the highlight on resume or termination. Concurrent
debug sessions maintain their own highlights.

You can also use ordinary Scala breakpoints in step bodies. Their conditions and
enabled state remain independent of feature breakpoints.

## Editor results

With [result display enabled](configuration.md#run-results-and-history), feature
steps and Examples data rows show their latest recorded result in the gutter.
Hover for the status, duration, run, selected location and error; click for
**Cucumber Result Details**. Quick Documentation can also show these results.

A project source change makes recorded results stale. The warning icon means to
rerun before relying on them; it replaces the previous outcome icon. These markers
describe observed executions, not coverage. See the [icon reference](icons.md).

For comparisons across retained attempts, use [Run History](workspace.md#run-history).

## Explain a failure

After [enabling failure explanations](configuration.md#failure-explanations), run the
scenario or Examples row with **Scala Cucumber**. Place the caret on its failed step,
scenario or Examples row, then choose **Explain Cucumber Failure…** from the editor's
context menu. If several recorded runs match, choose the run and row to explain.

The review window shows the recorded status and error separately from the generated
explanation. Review or redact **Context to send**, check the displayed server and model,
then click **Explain**. Opening the window sends nothing. The context includes the
selected Gherkin, its background, static Scala matches and locally resolved error
locations. Multiple Scala matches are shown as ambiguous; source resolution does not
prove which binding executed.

Use the source selector and **Open source** to navigate to a concrete feature, Scala
match or local stack-trace candidate. **Cancel request** stops waiting for the model;
closing the window also discards its pending answer. Generated text is a suggestion
to verify against the recorded facts. The action does not change source files.

Run again after a project source change, after clearing editor results, or when the
recorded result is unavailable. Recent explanation context is kept for the IDE session;
saved run history does not restore it. Provider failures leave **Explain** available
for retry. Long context is visibly shortened before review; edited submissions are
limited to 32,000 characters. A slow model can take several minutes; shorten the
reviewed context or choose a smaller chat model if a request times out.
