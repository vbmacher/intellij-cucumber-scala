package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor

class LibraryLightProjectDescriptor(libraries: Dependency*) extends DefaultLightProjectDescriptor {

  override def configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry): Unit = {
    super.configureModule(module, model, contentEntry)
    libraries.foreach(_.addToModule(module, model))
  }
}

trait Dependency {

  def addToModule(module: Module, model: ModifiableRootModel): Unit
}