package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScCucumber, ScCucumberExtension, ScCucumberStepIndex}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.execution.{DefaultExecutionResult, ExecutionException, ExecutionResult, Executor}
import com.intellij.execution.configurations._
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.ui.{JBIntSpinner, TextFieldWithAutoCompletion, TitledSeparator}
import com.intellij.ui.components.{JBLabel, JBScrollPane, JBTextField}
import com.intellij.util.ui.{FormBuilder, JBUI, UIUtil}
import io.cucumber.tagexpressions.TagExpressionParser
import scala.util.Try
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.pointers.{VirtualFilePointer, VirtualFilePointerManager}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.editor.RangeMarker
import org.jdom.Element

import java.util
import javax.swing._
import scala.jdk.CollectionConverters._

class ScCucumberConfigurationType extends ConfigurationType {
  private val factory = new ConfigurationFactory(this) {
    override def getId: String = "ScalaCucumber"
    override def createTemplateConfiguration(project: Project): RunConfiguration = new ScCucumberRunConfiguration(project, this, "Scala Cucumber")
  }
  override def getDisplayName: String = "Scala Cucumber"
  override def getConfigurationTypeDescription: String = "Run Scala Cucumber features, scenarios and example rows"
  override def getIcon: javax.swing.Icon = icons.CucumberIcons.Cucumber
  override def getId: String = "ScalaCucumber"
  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(factory)
}

