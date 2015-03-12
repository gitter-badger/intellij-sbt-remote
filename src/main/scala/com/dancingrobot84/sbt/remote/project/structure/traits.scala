package com.dancingrobot84.sbt.remote.project.structure

import java.io.File
import java.net.URI
import sbt.ModuleID

/**
 * @author: Nikolay Obedin
 * @since: 2/12/15.
 */

trait ProjectRef {
  var project: Project
}

trait Project {
  val base: URI
  var name: String

  def addModule(id: Module.Id, base: File): Module
  def findModule(id: Module.Id): Option[Module]
  def removeModule(id: Module.Id): Unit

  def addLibrary(id: Library.Id): Library
  def findLibrary(id: Library.Id): Option[Library]
  def removeLibrary(id: Library.Id): Unit

  def addDependency(moduleId: Module.Id, dependency: Dependency): Unit
  def removeDependency(moduleId: Module.Id, dependency: Dependency): Unit

  def copy: Project
}

trait Module {
  val base: File
  val id: Module.Id
  var name: String

  def addPath(path: Path): Unit
  def removePath(path: Path): Unit
}

object Module {
  type Id = String
}

trait Library {
  val id: Library.Id

  def addArtifact(artifact: Artifact): Unit
}

object Library {
  case class Id(organization: String, name: String, version: String, internalVersion: Int) {
    override def toString = s"$organization:$name:$version:$internalVersion"

    def ~=(otherId: Library.Id) =
      organization == otherId.organization &&
      name == otherId.name &&
      version == otherId.version
  }

  object Id {
    def forUnmanagedJars(moduleId: Module.Id, configuration: Configuration) =
      Id("unmanaged-jars", moduleId, configuration.toString.toLowerCase, 0)

    def fromSbtModuleId(moduleId: ModuleID) =
      Id(moduleId.organization, moduleId.name, moduleId.revision, 0)
  }
}

sealed trait Path {
  val base: File
}

object Path {
  case class Source(base: File) extends Path
  case class GenSource(base: File) extends Path
  case class TestSource(base: File) extends Path
  case class GenTestSource(base: File) extends Path
  case class Resource(base: File) extends Path
  case class GenResource(base: File) extends Path
  case class TestResource(base: File) extends Path
  case class GenTestResource(base: File) extends Path
  case class Exclude(base: File) extends Path
  case class Output(base: File) extends Path
  case class TestOutput(base: File) extends Path
}

sealed trait Dependency
object Dependency {
  import com.dancingrobot84.sbt.remote.project.structure.{Library => Lib, Module => Mod}
  case class Library(id: Lib.Id, configuration: Configuration) extends Dependency
  case class Module(id: Mod.Id, configuration: Configuration) extends Dependency
}

sealed trait Configuration
object Configuration {
  case object Test     extends Configuration
  case object Compile  extends Configuration
  case object Runtime  extends Configuration
  case object Provided extends Configuration

  // TODO: this is very naive, need proper configuration parser
  def fromString(confStr: String): Option[Configuration] = confStr match {
    case "test"     => Some(Configuration.Test)
    case "compile"  => Some(Configuration.Compile)
    case "runtime"  => Some(Configuration.Runtime)
    case "provided" => Some(Configuration.Provided)
    case _          => None
  }
}

sealed trait Artifact
object Artifact {
  case class Binary(file: File) extends Artifact
  case class Source(file: File) extends Artifact
  case class Doc(file: File) extends Artifact
}

