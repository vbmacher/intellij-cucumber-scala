package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.cucumber.psi.GherkinTag

import scala.util.Try

object RequirementReferences {
  // One regular expression => relative Markdown path#id OR https:// URL template per line.
  def destination(tag: GherkinTag): Option[String] = {
    ScalaCucumberSettings.getInstance(tag.getProject).requirementMappings.linesIterator
      .filterNot(line => line.trim.isEmpty || line.trim.startsWith("#")).flatMap { line =>
        val split = line.indexOf("=>")
        if (split < 0) None else Try {
          val matcher = java.util.regex.Pattern.compile(line.substring(0, split).trim).matcher(tag.getText)
          if (matcher.matches()) Some(matcher.replaceFirst(line.substring(split + 2).trim)) else None
        }.toOption.flatten
      }.take(1).toSeq.headOption
  }

  def targets(tag: GherkinTag): Seq[PsiElement] = destination(tag).toSeq.flatMap { target =>
    if (target.startsWith("https://") || target.startsWith("http://")) Seq(new RequirementTarget(tag, target, None, 0): PsiElement)
    else {
      val split = target.lastIndexOf('#')
      if (split < 0) Seq.empty
      else {
        val relative = target.substring(0, split)
        val id = target.substring(split + 1)
        val roots = Option(tag.getProject.getBaseDir).toSeq ++ com.intellij.openapi.roots.ProjectRootManager.getInstance(tag.getProject).getContentRoots
        roots.distinct.flatMap(root => Option(root.findFileByRelativePath(relative))).distinct.flatMap { file =>
          Option(PsiManager.getInstance(tag.getProject).findFile(file)).toSeq.flatMap { psi =>
            val quoted = java.util.regex.Pattern.quote(id)
            // Explicit Markdown IDs and HTML anchors; ordinary prose is not an acceptance-criterion declaration.
            val pattern = ("(?m)(?:\\{#(" + quoted + ")\\}|\\bid=[\"'](" + quoted + ")[\"']|^#{1,6}\\s+(" + quoted + ")(?:\\s|$))").r
            pattern.findAllMatchIn(psi.getText).map { m =>
              val group = (1 to m.groupCount).find(i => m.group(i) != null).get
              new RequirementTarget(tag, id, Some(psi), m.start(group)): PsiElement
            }.toSeq
          }
        }
      }
    }
  }
}

class RequirementTarget(tag: GherkinTag, label: String, file: Option[PsiFile], offset: Int) extends FakePsiElement {
  override def getParent: PsiElement = file.getOrElse(tag)
  override def getName: String = label
  override def getTextOffset: Int = offset
  override def getTextRange: TextRange = TextRange.from(offset, if (file.nonEmpty) label.length else 0)
  override def isValid: Boolean = tag.isValid && file.forall(_.isValid)
  override def canNavigate: Boolean = isValid
  override def canNavigateToSource: Boolean = file.nonEmpty && isValid
  override def navigate(requestFocus: Boolean): Unit = file match {
    case Some(source) => new OpenFileDescriptor(source.getProject, source.getVirtualFile, offset).navigate(requestFocus)
    case None => if (Dialogs.confirm(tag.getProject, "Open Requirement URL", label)) BrowserUtil.browse(label)
  }
}

class RequirementReferenceContributor extends PsiReferenceContributor {
  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit =
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[GherkinTag]), new PsiReferenceProvider {
      override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = element match {
        case tag: GherkinTag if RequirementReferences.destination(tag).nonEmpty => Array(new PsiPolyVariantReferenceBase[GherkinTag](tag, TextRange.from(1, math.max(0, tag.getTextLength - 1)), true) {
          override def multiResolve(incompleteCode: Boolean): Array[ResolveResult] = RequirementReferences.targets(tag).map(new PsiElementResolveResult(_): ResolveResult).toArray
          override def getVariants: Array[AnyRef] = Array.empty
        })
        case _ => PsiReference.EMPTY_ARRAY
      }
    })
}
