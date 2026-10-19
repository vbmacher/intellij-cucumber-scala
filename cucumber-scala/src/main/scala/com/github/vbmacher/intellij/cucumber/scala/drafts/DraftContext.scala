package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.run.ScenarioSelection
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.{inReadAction, matchingSteps, moduleOf}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiErrorElement, PsiFileFactory}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._

object DraftContext {
  val maxContextLength: Int = 32000
  final case class Validation(errors: Seq[String], steps: Seq[String]) {
    def summary: String = (errors ++ steps :+ "Not executed").mkString("\n")
  }

  final case class Snapshot(file: GherkinFile, document: Document, stamp: Long, offset: Int,
                            indent: String, text: String) {
    def problem: Option[String] = inReadAction {
      if (!file.isValid || file.getProject.isDisposed) Some("The destination is unavailable. Close this window and reopen the action.")
      else if (document.getModificationStamp != stamp) Some("The feature changed. Close this window and review a new request.")
      else if (!file.isWritable || !document.isWritable) Some("The destination is read-only.")
      else None
    }

    def formatted(draft: ScenarioDraft): String = {
      val lines = draft.gherkin.replace("\r\n", "\n").replace('\r', '\n').stripTrailing().split("\n", -1).toSeq
      val margin = lines.filter(_.trim.nonEmpty).map(_.takeWhile(_.isWhitespace).length).minOption.getOrElse(0)
      lines.map(line => if (line.trim.isEmpty) "" else indent + line.drop(margin)).mkString("\n")
    }
    def insertion(drafts: Seq[ScenarioDraft]): String = "\n\n" + drafts.map(formatted).mkString("\n\n") + "\n"

    /** Called in a smart-mode read action. The temporary PSI never enters the project or step index. */
    def validate(drafts: Seq[ScenarioDraft]): Validation = {
      val source = document.getText
      val addition = insertion(drafts)
      val preview = PsiFileFactory.getInstance(file.getProject)
        .createFileFromText(file.getName, GherkinFileType.INSTANCE, source.patch(offset, addition, 0)).asInstanceOf[GherkinFile]
      def holders(root: PsiElement): Seq[GherkinStepsHolder] = PsiTreeUtil.findChildrenOfType(root, classOf[GherkinStepsHolder]).asScala.toSeq
      val added = holders(preview).filter(s => s.getTextOffset >= offset && s.getTextOffset < offset + addition.length)
      val retained = holders(preview).filterNot(added.contains)
      val errors = Vector.newBuilder[String]
      errors ++= PsiTreeUtil.findChildrenOfType(preview, classOf[PsiErrorElement]).asScala.map(e => s"Syntax: ${e.getErrorDescription}")
      if (drafts.isEmpty || added.size != drafts.size || holders(preview).size != holders(file).size + drafts.size ||
        preview.getFeatures.length != file.getFeatures.length ||
        PsiTreeUtil.findChildrenOfType(preview, classOf[GherkinRule]).size() != PsiTreeUtil.findChildrenOfType(file, classOf[GherkinRule]).size())
        errors += "Each draft must contain exactly one Scenario or Scenario Outline, without Feature or Rule headers."
      if (retained.map(_.getText.trim) != holders(file).map(_.getText.trim))
        errors += "Drafts must not change existing scenarios or their tags."
      added.foreach { scenario =>
        if (scenario.isInstanceOf[GherkinScenario] && scenario.asInstanceOf[GherkinScenario].isBackground)
          errors += "Drafts cannot add a Background."
        if (scenario.getScenarioName.trim.isEmpty || scenario.getSteps.isEmpty) errors += "Each draft needs a scenario title and at least one step."
        // The editor parser accepts stray description text even after steps; executable Gherkin does not.
        if (scenario.getSteps.headOption.exists(first => scenario.getNode.getChildren(null).exists(child =>
          child.getStartOffset > first.getTextOffset && child.getElementType == GherkinTokenTypes.TEXT)))
          errors += "Unexpected text after a step. Use a Gherkin step keyword, table or DocString."
        scenario.getSteps.flatMap(step => Option(step.getPystring)).foreach { value =>
          val lines = value.getText.trim.linesIterator.toSeq
          val delimiter = lines.headOption.map(_.trim.take(3)).getOrElse("")
          if (lines.size < 2 || !Set("\"\"\"", "```").contains(delimiter) || !lines.lastOption.exists(_.trim == delimiter))
            errors += "DocStrings need a matching closing delimiter on its own line."
        }
        scenario match {
          case outline: GherkinScenarioOutline =>
            val blocks = outline.getExamplesBlocks.asScala
            if (blocks.isEmpty || blocks.exists(b => Option(b.getTable).forall(_.getDataRows.isEmpty)))
              errors += "Scenario Outlines need Examples with data rows."
          case _ => ()
        }
        PsiTreeUtil.findChildrenOfType(scenario, classOf[GherkinTable]).asScala.foreach { table =>
          val width = Option(table.getHeaderRow).map(_.getPsiCells.size()).getOrElse(0)
          if (width == 0 || table.getDataRows.asScala.exists(_.getPsiCells.size() != width))
            errors += "Tables must have the same number of cells in every row."
        }
      }
      val module = moduleOf(file)
      val steps = added.flatMap(_.getSteps).map { step =>
        val matches = module.toSeq.flatMap(matchingSteps(step, _))
        if (module.isEmpty) s"Validation unavailable (no destination module): ${step.getName}"
        else if (matches.isEmpty) s"Unresolved: ${step.getName}"
        else if (matches.size > 1) s"Ambiguous: ${step.getName}\n" + matches.flatMap(d => Option(d.getElement)).map { target =>
          val line = target.getContainingFile.getText.take(target.getTextOffset).count(_ == '\n') + 1
          s"  ${target.getContainingFile.getName}:$line"
        }.mkString("\n")
        else s"Step definition found: ${step.getName}"
      }
      Validation(errors.result().distinct, steps)
    }

    /** Revalidate and check the snapshot inside the same command as the edit. */
    def insert(drafts: Seq[ScenarioDraft]): Either[String, Unit] = {
      var result: Either[String, Unit] = Left("Nothing inserted.")
      WriteCommandAction.runWriteCommandAction(file.getProject, "Insert edge-case scenarios", null, () => {
        val invalid = problem.orElse(if (DumbService.isDumb(file.getProject)) Some("Wait for indexing before inserting drafts.") else None)
        result = invalid.toLeft(()).flatMap { _ =>
          PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
          val checked = validate(drafts)
          if (checked.errors.nonEmpty) Left(checked.errors.mkString("\n"))
          else {
            document.insertString(offset, insertion(drafts))
            PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
            Right(())
          }
        }
      })
      result
    }
  }

