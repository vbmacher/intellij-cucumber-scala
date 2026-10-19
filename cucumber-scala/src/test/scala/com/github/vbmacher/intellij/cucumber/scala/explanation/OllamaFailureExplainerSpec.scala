package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.google.gson.JsonParser
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
class OllamaFailureExplainerSpec extends Matchers with JUnitSuiteLike {
  private def server(handler: HttpExchange => Unit)(test: String => Unit): Unit = {
    val http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    http.createContext("/api/chat", exchange => try handler(exchange) finally exchange.close())
    http.start()
    try test(s"http://127.0.0.1:${http.getAddress.getPort}") finally http.stop(0)
  }
  private def respond(exchange: HttpExchange, code: Int, body: String): Unit = {
    val bytes = body.getBytes(UTF_8)
    exchange.sendResponseHeaders(code, bytes.length)
    exchange.getResponseBody.write(bytes)
  }

  @Test
  def testExactReviewedContextAndConfiguredModelWithTokenOnlyInHeader(): Unit = {
    val request = new CompletableFuture[String]()
    val authorization = new CompletableFuture[String]()
    server { exchange =>
      request.complete(new String(exchange.getRequestBody.readAllBytes(), UTF_8))
      authorization.complete(exchange.getRequestHeaders.getFirst("Authorization"))
      respond(exchange, 200, """{"done":true,"message":{"content":"Possible cause; verify the assertion."}}""")
    } { url =>
      val context = "Reviewed and redacted context\nSteps.scala:4"
      new OllamaFailureExplainer(url, "chosen-chat", () => Some("test-secret"))
        .explain(context, () => ()) shouldBe "Possible cause; verify the assertion."
      authorization.get(5, TimeUnit.SECONDS) shouldBe "Bearer test-secret"
      val raw = request.get(5, TimeUnit.SECONDS)
      raw should not include "test-secret"
      val body = JsonParser.parseString(raw).getAsJsonObject
      body.get("model").getAsString shouldBe "chosen-chat"
      body.get("stream").getAsBoolean shouldBe false
      val messages = body.getAsJsonArray("messages")
      messages.size() shouldBe 2
      messages.get(0).getAsJsonObject.get("content").getAsString should include("not proof")
      messages.get(1).getAsJsonObject.get("content").getAsString shouldBe context
    }
  }

  @Test
  def testLocalServerWorksWithoutAToken(): Unit = {
    val authorization = new CompletableFuture[String]()
    server { exchange =>
      authorization.complete(exchange.getRequestHeaders.getFirst("Authorization"))
      respond(exchange, 200, """{"done":true,"message":{"content":"Check the assertion."}}""")
    } { url =>
      new OllamaFailureExplainer(url, "chat", () => None).explain("context", () => ()) shouldBe "Check the assertion."
      authorization.get(5, TimeUnit.SECONDS) shouldBe null
    }
  }

  @Test
  def testFailuresAreActionableWithoutEchoingProviderDataOrCredentials(): Unit = {
    server(exchange => respond(exchange, 401, "private-provider-data")) { url =>
      val error = intercept[IOException](new OllamaFailureExplainer(url, "model", () => Some("secret" )).explain("context", () => ()))
      error.getMessage should include("HTTP 401")
      error.getMessage should not include "private-provider-data"
      error.getMessage should not include "secret"
    }
    Seq("not json", "{}", """{"done":false,"message":{"content":"partial"}}""",
      """{"done":true,"message":{"content":""}}""").foreach { response =>
      server(exchange => respond(exchange, 200, response)) { url =>
        intercept[IOException](new OllamaFailureExplainer(url, "model", () => None).explain("context", () => ()))
      }
    }
    val invalid = intercept[IOException](new OllamaFailureExplainer("http://user:private@localhost", "model", () => None).explain("context", () => ()))
    invalid.getMessage should not include "private"
    val token = intercept[IOException](new OllamaFailureExplainer("http://localhost", "model", () => Some("private\u0000")).explain("context", () => ()))
    token.getMessage should not include "private"
  }

  @Test
  def testCancellationStopsWaitingForAnInflightResponse(): Unit = {
    val received = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    server { _ => received.countDown(); release.await(5, TimeUnit.SECONDS) } { url =>
      try {
        intercept[ProcessCanceledException] {
          new OllamaFailureExplainer(url, "model", () => None).explain("context", () =>
            if (received.getCount == 0) throw new ProcessCanceledException())
        }
        release.getCount shouldBe 1L
      } finally release.countDown()
    }
  }
}
