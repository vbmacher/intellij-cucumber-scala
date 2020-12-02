package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.jarRepository.{JarRepositoryManager, RemoteRepositoryDescription}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.{DependencyScope, ModifiableRootModel}
import com.intellij.project.IntelliJProjectConfiguration
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class RemoteDependency(coordinates: Seq[String]) extends Dependency {
  require(coordinates.nonEmpty)

  def addToModule(module: Module, model: ModifiableRootModel): Unit = {
    val tableModel = model.getModuleLibraryTable.getModifiableModel
    val library = tableModel.createLibrary(coordinates.head)
    val libraryModel = library.getModifiableModel

    for (coordinates <- coordinates) {
      val roots = loadRoots(module.getProject, coordinates)
      for (root <- roots) {
        libraryModel.addRoot(root.getFile, root.getType)
      }
    }

    libraryModel.commit()
    tableModel.commit()

    model.findLibraryOrderEntry(library).setScope(DependencyScope.COMPILE)
  }

  private def loadRoots(project: Project, coordinates: String): Seq[OrderRoot] = {
    def libraryProperties = new RepositoryLibraryProperties(coordinates, true)

    val roots = JarRepositoryManager.loadDependenciesModal(
      project, libraryProperties, false, false, null,
      getRemoteRepositoryDescriptions.asJava
    )

    require(!roots.isEmpty)
    roots.asScala.toSeq
  }

  private def getRemoteRepositoryDescriptions: Seq[RemoteRepositoryDescription] = {
    Try(IntelliJProjectConfiguration.getRemoteRepositoryDescriptions.asScala.map {
      repository => new RemoteRepositoryDescription(repository.getId, repository.getName, repository.getUrl)
    }) match {
      case Success(value) => value.toSeq
      case Failure(_) => Seq(
        RemoteRepositoryDescription.MAVEN_CENTRAL
      )
    }
  }
}

object RemoteDependency {

  def apply(coordinates: String*): RemoteDependency = {
    new RemoteDependency(coordinates)
  }
}