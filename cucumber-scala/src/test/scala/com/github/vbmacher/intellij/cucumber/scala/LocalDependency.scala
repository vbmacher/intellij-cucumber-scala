package com.github.vbmacher.intellij.cucumber.scala

import java.io.File
import java.net.URL

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.PsiTestUtil

case class LocalDependency(libraryName: String, jarPath: URL, srcPath: URL) extends Dependency {

  def addToModule(module: Module, model: ModifiableRootModel): Unit = {
    PsiTestUtil.newLibrary(libraryName)
        .classesRoot(new File(jarPath.toURI).getPath)
        .sourceRoot(new File(srcPath.toURI).getPath)
        .addTo(model)
  }
}
