package com.github.vbmacher.intellij.cucumber.scala

trait FeaturePdfExporter {
  def write(project: com.intellij.openapi.project.Project, destination: java.nio.file.Path, html: String): Unit
}
