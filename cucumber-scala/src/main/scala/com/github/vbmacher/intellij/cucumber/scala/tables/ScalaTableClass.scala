package com.github.vbmacher.intellij.cucumber.scala.tables

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.{FileChooser, FileChooserDescriptorFactory}
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{JavaPsiFacade, PsiFileFactory, PsiManager}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator


object ScalaTableClass {
  def source(name: String, cells: Seq[Seq[String]]): Either[String, String] = {
    if (!ScalaNamesValidator.isIdentifier(name)) Left("Choose a valid Scala class name.")
    else if (cells.isEmpty || cells.map(_.size).distinct.size != 1) Left("A rectangular table with headers is required.")
    else {
      val headers = cells.head.map(GherkinTables.decode)
      if (headers.exists(h => h.isEmpty || h.contains('`') || h.contains('\n')) || headers.distinct.size != headers.size)
        Left("Headers must be nonempty and unique; rename invalid or duplicate headers before generating a class.")
      else {
        val fields = headers.zipWithIndex.map { case (header, i) =>
          val field = if (ScalaNamesValidator.isIdentifier(header)) header else s"`$header`"
          val values = cells.drop(1).map(row => GherkinTables.decode(row(i)))
          val tpe = if (values.isEmpty || values.exists(_.isEmpty)) "String"
            else if (values.forall(v => v == "true" || v == "false")) "Boolean"
            else if (values.forall(_.toIntOption.nonEmpty)) "Int"
            else if (values.forall(_.toLongOption.nonEmpty)) "Long"
            else "String"
          s"$field: $tpe"
        }
        Right(s"case class $name(${fields.mkString(", ")})\n")
      }
    }
  }
}

class GenerateTableClassAction extends AnAction("Generate Scala Case Class…") {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(Option(event.getData(CommonDataKeys.EDITOR)).flatMap(GherkinTables.selection).exists(s => !s.isExamples))

  override def actionPerformed(event: AnActionEvent): Unit = {
    for {
      project <- Option(event.getProject)
      editor <- Option(event.getData(CommonDataKeys.EDITOR))
      selection <- GherkinTables.selection(editor)
      name <- Option(Messages.showInputDialog(project, "Class name", "Generate Scala Case Class", null, "DataRow", null))
    } {
      ScalaTableClass.source(name, selection.cells) match {
        case Left(message) => Messages.showErrorDialog(project, message, "Generate Scala Case Class")
        case Right(source) =>
          Dialogs.edit(project, "Review Scala Source · Edit Names, Types and Package; Mixed or Missing Values Use String", source).foreach { edited =>
            val folder = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null)
            Option(folder).flatMap(f => Option(PsiManager.getInstance(project).findDirectory(f))).foreach { directory =>
              val fileName = name + ".scala"
              val parsed = PsiFileFactory.getInstance(project).createFileFromText(fileName, ScalaFileType.INSTANCE, edited)
              val errors = com.intellij.psi.util.PsiTreeUtil.findChildOfType(parsed, classOf[com.intellij.psi.PsiErrorElement])
              val scalaFile = parsed.asInstanceOf[org.jetbrains.plugins.scala.lang.psi.api.ScalaFile]
              val conflicts = scalaFile.typeDefinitions.exists { definition =>
                JavaPsiFacade.getInstance(project).findClasses(definition.getQualifiedName, GlobalSearchScope.allScope(project)).nonEmpty
              }
              if (!directory.isWritable || directory.findFile(fileName) != null || errors != null || conflicts)
                Messages.showErrorDialog(project, "Choose a writable folder and valid Scala source with no existing class or filename conflicts.", "Generate Scala Case Class")
              else WriteCommandAction.runWriteCommandAction(project, "Generate Scala case class", null, () => { directory.add(parsed); () })
            }
          }
      }
    }
  }
}
