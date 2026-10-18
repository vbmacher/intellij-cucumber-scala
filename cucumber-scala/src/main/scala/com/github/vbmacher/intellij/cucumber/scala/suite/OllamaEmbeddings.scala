package com.github.vbmacher.intellij.cucumber.scala.suite

import com.google.gson.{JsonObject, JsonParser}
import com.intellij.openapi.progress.ProcessCanceledException

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.{ExecutionException, TimeUnit, TimeoutException}
import scala.jdk.CollectionConverters._
import scala.util.Try

final class OllamaEmbeddings(url: String, model: String) extends ScenarioSearch.Embeddings {
  override val id: String = s"Ollama · ${url.stripSuffix("/")} · $model"

  override def embed(text: String, checkCanceled: () => Unit): Vector[Double] = {
    require(model.trim.nonEmpty, "Configure an embedding model in Settings → Scala Cucumber.")
    val endpoint = URI.create(url.stripSuffix("/") + "/api/embed")
    require(Set("http", "https").contains(endpoint.getScheme) && endpoint.getHost != null,
      "Configure a valid Ollama URL in Settings → Scala Cucumber.")
    checkCanceled()
    val body = new JsonObject()
    body.addProperty("model", model)
    body.addProperty("input", text)
    body.addProperty("truncate", false)
    val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString)).build()
    val pending = OllamaEmbeddings.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    try {
      var response: Option[HttpResponse[String]] = None
      while (response.isEmpty) {
        checkCanceled()
        try response = Some(pending.get(100, TimeUnit.MILLISECONDS))
        catch { case _: TimeoutException => () }
      }
      checkCanceled()
      val result = response.getOrElse(throw new IOException("Ollama returned no response."))
      if (result.statusCode() != 200)
        throw new IOException(s"Ollama returned HTTP ${result.statusCode()}. Check the server and installed embedding model in Settings → Scala Cucumber.")
      Try {
        val embeddings = JsonParser.parseString(result.body()).getAsJsonObject.getAsJsonArray("embeddings")
        require(embeddings.size() == 1)
        embeddings.get(0).getAsJsonArray.asScala.map(_.getAsDouble).toVector
      }.getOrElse(throw new IOException("Ollama returned invalid embedding data."))
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); throw new ProcessCanceledException()
      case e: ExecutionException => throw new IOException("Ollama is unavailable. Check the URL and installed embedding model in Settings → Scala Cucumber.", e.getCause)
    } finally pending.cancel(true)
  }
}

private object OllamaEmbeddings {
  lazy val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
}
