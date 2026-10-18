package com.github.vbmacher.intellij.cucumber.scala.suite

import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProcessCanceledException
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuiteLike

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}

@RunWith(classOf[JUnit4])
class OllamaEmbeddingsSpec extends Matchers with JUnitSuiteLike {
  private def server(handler: HttpExchange => Unit)(test: String => Unit): Unit = {
    val http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    http.createContext("/api/embed", exchange => try handler(exchange) finally exchange.close())
    http.start()
    try test(s"http://127.0.0.1:${http.getAddress.getPort}") finally http.stop(0)
  }
  private def respond(exchange: HttpExchange, code: Int, body: String): Unit = {
    val bytes = body.getBytes(UTF_8)
    exchange.sendResponseHeaders(code, bytes.length)
    exchange.getResponseBody.write(bytes)
  }

  @Test
  def testConfiguredModelAndTextUseTheOllamaEmbedContract(): Unit = {
    val request = new CompletableFuture[String]()
    server { exchange =>
      request.complete(new String(exchange.getRequestBody.readAllBytes(), UTF_8))
      respond(exchange, 200, "{\"embeddings\":[[0.6,0.8]]}")
    } { url =>
      val provider = new OllamaEmbeddings(url, "chosen-model")
      provider.embed("withdrawal \"declined\"", () => ()) shouldBe Vector(0.6, 0.8)
      val body = JsonParser.parseString(request.get(5, TimeUnit.SECONDS)).getAsJsonObject
      body.get("model").getAsString shouldBe "chosen-model"
      body.get("input").getAsString shouldBe "withdrawal \"declined\""
      body.get("truncate").getAsBoolean shouldBe false
      provider.id should include("chosen-model")
    }
  }

  @Test
  def testUnavailableModelAndMalformedResponseAreActionableFailures(): Unit = {
    server(exchange => respond(exchange, 404, "{\"error\":\"model missing\"}")) { url =>
      val error = intercept[java.io.IOException](new OllamaEmbeddings(url, "missing").embed("text", () => ()))
      error.getMessage should include("HTTP 404")
      error.getMessage should include("Settings")
    }
    server(exchange => respond(exchange, 200, "{\"embeddings\":[]}")) { url =>
      intercept[java.io.IOException](new OllamaEmbeddings(url, "model").embed("text", () => ()))
    }
  }

  @Test
  def testCancellationInterruptsARequestWhileTheServerIsStillWaiting(): Unit = {
    val received = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    server { _ => received.countDown(); release.await(5, TimeUnit.SECONDS) } { url =>
      try {
        val provider = new OllamaEmbeddings(url, "model")
        intercept[ProcessCanceledException] {
          provider.embed("text", () => if (received.getCount == 0) throw new ProcessCanceledException())
        }
        release.getCount shouldBe 1L
      } finally release.countDown()
    }
  }
}
