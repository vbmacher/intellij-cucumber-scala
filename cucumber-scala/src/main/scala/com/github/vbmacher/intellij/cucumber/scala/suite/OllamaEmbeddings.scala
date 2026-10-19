package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.util.OllamaHttp
import com.google.gson.JsonObject

import java.io.IOException
import java.time.Duration
import scala.jdk.CollectionConverters._
import scala.util.Try

final class OllamaEmbeddings(url: String, model: String) extends ScenarioSearch.Embeddings {
  override val id: String = s"Ollama · ${url.stripSuffix("/")} · $model"

  override def embed(text: String, checkCanceled: () => Unit): Vector[Double] = {
    require(model.trim.nonEmpty, "Configure an embedding model in Settings → Scala Cucumber.")
    val body = new JsonObject()
    body.addProperty("model", model)
    body.addProperty("input", text)
    body.addProperty("truncate", false)
    val result = OllamaHttp.post(url, "/api/embed", body, Duration.ofMinutes(2), checkCanceled)
    Try {
      val embeddings = result.getAsJsonArray("embeddings")
      require(embeddings.size() == 1)
      embeddings.get(0).getAsJsonArray.asScala.map(_.getAsDouble).toVector
    }.getOrElse(throw new IOException("Ollama returned invalid embedding data."))
  }
}