class ScCucumberRunConfiguration(project: Project, factory: ConfigurationFactory, name: String)
  extends ModuleBasedConfiguration[JavaRunConfigurationModule, Element](name, new JavaRunConfigurationModule(project, true), factory) {
  private var pointer: VirtualFilePointer = _
  private var lineMarker: RangeMarker = _
  private var savedLine = 0
  var glue: String = ""
  var tags: String = ""
  var vmOptions: String = ""
  var programOptions: String = ""
  var workingDirectory: String = ""
  var environment: Map[String, String] = Map.empty
  var selectedLocations: Seq[String] = Seq.empty

  def featureUrl: String = Option(pointer).map(_.getUrl).getOrElse("")
  def featureUrl_=(url: String): Unit = {
    pointer = if (url.isEmpty) null else VirtualFilePointerManager.getInstance().create(url, project, null)
    setLine(savedLine)
  }
  def line: Int = ScCucumber.inReadAction {
    Option(lineMarker).filter(_.isValid).flatMap { marker =>
      Option(pointer).flatMap(p => Option(p.getFile)).flatMap(f => Option(FileDocumentManager.getInstance().getDocument(f)))
        .map(_.getLineNumber(marker.getStartOffset) + 1)
    }.getOrElse(savedLine)
  }
  def setLine(value: Int): Unit = ScCucumber.inReadAction {
    savedLine = value
    Option(lineMarker).foreach(_.dispose())
    lineMarker = null
    if (value > 0) for {
      file <- Option(pointer).flatMap(p => Option(p.getFile))
      document <- Option(FileDocumentManager.getInstance().getDocument(file))
      if value <= document.getLineCount
    } lineMarker = document.createRangeMarker(document.getLineStartOffset(value - 1), document.getLineEndOffset(value - 1))
  }

  override def getValidModules: util.Collection[Module] = ModuleManager.getInstance(project).getModules.toSeq.asJava
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScCucumberRunEditor(project)
  override def suggestedName(): String = Option(pointer).flatMap(p => Option(p.getFile)).map(_.getName + (if (line > 0) ":" + line else "")).orNull

  override def checkConfiguration(): Unit = {
    val module = getConfigurationModule.getModule
    if (module == null) throw new RuntimeConfigurationError("Select the module that owns the feature and its test runtime.")
    if (ModuleRootManager.getInstance(module).getSdk == null) throw new RuntimeConfigurationError("Configure a JDK for module " + module.getName)
    if (pointer == null || !pointer.isValid) throw new RuntimeConfigurationError("Choose an existing .feature file.")
    if (getConfigurationModule.findClass("io.cucumber.core.cli.Main") == null || getConfigurationModule.findClass("io.cucumber.scala.ScalaDsl") == null)
      throw new RuntimeConfigurationError("Add the supported cucumber-scala runtime to this module's test dependencies and reimport the project.")
    Try(TagExpressionParser.parse(tags)).failed.foreach(e => throw new RuntimeConfigurationError(e.getMessage))
    if (ModuleRootManager.getInstance(module).getContentRoots.length > 1 && workingDirectory.isEmpty)
      throw new RuntimeConfigurationError("This module has several content roots. Choose the working directory explicitly.")
    if (line > 0 && (lineMarker == null || !lineMarker.isValid)) throw new RuntimeConfigurationError("The selected scenario/example location no longer exists. Select it again in the feature editor.")
  }

  def parameters(): JavaParameters = {
    val module = getConfigurationModule.getModule
    if (module == null) throw new ExecutionException("Select a Scala Cucumber module.")
    val params = new JavaParameters()
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    params.setMainClass("io.cucumber.core.cli.Main")
    val roots = ModuleRootManager.getInstance(module).getContentRoots
    params.setWorkingDirectory(if (workingDirectory.nonEmpty) workingDirectory else roots.headOption.map(_.getPath).getOrElse(project.getBasePath))
    params.getVMParametersList.addParametersString(vmOptions)
    params.getEnv.putAll(environment.asJava)
    params.getProgramParametersList.addAll(programArguments.asJava)

    params
  }

  private def detectedGlue: Seq[String] = Option(getConfigurationModule.getModule).toSeq.flatMap { module =>
    val steps = new ScCucumberExtension().loadStepsFor(module).asScala.flatMap(d => Option(d.getElement)).map(_.getContainingFile)
    val registrations = Seq(ScCucumberStepIndex.Parameters, ScCucumberStepIndex.Hooks, ScCucumberStepIndex.Tables, ScCucumberStepIndex.Transformers)
      .flatMap(kind => indexedCalls(project, stepSearchScope(module), kind)).filter(_.isFromCucumber).map(_.getContainingFile)
    (steps ++ registrations)
      .collect { case file: org.jetbrains.plugins.scala.lang.psi.api.ScalaFile => file.getPackageName }.distinct.sorted
  }

  def programArguments: Seq[String] = {
    val arguments = new ParametersList()
    arguments.addParametersString(programOptions)
    arguments.add("--plugin", "pretty")
    val packages = if (glue.nonEmpty) glue.split("[,\n]").map(_.trim).filter(_.nonEmpty).toSeq else detectedGlue
    packages.foreach(pkg => arguments.add("--glue", pkg))
    if (tags.nonEmpty) arguments.add("--tags", tags)
    val file = Option(pointer).flatMap(p => Option(p.getFile)).getOrElse(throw new ExecutionException("Feature file is missing."))
    if (selectedLocations.nonEmpty) selectedLocations.foreach(arguments.add)
    else arguments.add(file.getPath + (if (line > 0) ":" + line else ""))
    arguments.getList.asScala.toSeq
  }

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = new JavaCommandLineState(environment) {
    private lazy val messageFile = java.nio.file.Files.createTempFile("scala-cucumber-results-", ".ndjson")
    private val runId = java.util.UUID.randomUUID().toString
    override def createJavaParameters(): JavaParameters = {
      val result = ScCucumber.inReadAction { parameters() }
      result.getProgramParametersList.add("--plugin", "message:" + messageFile)
      result
    }
    override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
      val module = getConfigurationModule.getModule
      val stamp = com.intellij.psi.util.PsiModificationTracker.getInstance(project).getModificationCount
      val started = System.currentTimeMillis()
      // Scanning all features and hashing sources is slow; results recorded during the run wait for it in the background.
      val context = com.intellij.openapi.application.ReadAction.nonBlocking[RunResults.Context](() => {
        val locations = Scenarios.locations(project, module.getModuleWithDependenciesAndLibrariesScope(true), "").toOption.toSeq.flatten
        val identities = locations.map(location => RunResults.key(RunResults.fileUri(location.target.getContainingFile.getVirtualFile.getUrl), location.line) -> RunResults.identity(location)).toMap
        val scalaSources = com.intellij.psi.search.FileTypeIndex.getFiles(org.jetbrains.plugins.scala.ScalaFileType.INSTANCE, ScCucumber.stepSearchScope(module)).asScala.toSeq
          .filter(file => !com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).isInLibrarySource(file)).sortBy(_.getPath)
          .flatMap(file => Option(com.intellij.psi.PsiManager.getInstance(project).findFile(file)).map(psi => file.getUrl + "\n" + psi.getText))
        val environmentKey = module.getName + " · " + Option(ModuleRootManager.getInstance(module).getSdk).map(_.getVersionString).getOrElse("unknown JDK") +
          " · config " + RunResults.digest(Seq(glue, tags, vmOptions, programOptions, workingDirectory, ScCucumberRunConfiguration.this.environment.toSeq.sorted.mkString, RunResults.digest(scalaSources.mkString("\n"))).mkString("\n")).take(12)
        RunResults.Context(stamp, RunResults.revision(project), environmentKey, identities, started)
      }).inSmartMode(project).expireWith(project).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService)
      val handler = try startProcess() catch {
        case error: Throwable => context.cancel(); java.nio.file.Files.deleteIfExists(messageFile); throw error
      }
      val properties = new ScCucumberConsoleProperties(ScCucumberRunConfiguration.this, executor)
      @volatile var runtimeMetadata = "unknown runtime"
      def record(store: (RunResults, RunResults.Context) => Unit): Unit = Try(context.get()).foreach { value =>
        store(RunResults.getInstance(project), value.copy(environment = value.environment + " · " + runtimeMetadata))
      }
      val messages = new CucumberMessages(runId,
        text => handler.notifyTextAvailable(text, com.intellij.execution.process.ProcessOutputTypes.STDOUT),
        result => record((results, context) => results.record(result, context)),
        result => record((results, context) => results.record(result, context)),
        metadata => runtimeMetadata = metadata)
      // Drain final messages before the native console handles process termination and closes its test tree.
      new CucumberMessageTail(messageFile, handler, messages)
      val console = SMTestRunnerConnectionUtil.createAndAttachConsole("ScalaCucumber", handler, properties)
      new DefaultExecutionResult(console, handler)
    }
  }

  override def clone(): ScCucumberRunConfiguration = {
    val copy = super.clone().asInstanceOf[ScCucumberRunConfiguration]
    copy.pointer = null
    copy.lineMarker = null
    copy.savedLine = line
    copy.featureUrl = featureUrl
    copy
  }

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    Seq("feature" -> featureUrl, "line" -> line.toString, "glue" -> glue, "tags" -> tags, "vmOptions" -> vmOptions,
      "programOptions" -> programOptions, "workingDirectory" -> workingDirectory).foreach { case (key, value) => element.setAttribute(key, value) }
    environment.toSeq.sorted.foreach { case (key, value) => element.addContent(new Element("env").setAttribute("name", key).setAttribute("value", value)) }
    element.removeChildren("location")
    selectedLocations.foreach(selector => element.addContent(new Element("location").setAttribute("selector", selector)))
  }
  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    def value(key: String) = Option(element.getAttributeValue(key)).getOrElse("")
    savedLine = value("line").toIntOption.getOrElse(0)
    featureUrl = value("feature")
    glue = value("glue"); tags = value("tags"); vmOptions = value("vmOptions"); programOptions = value("programOptions"); workingDirectory = value("workingDirectory")
    environment = element.getChildren("env").asScala.map(e => e.getAttributeValue("name") -> e.getAttributeValue("value")).toMap
    selectedLocations = element.getChildren("location").asScala.flatMap(e => Option(e.getAttributeValue("selector"))).toVector
  }
}