  /** Selection is the specification context; its last scenario determines the insertion scope. */
  def collect(file: GherkinFile, document: Document, caret: Int, selection: Option[(Int, Int)]): Either[String, Snapshot] = {
    val source = document.getText
    val selected = selection.filter { case (start, end) => source.substring(start, end).trim.nonEmpty }
    val point = selected.map(_._2 - 1).getOrElse(caret).min(math.max(0, source.length - 1))
    val selectedScenario = selected.flatMap { case (start, end) =>
      PsiTreeUtil.findChildrenOfType(file, classOf[GherkinStepsHolder]).asScala.toSeq
        .filter(s => s.getTextRange.getStartOffset < end && s.getTextRange.getEndOffset > start).lastOption
    }
    val leaf = selectedScenario.getOrElse(file.findElementAt(point))
    val ancestors = Iterator.iterate(leaf)(_.getParent).takeWhile(_ != null).toSeq
    val scenario = ancestors.collectFirst { case s: GherkinStepsHolder => s }
    val container = ancestors.collectFirst { case r: GherkinRule => r; case f: GherkinFeature => f }
      .orElse(file.getFeatures.lastOption)
    container.toRight("Place the caret in a feature or scenario.").map { scope =>
      val anchor = scenario.getOrElse(scope)
      val offset = anchor.getTextRange.getEndOffset
      def leading(element: PsiElement): String = {
        val start = ScenarioSelection.sourceOffset(element)
        source.substring(source.lastIndexOf('\n', math.max(0, start - 1)) + 1, start).takeWhile(c => c == ' ' || c == '\t')
      }
      val indent = scenario.map(leading).getOrElse {
        scope.getChildren.collectFirst { case s: GherkinStepsHolder => leading(s) }.getOrElse(leading(scope) + "  ")
      }
      def header(element: PsiElement): String = {
        val children = element.getChildren.filter(e => e.isInstanceOf[GherkinStepsHolder] || e.isInstanceOf[GherkinRule])
        source.substring(element.getTextRange.getStartOffset, children.headOption.map(_.getTextRange.getStartOffset).getOrElse(element.getTextRange.getEndOffset)).trim
      }
      val context = if (scenario.isEmpty && selected.isEmpty) {
        val featureHeader = ancestors.collectFirst { case f: GherkinFeature if f != scope => header(f) }.toSeq
        s"Gherkin language: ${file.getLocaleLanguage}\n\n" + (featureHeader :+ scope.getText).mkString("\n\n")
      }
      else {
        val headers = ancestors.reverse.collect { case f: GherkinFeature => header(f); case r: GherkinRule => header(r) }
        val backgrounds = ancestors.reverse.flatMap(_.getChildren.collect { case s: GherkinScenario if s.isBackground => s.getText })
        s"Gherkin language: ${file.getLocaleLanguage}\n\n" + (headers ++ backgrounds :+
          selected.map { case (start, end) => source.substring(start, end) }.getOrElse(anchor.getText)).mkString("\n\n")
      }
      Snapshot(file, document, document.getModificationStamp, offset, indent, context)
    }
  }
}
