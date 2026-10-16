package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.execution.{Executor, Location, PsiLocation}
import com.intellij.execution.configurations.{ModuleRunProfile, RunProfileState}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.{SMTestLocator, SMTestProxy, SMTRunnerConsoleProperties}
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFileManager}
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope

import java.util
import scala.jdk.CollectionConverters._

object ScCucumberConsole {
  def selectorPath(value: String): String = {
    if (value.startsWith("file:")) {
      val colon = value.lastIndexOf(':')
      if (colon > 5 && value.substring(colon + 1).toIntOption.nonEmpty)
        java.nio.file.Path.of(java.net.URI.create(value.substring(0, colon))).toString + value.substring(colon)
      else value
    } else value
  }

  val Locator: SMTestLocator = new SMTestLocator {
    override def getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): util.List[Location[_]] = {
      val at = path.lastIndexOf(':')
      if (protocol != "scala-cucumber" || at < 0) util.Collections.emptyList()
      else {
        val url = path.substring(0, at)
        val file = Option(VirtualFileManager.getInstance().findFileByUrl(url))
          .orElse(Option(LocalFileSystem.getInstance().findFileByPath(url.stripPrefix("file:"))))
        (for {
          virtualFile <- file.toSeq
          psi <- Option(PsiManager.getInstance(project).findFile(virtualFile))
          line <- path.substring(at + 1).toIntOption
          if line > 0
          document <- Option(com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psi))
          if line <= document.getLineCount
          target <- Option(psi.findElementAt(document.getLineStartOffset(line - 1)))
        } yield PsiLocation.fromPsiElement(target): Location[_]).asJava
      }
    }
  }
}

class ScCucumberConsoleProperties(configuration: ScCucumberRunConfiguration, executor: Executor)
  extends SMTRunnerConsoleProperties(configuration, "ScalaCucumber", executor) {
  setIdBasedTestTree(true)
  override def getTestLocator: SMTestLocator = ScCucumberConsole.Locator
  override def createRerunFailedTestsAction(console: ConsoleView): AbstractRerunFailedTestsAction = {
    val action = new AbstractRerunFailedTestsAction(console) {
      override def getRunProfile(environment: ExecutionEnvironment): AbstractRerunFailedTestsAction.MyRunProfile = {
        val locations = getFailedTests(configuration.getProject).asScala.collect { case proxy: SMTestProxy if proxy.isLeaf => proxy.getMetainfo }
          .filter(value => value != null && value.nonEmpty).distinct.map(ScCucumberConsole.selectorPath).toSeq
        val rerun = configuration.clone().asInstanceOf[ScCucumberRunConfiguration]
        rerun.selectedLocations = locations
        new AbstractRerunFailedTestsAction.MyRunProfile(rerun) {
          override def getModules: Array[Module] = rerun.getModules
          override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = rerun.getState(executor, environment)
        }
      }
    }
    action.init(this)
    action
  }
}
