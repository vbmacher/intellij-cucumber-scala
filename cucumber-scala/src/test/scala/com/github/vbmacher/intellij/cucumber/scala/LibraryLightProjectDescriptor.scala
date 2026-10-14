package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.testFramework.{LightProjectDescriptor, PsiTestUtil}

import java.io.File
import java.net.URL

case class LocalDependency(libraryName: String, jarPath: URL, srcPath: URL)

class LibraryLightProjectDescriptor(libraries: LocalDependency*) extends LightProjectDescriptor {

  override def configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry): Unit = {
    super.configureModule(module, model, contentEntry)
    for (library <- libraries) {
      PsiTestUtil.newLibrary(library.libraryName)
        .classesRoot(new File(library.jarPath.toURI).getPath)
        .sourceRoot(new File(library.srcPath.toURI).getPath)
        .addTo(model)
    }
  }
}