class ScCucumberRunEditor(project: Project) extends SettingsEditor[ScCucumberRunConfiguration] {
  private val modules = ModuleManager.getInstance(project).getModules.sortBy(_.getName)
  private val module = new JComboBox[String](modules.map(_.getName))
  private val featurePath = new JBTextField()
  private val feature = new TextFieldWithBrowseButton(featurePath)
  private val line = new JBIntSpinner(0, 0, Int.MaxValue)
  private val glue = new JBTextField()
  private val tagField = TextFieldWithAutoCompletion.create(project, java.util.Collections.emptyList[String](), true, "")
  private val vmOptions = new JBTextField()
  private val runtimeOptions = new JBTextField()
  private val directoryPath = new JBTextField()
  private val workingDirectory = new TextFieldWithBrowseButton(directoryPath)
  private val environment = new JTextArea(3, 30)
  private val preview = new JButton("Preview Selected Scenarios / Examples")
  private def updateTags(): Unit = {
    val selected = modules.find(_.getName == module.getSelectedItem)
    selected.foreach { m =>
      com.intellij.openapi.application.ReadAction.nonBlocking[java.util.Collection[String]](() =>
        Scenarios.locations(project, m.getModuleWithDependenciesAndLibrariesScope(true), "").toOption.toSeq.flatten.flatMap(_.tags).distinct.sorted.asJava)
        .inSmartMode(project).expireWith(project).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), values => tagField.setVariants(values))
        .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService)
    }
  }
  module.addActionListener(_ => updateTags())
  preview.addActionListener(_ => modules.find(_.getName == module.getSelectedItem).foreach { m =>
    val expression = tagField.getText
    com.intellij.openapi.application.ReadAction.nonBlocking[Either[String, Seq[String]]](() =>
      Scenarios.locations(project, m.getModuleWithDependenciesAndLibrariesScope(true), expression).map(_.map(_.toString)))
      .inSmartMode(project).expireWith(project).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), result => {
        result match {
          case Left(message) => com.intellij.openapi.ui.Messages.showErrorDialog(project, message, "Tag Expression")
          case Right(locations) => Dialogs.confirm(project, s"${locations.size} Executable Scenarios / Example Rows", if (locations.isEmpty) "Empty selection" else locations.mkString("\n"))
        }
      }).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService)
  })
  override def createEditor(): JComponent = {
    module.setToolTipText("Module supplying the JDK, step definitions and Cucumber Scala test dependencies.")
    feature.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile()
      .withExtensionFilter("feature").withTitle("Choose Feature File"))
    workingDirectory.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir()
      .withTitle("Choose Working Directory"))
    featurePath.getEmptyText.setText("Choose a .feature file")
    directoryPath.getEmptyText.setText("Module content root")
    workingDirectory.setToolTipText("Leave blank to use the module content root. Required for modules with several content roots.")
    glue.getEmptyText.setText("Detect automatically")
    glue.setToolTipText("Comma-separated packages containing step definitions and hooks. Blank detects Scala glue; if none is found, uses the runtime default.")
    tagField.setToolTipText("Filter scenarios by tags, for example: @smoke and not @ignored. Blank runs all tags.")
    tagField.setPlaceholder("All tags; e.g. @smoke and not @ignored")
    tagField.ensureWillComputePreferredSize()
    preview.setToolTipText("Preview scenarios and Examples rows in the module and its dependencies, filtered by tags.")
    vmOptions.getEmptyText.setText("e.g. -Xmx2g -Dconfig.resource=test.conf")
    runtimeOptions.getEmptyText.setText("e.g. --threads 2 --plugin json:results.json")
    runtimeOptions.setToolTipText("Additional Cucumber command-line options, including reporters.")
    environment.setToolTipText("One NAME=value per line, for example: API_URL=http://localhost:8080")
    environment.setBorder(JBUI.Borders.empty(4))
    Seq("Feature file" -> feature.getTextField, "Line" -> line, "Glue packages" -> glue,
      "Tag expression" -> tagField, "VM options" -> vmOptions, "Cucumber options" -> runtimeOptions,
      "Working directory" -> workingDirectory.getTextField, "Environment variables" -> environment)
      .foreach { case (name, component) => component.getAccessibleContext.setAccessibleName(name) }
    val lineRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
    lineRow.add(line)
    val lineHint = new JBLabel("0 runs the whole feature", UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER)
    lineHint.setBorder(JBUI.Borders.emptyLeft(8))
    lineRow.add(lineHint)
    line.setToolTipText("Scenario or Examples-row line number; 0 runs the whole feature.")
    val moduleRow = new JPanel(new java.awt.BorderLayout())
    moduleRow.add(module, java.awt.BorderLayout.CENTER)
    val form = FormBuilder.createFormBuilder()
      .addComponent(new TitledSeparator("Selection"), 0)
      .addLabeledComponent("Module:", moduleRow)
      .addLabeledComponent("Feature file:", feature)
      .addLabeledComponent("Line:", lineRow)
      .addLabeledComponent("Tag expression:", tagField)
      .addComponentToRightColumn(preview)
      .addComponent(new TitledSeparator("Runtime"), 16)
      .addLabeledComponent("Glue packages:", glue)
      .addLabeledComponent("VM options:", vmOptions)
      .addLabeledComponent("Cucumber options:", runtimeOptions)
      .addLabeledComponent("Working directory:", workingDirectory)
      .addLabeledComponent("Environment variables:", new JBScrollPane(environment))
      .addTooltip("One NAME=value per line.")
      .getPanel
    form.setBorder(JBUI.Borders.empty(8, 12))
    val panel = new JPanel(new java.awt.BorderLayout())
    panel.add(form, java.awt.BorderLayout.NORTH)
    updateTags()
    panel
  }
  override def resetEditorFrom(configuration: ScCucumberRunConfiguration): Unit = {
    module.setSelectedItem(Option(configuration.getConfigurationModule.getModule).map(_.getName).orNull)
    feature.setText(if (configuration.featureUrl.startsWith("file://")) FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(configuration.featureUrl)) else configuration.featureUrl)
    line.setNumber(configuration.line)
    glue.setText(configuration.glue)
    tagField.setText(configuration.tags)
    vmOptions.setText(configuration.vmOptions)
    runtimeOptions.setText(configuration.programOptions)
    workingDirectory.setText(configuration.workingDirectory)
    environment.setText(configuration.environment.toSeq.sorted.map { case (k, v) => k + "=" + v }.mkString("\n"))
  }
  override def applyEditorTo(configuration: ScCucumberRunConfiguration): Unit = {
    try line.commitEdit() catch {
      case _: java.text.ParseException => throw new com.intellij.openapi.options.ConfigurationException("Enter a non-negative line number; 0 runs the whole feature.")
    }
    configuration.setModule(modules.find(_.getName == module.getSelectedItem).orNull)
    val path = feature.getText.trim
    configuration.featureUrl = if (path.isEmpty || path.contains("://")) path else VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path))
    configuration.setLine(line.getNumber)
    configuration.glue = glue.getText
    configuration.tags = tagField.getText
    configuration.vmOptions = vmOptions.getText
    configuration.programOptions = runtimeOptions.getText
    configuration.workingDirectory = workingDirectory.getText
    configuration.environment = environment.getText.split("\n").filter(_.contains("=")).map { line => val at = line.indexOf('='); line.substring(0, at) -> line.substring(at + 1) }.toMap
  }
}
