# Installation

## IDE and plugin requirements

This checkout targets **IntelliJ IDEA 2026.2**, from build **262.9437.185** through
**262.***. Choose a plugin release compatible with your IDE build.

Keep these plugins enabled in **Settings → Plugins → Installed**:

| Plugin or runtime component | Required for |
| --- | --- |
| **Scala** (`org.intellij.scala`) | Scala step definitions, navigation and generation |
| **Gherkin** (`gherkin`) | Feature-file editing and the Cucumber editor actions |
| **Java** (`com.intellij.modules.java`) | The Scala/JVM integration and native Run/Debug support |
| **JCEF**, the browser component in JetBrains Runtime | PDF export; without it, the export dialog offers HTML only |

The development build uses Scala plugin **2026.2.16** and Gherkin plugin
**262.8665.173**. These versions are declared in [build.sbt](../build.sbt).
TextMate is not required for this plugin's feature-file support.

## Install from Marketplace

1. Open **Settings → Plugins → Marketplace**.
2. Find **Cucumber for Scala** and click **Install**.
3. Restart the IDE if prompted.

For a downloaded plugin ZIP, open the Plugins page's settings menu and choose
**Install Plugin from Disk…**, then select the archive. See JetBrains'
[plugin installation guide](https://www.jetbrains.com/help/idea/managing-plugins.html).

## Prepare a Cucumber Scala project

Import the project with its build tool, assign a JDK to the module containing the
features, and wait for dependency import and indexing to finish. Navigation uses the
steps available to that module, including its dependencies. Attach library sources
when you want to open definitions supplied by a library.

The project needs the Cucumber Scala runtime; installing the IDE plugin does not add
that dependency. The repository's example uses Scala **2.13.18** and:

```scala
libraryDependencies += "io.cucumber" %% "cucumber-scala" % "8.39.7"
```

Native runs need `io.cucumber.core.cli.Main` and `io.cucumber.scala.ScalaDsl` on the
selected module's test classpath. A module with several content roots also needs an
explicit working directory in its [run configuration](configuration.md#run-configurations).

## Prepare optional semantic search

An embedding model converts text into numbers used to compare related wording.
Exact text search does not need Ollama or a model.

1. Install and start [Ollama](https://docs.ollama.com/quickstart).
   The `embeddinggemma` example below requires Ollama **0.11.10 or later**;
   older servers can list the downloaded model but fail to generate embeddings.
2. Download an embedding model, for example:

   ```sh
   ollama pull embeddinggemma
   ```

3. Set the server and model in [semantic-search configuration](configuration.md#scenario-semantic-search).
4. Follow the [Scenario Search workflow](workspace.md#semantic-search).

The plugin uses the model already available on your server; it does not download models.
See the [EmbeddingGemma model page](https://ollama.com/library/embeddinggemma) for model requirements.

## Prepare optional chat models

Use a local or remote [Ollama-compatible chat server](https://docs.ollama.com/api/chat)
with an installed chat model. For a local Ollama server, for example:

```sh
ollama pull llama3.2:3b
```

Configure [failure explanations](configuration.md#failure-explanations) or
[edge-case drafts](configuration.md#edge-case-drafts), then follow
[Explain a failure](running.md#explain-a-failure) or
[Suggest edge-case scenarios](editing.md#suggest-edge-case-scenarios).
Draft generation requires a chat model and a server supporting Ollama's
[structured JSON output](https://docs.ollama.com/capabilities/structured-outputs).
A remote server
receives the context you review and submit; use its URL and optional access token.
Local Ollama at `http://localhost:11434` needs no access token; leave the optional
token fields blank. The plugin does not download chat models.

## Development environment

Use **JDK 25**, as specified in [.github/workflows/build.yml](../.github/workflows/build.yml).
Have Git and an sbt launcher available on your path. The launcher uses sbt **1.11.7**
from [project/build.properties](../project/build.properties).
The build downloads its IntelliJ SDK, plugin dependencies and bundled JetBrains Runtime.
The first build needs network access.

```sh
git clone https://github.com/vbmacher/intellij-cucumber-scala.git
cd intellij-cucumber-scala
sbt --batch test
```

Open the repository root as an sbt project in IntelliJ IDEA. To work with the example,
launch the sandbox IDE and import the repository root there as an sbt project:

```sh
sbt 'cucumber-scala / runIDE'
```

Wait for sbt import and indexing before trying navigation or Run/Debug actions.
Use the **example** test module and its project JDK for the example feature files.

| Task | Command |
| --- | --- |
| Run a focused suite | `sbt 'cucumber-scala / testOnly <suite>'` |
| Run all suites | `sbt --batch test` |
| Build the plugin ZIP | `sbt 'cucumber-scala / packageArtifactZip'` |
| Launch the sandbox IDE | `sbt 'cucumber-scala / runIDE'` |

The ZIP is written to `cucumber-scala/target/intellij-cucumber-scala-2026.2.zip`.
PDF rendering needs the SDK's JCEF-capable JetBrains Runtime. On Linux, a display must
be available; a headless build runner can provide one with `xvfb-run -a sbt --batch test`.
The CI workflow configures its Chromium sandbox restriction for that runner only.

### Publishing credentials

Publishing uses the `IJ_PLUGIN_REPO_TOKEN` environment variable or
`~/.ij-plugin-repo-token`. Signing uses `PLUGIN_SIGN_KEY`, `PLUGIN_SIGN_CERT` and
`PLUGIN_SIGN_KEY_PWD`. Keep these credentials outside the repository. See the
[sbt-idea-plugin publishing and signing tasks](https://github.com/JetBrains/sbt-idea-plugin)
before using `publishPlugin`.
