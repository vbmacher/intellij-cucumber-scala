package com.github.vbmacher.intellij.cucumber.scala.util

import com.google.gson.{JsonObject, JsonParser}
import com.intellij.openapi.progress.ProcessCanceledException

import java.io.IOException
import java.net.{ConnectException, URI}
import java.net.http.{HttpClient, HttpConnectTimeoutException, HttpRequest, HttpResponse, HttpTimeoutException}
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
    val address = endpoint(url, path)
    val request = HttpRequest.newBuilder(address).timeout(timeout)
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
        throw new IOException(responseError(result.statusCode(), result.body()))
      Try(JsonParser.parseString(result.body()).getAsJsonObject)
        .getOrElse(throw new IOException("Ollama returned invalid JSON data."))
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); throw new ProcessCanceledException()
      case error: ExecutionException =>
        val message = error.getCause match {
          case _: ConnectException | _: HttpConnectTimeoutException =>
            s"Could not connect to Ollama at $address. Start the server and check the URL and port in Settings → Scala Cucumber."
          case _: HttpTimeoutException =>
            s"Ollama at $address did not respond within ${timeout.toSeconds} seconds. The model may still be loading; check the server logs or use a smaller model."
          case _ => s"Could not complete the request to Ollama at $address. Check the server connection and logs."
        }
        throw new IOException(message)
    } finally pending.cancel(true)
  }

  private def responseError(status: Int, body: String): String = {
    // Classify known failures without displaying provider data or credentials.
    val detail = Try(JsonParser.parseString(body).getAsJsonObject.get("error").getAsString).getOrElse("")
    val advice = if (detail.contains("does not support embeddings"))
      "The selected model cannot generate embeddings on this server. Choose a supported embedding model and check its minimum Ollama version."
    else if (detail.contains("does not support chat") || detail.contains("does not support generate"))
      "The selected model cannot generate chat responses. Choose an installed chat model."
    else status match {
      case 401 | 403 => "The server denied access. Local Ollama needs no token; check authentication only if this server requires it."
      case 404 => "The model or API endpoint was not found. Check the Ollama URL and installed model in Settings → Scala Cucumber."
      case _ => "The server could not process the request. Check its logs, model compatibility and available memory."
    }
    s"Ollama returned HTTP $status. $advice"
  }
}
