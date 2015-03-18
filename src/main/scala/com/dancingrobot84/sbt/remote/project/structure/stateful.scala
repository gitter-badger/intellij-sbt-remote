package com.dancingrobot84.sbt.remote
package project
package structure

import java.io.File
import java.net.URI

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{ DataNode, ProjectKeys }
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import scala.collection.mutable

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class StatefulProject(val base: URI, var name: String) extends Project with Cloneable {
  private val modules0 = mutable.Set.empty[StatefulModule]
  private val libraries0 = mutable.Set.empty[StatefulLibrary]

  def addModule(id: Module.Id, base: File) =
    modules0.find(_.id == id).getOrElse {
      val module = new StatefulModule(base, id, id)
      modules0 += module
      module
    }

  def removeModule(id: Module.Id) =
    modules0.find(_.id == id).foreach(modules0 -= _)

  def modules = modules0.toSet

  def addLibrary(id: Library.Id): Library =
    libraries0.find(_.id == id).getOrElse {
      val lib = new StatefulLibrary(id)
      libraries0 += lib
      lib
    }

  def removeLibrary(id: Library.Id): Unit =
    libraries0.find(_.id == id).foreach(libraries0 -= _)

  def libraries = libraries0.toSet

  def copy: Project = this.clone.asInstanceOf[Project]
}

class StatefulModule(val base: File, val id: Module.Id, var name: String) extends Module {
  private val paths0 = mutable.Set.empty[Path]
  private val dependencies0 = mutable.Set.empty[Dependency]

  def addPath(path: Path) =
    paths0 += path

  def removePath(path: Path) =
    paths0 -= path

  def paths = paths0.toSet

  def addDependency(dependency: Dependency) =
    dependencies0 += dependency

  def removeDependency(dependency: Dependency) =
    dependencies0 -= dependency

  def dependencies = dependencies0.toSet
}

class StatefulLibrary(val id: Library.Id) extends Library {
  private val artifacts0 = mutable.Set.empty[Artifact]

  def addArtifact(artifact: Artifact) =
    artifacts0 += artifact

  def artifacts = artifacts0.toSet
}
