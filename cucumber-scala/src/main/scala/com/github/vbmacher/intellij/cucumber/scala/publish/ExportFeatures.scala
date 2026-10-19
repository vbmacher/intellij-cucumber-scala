package com.github.vbmacher.intellij.cucumber.scala.publish

import com.github.vbmacher.intellij.cucumber.scala.run.ScenarioSelection
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.moduleOf
import com.github.vbmacher.intellij.cucumber.scala.util.{Dialogs, GherkinFileAction}
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileChooser.{FileChooserFactory, FileSaverDescriptor}
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinFile

import java.nio.file.{Files, Path, StandardCopyOption}
import javax.swing._
import scala.jdk.CollectionConverters._
import scala.util.Try

object ExportFeatures {
  def writeHtml(destination: Path, html: String): Unit = {
    val temporary = Files.createTempFile(destination.toAbsolutePath.getParent, ".cucumber-export-", ".html")
    try {
      Files.writeString(temporary, html, java.nio.charset.StandardCharsets.UTF_8)
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally Files.deleteIfExists(temporary)
  }


}

class ExportFeaturesAction extends GherkinFileAction("Export Features…") {
  override def actionPerformed(event: AnActionEvent): Unit = event.getData(CommonDataKeys.PSI_FILE) match {
    case feature: GherkinFile =>
      val project = feature.getProject
      val scope = new JComboBox[String](Array("Current feature", "Owning module", "Project contents"))
      val tags = new JTextField("")
      val format = new JComboBox[String](Array("HTML") ++ Option(project.getService(classOf[FeaturePdfExporter])).map(_ => "PDF"))
      if (Dialogs.form(project, "Export Features", None, "Scope" -> scope, "Tag expression" -> tags, "Format" -> format)) {
        val searchScope = scope.getSelectedIndex match {
          case 0 => GlobalSearchScope.fileScope(feature)
          case 1 => moduleOf(feature).map(_.getModuleWithDependenciesAndLibrariesScope(true)).getOrElse(GlobalSearchScope.EMPTY_SCOPE)
          case _ => GlobalSearchScope.projectScope(project)
        }
        ScenarioSelection.locations(project, searchScope, tags.getText) match {
          case Left(message) => Messages.showErrorDialog(project, message, "Export Features")
          case Right(locations) =>
            if (Dialogs.confirm(project, s"Export ${locations.size} Executable Scenarios / Example Rows", if (locations.isEmpty) "Empty selection" else locations.mkString("\n"))) {
              val documents = locations.groupBy(_.scenario.getContainingFile).toSeq.sortBy(_._1.getVirtualFile.getPath).map { case (file, selected) =>
                val rows = selected.collect { case l if l.target != l.scenario => l.target.getTextOffset }.toSet
                FeatureHtml.render(file.asInstanceOf[GherkinFile], Some(selected.map(_.scenario.getTextOffset).toSet), Some(rows))
              }
              // Each fragment contains only escaped, local content; export as one self-contained document.
              val html = if (documents.isEmpty) "<!doctype html><html><meta charset=\"UTF-8\"><body>Empty scenario selection.</body></html>"
                else documents.head.replace("</body></html>", documents.drop(1).map(d => d.substring(d.indexOf("<body>") + 6, d.lastIndexOf("</body>"))).mkString + "</body></html>")
              val extension = if (format.getSelectedItem == "PDF") "pdf" else "html"
              val saver = FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor("Export Features", "Choose an export destination", extension), project)
              Option(saver.save(java.nio.file.Path.of(project.getBasePath), "features." + extension)).foreach { chosen =>
                Try {
                  if (extension == "pdf") project.getService(classOf[FeaturePdfExporter]).write(project, chosen.getFile.toPath, html)
                  else ExportFeatures.writeHtml(chosen.getFile.toPath, html)
                }.failed.foreach(e => Messages.showErrorDialog(project, e.getMessage, "Export Features"))
              }
            }
        }
      }
    case _ =>
  }
}
