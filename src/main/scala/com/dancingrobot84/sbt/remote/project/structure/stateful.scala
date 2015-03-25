package com.dancingrobot84.sbt.remote
package project
package structure

import java.io.File
import java.net.URI

import scala.collection.mutable

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class StatefulProject(val base: URI, var name: String) extends Project with Cloneable {
  private val modules0 = mutable.Set.empty[StatefulModule]
  private val libraries0 = mutable.Set.empty[StatefulLibrary]

  override def addModule(id: Module.Id, base: File) =
    modules0.find(_.id == id).getOrElse {
      val module = new StatefulModule(base, id, id)
      modules0 += module
      module
    }

  override def removeModule(id: Module.Id) =
    modules0.find(_.id == id).foreach(modules0 -= _)

  override def modules = modules0.toSet

  override def addLibrary(id: Library.Id): Library =
    libraries0.find(_.id == id).getOrElse {
      val lib = new StatefulLibrary(id)
      libraries0 += lib
      lib
    }

  override def removeLibrary(id: Library.Id): Unit =
    libraries0.find(_.id == id).foreach(libraries0 -= _)

  override def libraries = libraries0.toSet

  override def copy: Project = this.clone.asInstanceOf[Project]
}

class StatefulModule(val base: File, val id: Module.Id, var name: String) extends Module {
  private val paths0 = mutable.Set.empty[Path]
  private val dependencies0 = mutable.Set.empty[Dependency]

  override def addPath(path: Path) =
    paths0 += path

  override def removePath(path: Path) =
    paths0 -= path

  override def paths = paths0.toSet

  override def addDependency(dependency: Dependency) =
    dependencies0 += dependency

  override def removeDependency(dependency: Dependency) =
    dependencies0 -= dependency

  override def dependencies = dependencies0.toSet
}

class StatefulLibrary(val id: Library.Id) extends Library {
  private val artifacts0 = mutable.Set.empty[Artifact]

  override def addArtifact(artifact: Artifact) =
    artifacts0 += artifact

  override def artifacts = artifacts0.toSet
}
