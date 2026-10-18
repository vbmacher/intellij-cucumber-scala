package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.run.ScenarioSelection
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{SmartPointerManager, SmartPsiElementPointer}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

import java.util.Locale

object ScenarioSearch {
  final case class Document(title: String, text: String, path: String, line: Int,
                            pointer: SmartPsiElementPointer[GherkinStepsHolder]) {
    def navigation(project: Project): Option[OpenFileDescriptor] = Option(pointer.getElement)
      .filter(s => s.isValid && s.getText == text)
      .map(s => new OpenFileDescriptor(project, s.getContainingFile.getVirtualFile, ScenarioSelection.sourceOffset(s)))
  }
  final case class Hit(document: Document, similarity: Option[Double]) {
    override def toString: String = s"${document.title} · ${document.path}:${document.line}" +
      similarity.map(s => f" · similarity ${s * 100}%.0f%%").getOrElse("")
  }
  trait Embeddings {
    def id: String
    def embed(text: String, checkCanceled: () => Unit): Vector[Double]
  }
  final case class Index(provider: String, vectors: Map[String, Vector[Double]])

  /** Snapshot PSI under a short read action; embedding requests must run outside that action. */
  def documents(project: Project, scope: GlobalSearchScope): Seq[Document] =
    SuiteCatalog.scenarios(project, scope).map { scenario =>
      ProgressManager.checkCanceled()
      val file = scenario.getContainingFile
      val offset = ScenarioSelection.sourceOffset(scenario)
      Document(scenario.getScenarioName, scenario.getText, file.getVirtualFile.getPath,
        file.getText.take(offset).count(_ == '\n') + 1, SmartPointerManager.createPointer(scenario))
    }

  def exact(documents: Seq[Document], query: String, checkCanceled: () => Unit = () => ProgressManager.checkCanceled()): Seq[Hit] = {
    val needle = query.trim.toLowerCase(Locale.ROOT)
    documents.filter { document =>
      checkCanceled()
      document.text.toLowerCase(Locale.ROOT).contains(needle)
    }.map(Hit(_, None))
  }

  def build(documents: Seq[Document], provider: Embeddings,
            checkCanceled: () => Unit = () => ProgressManager.checkCanceled()): Index = {
    val vectors = documents.map(_.text).distinct.map { text =>
      checkCanceled()
      text -> normalized(provider.embed(text, checkCanceled))
    }.toMap
    checkCanceled()
    require(vectors.values.map(_.size).toSet.size <= 1, "Ollama returned inconsistent embedding dimensions.")
    Index(provider.id, vectors)
  }

  def semantic(documents: Seq[Document], query: String, provider: Embeddings, index: Index,
               checkCanceled: () => Unit = () => ProgressManager.checkCanceled()): Seq[Hit] = {
    require(index.provider == provider.id && documents.map(_.text).toSet == index.vectors.keySet,
      "Sources or model changed. Rebuild the semantic index.")
    if (query.trim.isEmpty || documents.isEmpty) Seq.empty
    else {
      checkCanceled()
      val vector = normalized(provider.embed(query, checkCanceled))
      require(index.vectors.values.forall(_.size == vector.size), "Embedding dimensions changed. Rebuild the semantic index.")
      documents.map { document =>
        checkCanceled()
        val score = vector.zip(index.vectors(document.text)).map { case (a, b) => a * b }.sum
        Hit(document, Some(math.max(-1.0, math.min(1.0, score))))
      }.filter(_.similarity.exists(_ > 0)).sortBy(h => (-h.similarity.getOrElse(0.0), h.document.path, h.document.line)).take(50)
    }
  }

  private def normalized(vector: Vector[Double]): Vector[Double] = {
    val norm = math.sqrt(vector.map(v => v * v).sum)
    require(vector.nonEmpty && vector.forall(_.isFinite) && norm.isFinite && norm > 0,
      "Ollama returned an invalid embedding vector.")
    vector.map(_ / norm)
  }
}
