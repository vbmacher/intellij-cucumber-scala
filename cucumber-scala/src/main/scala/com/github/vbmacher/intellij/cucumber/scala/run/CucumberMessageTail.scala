package com.github.vbmacher.intellij.cucumber.scala.run

import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler, ProcessOutputTypes}
import com.intellij.util.concurrency.AppExecutorUtil

import java.io.{ByteArrayOutputStream, RandomAccessFile}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ScheduledFuture, TimeUnit}
import scala.util.Try

class CucumberMessageTail(path: Path, handler: ProcessHandler, messages: CucumberMessages) {
  private val input = new RandomAccessFile(path.toFile, "r")
  private val pending = new ByteArrayOutputStream()
  private var closed = false
  private var complete = true
  private def drain(): Unit = synchronized {
    if (!closed) {
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while (count > 0) {
        var i = 0
        while (i < count) {
          if (buffer(i) == '\n') {
            val line = pending.toString(StandardCharsets.UTF_8)
            pending.reset()
            if (line.nonEmpty) Try(messages.consume(line)).failed.foreach { error =>
              complete = false
              handler.notifyTextAvailable("Cucumber result stream is incomplete: " + error.getMessage + "\n", ProcessOutputTypes.SYSTEM)
            }
          } else pending.write(buffer(i))
          i += 1
        }
        count = input.read(buffer)
      }
    }
  }
  private var scheduled: Option[ScheduledFuture[_]] = None
  handler.addProcessListener(new ProcessAdapter {
    override def startNotified(event: ProcessEvent): Unit = CucumberMessageTail.this.synchronized {
      // The runner starts notifications after attaching the console, so initial results cannot be lost.
      scheduled = Some(AppExecutorUtil.getAppScheduledExecutorService.scheduleWithFixedDelay(new Runnable {
        override def run(): Unit = Try(drain()).failed.foreach(_ => complete = false)
      }, 100, 100, TimeUnit.MILLISECONDS))
    }
    override def processTerminated(event: ProcessEvent): Unit = CucumberMessageTail.this.synchronized {
      scheduled.foreach(_.cancel(false))
      try {
        val drained = Try(drain())
        messages.close(interrupted = drained.isFailure || !complete || pending.size() > 0)
      } finally {
        closed = true
        input.close()
        Files.deleteIfExists(path)
      }
    }
  })
}
