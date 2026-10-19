package com.github.vbmacher.intellij.cucumber.scala.util

import com.google.gson.{JsonObject, JsonParser}
import com.intellij.openapi.progress.ProcessCanceledException

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.{ExecutionException, TimeUnit, TimeoutException}
import scala.util.Try

object OllamaHttp {
  private lazy val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

  def endpoint(url: String, path: String): URI = {
    val base = Try(URI.create(url.trim.stripSuffix("/"))).toOption.filter(uri =>
      Set("http", "https").contains(uri.getScheme) && uri.getHost != null &&
        uri.getUserInfo == null && uri.getQuery == null && uri.getFragment == null)
      .getOrElse(throw new IOException("Configure a valid Ollama URL without credentials, query or fragment in Settings → Scala Cucumber."))
    URI.create(base.toString + path)
  }

  def post(url: String, path: String, body: JsonObject, timeout: Duration,
           checkCanceled: () => Unit, token: Option[String] = None): JsonObject = {
    val request = HttpRequest.newBuilder(endpoint(url, path)).timeout(timeout)
      .header("Content-Type", "application/json")
    token.filter(_.nonEmpty).foreach { secret =>
      if (secret.exists(c => c < ' ' || c > '~')) throw new IOException("The stored access token contains invalid characters. Replace it in Settings → Scala Cucumber.")
      request.header("Authorization", "Bearer " + secret)
    }
    checkCanceled()
    val pending = client.sendAsync(request.POST(HttpRequest.BodyPublishers.ofString(body.toString)).build(), HttpResponse.BodyHandlers.ofString())
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
        throw new IOException(s"Ollama returned HTTP ${result.statusCode()}. Check the server, access token and installed model in Settings → Scala Cucumber.")
      Try(JsonParser.parseString(result.body()).getAsJsonObject)
        .getOrElse(throw new IOException("Ollama returned invalid JSON data."))
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); throw new ProcessCanceledException()
      case _: ExecutionException => throw new IOException("Ollama is unavailable or the request timed out. Check Settings → Scala Cucumber.")
    } finally pending.cancel(true)
  }
}
