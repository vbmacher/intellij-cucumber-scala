package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.execution.{DefaultExecutionResult, ExecutionException, ExecutionResult, Executor}
import com.intellij.execution.configurations._
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.ui.TextFieldWithAutoCompletion
import io.cucumber.tagexpressions.TagExpressionParser
import scala.util.Try
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
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
  def line: Int = Option(lineMarker).filter(_.isValid).flatMap { marker =>
    Option(pointer).flatMap(p => Option(p.getFile)).flatMap(f => Option(FileDocumentManager.getInstance().getDocument(f)))
      .map(_.getLineNumber(marker.getStartOffset) + 1)
  }.getOrElse(savedLine)
  def setLine(value: Int): Unit = {
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
    new ScCucumberExtension().loadStepsFor(module).asScala.flatMap(d => Option(d.getElement)).map(_.getContainingFile)
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
    override def createJavaParameters(): JavaParameters = ScCucumber.inReadAction { parameters() }
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
  }
  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    def value(key: String) = Option(element.getAttributeValue(key)).getOrElse("")
    savedLine = value("line").toIntOption.getOrElse(0)
    featureUrl = value("feature")
    glue = value("glue"); tags = value("tags"); vmOptions = value("vmOptions"); programOptions = value("programOptions"); workingDirectory = value("workingDirectory")
    environment = element.getChildren("env").asScala.map(e => e.getAttributeValue("name") -> e.getAttributeValue("value")).toMap
  }
}

class ScCucumberRunEditor(project: Project) extends SettingsEditor[ScCucumberRunConfiguration] {
  private val modules = ModuleManager.getInstance(project).getModules.sortBy(_.getName)
  private val module = new JComboBox[String](modules.map(_.getName))
  private val tagField = TextFieldWithAutoCompletion.create(project, java.util.Collections.emptyList[String](), true, "")
  private val preview = new JButton("Preview Selected Scenarios / Examples")
  private def updateTags(): Unit = {
    val selected = modules.find(_.getName == module.getSelectedItem)
    selected.foreach { m =>
      com.intellij.openapi.application.ReadAction.nonBlocking[java.util.Collection[String]](() =>
        ScenarioSelection.locations(project, m.getModuleWithDependenciesAndLibrariesScope(true), "").toOption.toSeq.flatten.flatMap(_.tags).distinct.sorted.asJava)
        .inSmartMode(project).expireWith(project).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), values => tagField.setVariants(values))
        .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService)
    }
  }
  module.addActionListener(_ => updateTags())
  preview.addActionListener(_ => modules.find(_.getName == module.getSelectedItem).foreach { m =>
    val expression = tagField.getText
    com.intellij.openapi.application.ReadAction.nonBlocking[Either[String, Seq[String]]](() =>
      ScenarioSelection.locations(project, m.getModuleWithDependenciesAndLibrariesScope(true), expression).map(_.map(_.toString)))
      .inSmartMode(project).expireWith(project).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), result => {
        result match {
          case Left(message) => com.intellij.openapi.ui.Messages.showErrorDialog(project, message, "Tag Expression")
          case Right(locations) => EditPreview.confirm(project, s"${locations.size} Executable Scenarios / Example Rows", if (locations.isEmpty) "Empty selection" else locations.mkString("\n"))
        }
      }).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService)
  })
  private val fields: Seq[(String, javax.swing.text.JTextComponent)] = Seq("Feature URL", "Line (0 = whole feature)", "Glue packages (comma separated; blank = runtime default)", "Tag expression", "VM options", "Runtime/reporter options", "Working directory (blank = module root)", "Environment (NAME=value per line)").zipWithIndex.map { case (label, index) => label -> (if (index == 7) new JTextArea(3, 30) else new JTextField()) }
  override def createEditor(): JComponent = {
    val panel = new JPanel(new java.awt.GridLayout(0, 2))
    panel.add(new JLabel("Module")); panel.add(module)
    fields.zipWithIndex.foreach { case ((label, field), i) => panel.add(new JLabel(label)); panel.add(if (i == 3) tagField else field) }
    panel.add(preview)
    updateTags()
    panel
  }
  override def resetEditorFrom(configuration: ScCucumberRunConfiguration): Unit = {
    module.setSelectedItem(Option(configuration.getConfigurationModule.getModule).map(_.getName).orNull)
    val values = Seq(configuration.featureUrl, configuration.line.toString, configuration.glue, configuration.tags, configuration.vmOptions, configuration.programOptions, configuration.workingDirectory,
      configuration.environment.toSeq.sorted.map { case (k, v) => k + "=" + v }.mkString("\n"))
    tagField.setText(configuration.tags)
    fields.zip(values).foreach { case ((_, field), value) => field.setText(value) }
  }
  override def applyEditorTo(configuration: ScCucumberRunConfiguration): Unit = {
    configuration.setModule(modules.find(_.getName == module.getSelectedItem).orNull)
    configuration.featureUrl = fields(0)._2.getText.trim
    configuration.setLine(fields(1)._2.getText.toIntOption.getOrElse(0))
    configuration.glue = fields(2)._2.getText
    configuration.tags = tagField.getText
    configuration.vmOptions = fields(4)._2.getText
    configuration.programOptions = fields(5)._2.getText
    configuration.workingDirectory = fields(6)._2.getText
    configuration.environment = fields(7)._2.getText.split("\n").filter(_.contains("=")).map { line => val at = line.indexOf('='); line.substring(0, at) -> line.substring(at + 1) }.toMap
  }
}
