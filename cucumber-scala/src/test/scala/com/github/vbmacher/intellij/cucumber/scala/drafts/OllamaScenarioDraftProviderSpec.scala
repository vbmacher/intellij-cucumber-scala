package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.google.gson.{JsonObject, JsonParser}
import com.intellij.openapi.progress.ProcessCanceledException
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuiteLike

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}

@RunWith(classOf[JUnit4])
class OllamaScenarioDraftProviderSpec extends Matchers with JUnitSuiteLike {
  private val content = """{"drafts":[{"title":"Zero","reason":"Lower boundary","gherkin":"Scenario: Zero\n  Given account"}]}"""
  private def server(handler: HttpExchange => Unit)(test: String => Unit): Unit = {
    val http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    http.createContext("/api/chat", exchange => try handler(exchange) finally exchange.close())
    http.start()
    try test(s"http://127.0.0.1:${http.getAddress.getPort}") finally http.stop(0)
  }
  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes(UTF_8)
    exchange.sendResponseHeaders(status, bytes.length)
    exchange.getResponseBody.write(bytes)
  }
  private def envelope(text: String): String = {
    val message = new JsonObject()
    message.addProperty("content", text)
    val body = new JsonObject()
    body.addProperty("done", true)
    body.add("message", message)
    body.toString
  }

  @Test
  def testConfiguredModelAndExactReviewedContext(): Unit = {
    val request = new CompletableFuture[String]()
    val authorization = new CompletableFuture[String]()
    server { exchange =>
      request.complete(new String(exchange.getRequestBody.readAllBytes(), UTF_8))
      authorization.complete(exchange.getRequestHeaders.getFirst("Authorization"))
      respond(exchange, 200, envelope(content))
    } { url =>
      new OllamaScenarioDraftProvider(url, "installed-chat", () => Some("fixture-secret")).suggest("reviewed context only", () => ()) shouldBe
        Seq(ScenarioDraft("Zero", "Lower boundary", "Scenario: Zero\n  Given account"))
      val body = JsonParser.parseString(request.get(5, TimeUnit.SECONDS)).getAsJsonObject
      authorization.get(5, TimeUnit.SECONDS) shouldBe "Bearer fixture-secret"
      body.toString should not include "fixture-secret"
      body.get("model").getAsString shouldBe "installed-chat"
      body.get("stream").getAsBoolean shouldBe false
      val format = body.getAsJsonObject("format")
      format.get("type").getAsString shouldBe "object"
      format.get("additionalProperties").getAsBoolean shouldBe false
      val drafts = format.getAsJsonObject("properties").getAsJsonObject("drafts")
      drafts.get("maxItems").getAsInt shouldBe OllamaScenarioDraftProvider.maxDrafts
      drafts.getAsJsonObject("items").getAsJsonArray("required").toString shouldBe "[\"title\",\"reason\",\"gherkin\"]"
      body.getAsJsonObject("options").get("temperature").getAsInt shouldBe 0
      body.getAsJsonArray("messages").get(1).getAsJsonObject.get("content").getAsString shouldBe "reviewed context only"
    }
  }

  @Test
  def testLocalServerWorksWithoutAToken(): Unit = {
    val authorization = new CompletableFuture[String]()
    server { exchange =>
      authorization.complete(exchange.getRequestHeaders.getFirst("Authorization"))
      respond(exchange, 200, envelope(content))
    } { url =>
      new OllamaScenarioDraftProvider(url, "chat").suggest("context", () => ()).map(_.title) shouldBe Seq("Zero")
      authorization.get(5, TimeUnit.SECONDS) shouldBe null
    }
  }

  @Test
  def testMalformedOrOversizedDraftsAndProviderErrors(): Unit = {
    Seq("not json", "{}", "{\"drafts\":[]}", content.replace("\"Zero\"", "42"),
      content.replace("Lower boundary", ""), content.replace("\"drafts\":", "\"extra\":1,\"drafts\":"),
      "x" * (OllamaScenarioDraftProvider.maxResponseLength + 1)).foreach { text =>
      intercept[IOException](OllamaScenarioDraftProvider.parse(text))
    }
    server(exchange => respond(exchange, 404, "private data")) { url =>
      val error = intercept[IOException](new OllamaScenarioDraftProvider(url, "missing").suggest("context", () => ()))
      error.getMessage should include("HTTP 404")
      error.getMessage should not include "private data"
    }
    server(exchange => respond(exchange, 200, "{\"done\":false}")) { url =>
      intercept[IOException](new OllamaScenarioDraftProvider(url, "chat").suggest("context", () => ()))
    }
  }

  @Test
  def testCancelInFlightRequest(): Unit = {
    val received = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    server { _ => received.countDown(); release.await(5, TimeUnit.SECONDS) } { url =>
      try intercept[ProcessCanceledException] {
        new OllamaScenarioDraftProvider(url, "chat").suggest("context", () =>
          if (received.getCount == 0) throw new ProcessCanceledException())
      } finally release.countDown()
    }
  }
}
