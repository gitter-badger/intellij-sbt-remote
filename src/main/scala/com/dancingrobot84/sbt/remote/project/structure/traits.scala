package com.dancingrobot84.sbt.remote.project.structure

import java.io.File
import java.net.URI

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

  def addModule(id: String, base: File): Module
  def findModule(id: String): Option[Module]
  def removeModule(id: String): Unit

  def addLibrary(id: LibraryId): Library
  def findLibrary(id: LibraryId): Option[Library]
  def removeLibrary(id: LibraryId): Unit

  def addDependency(moduleId: String, dependency: Dependency): Unit
  def removeDependency(moduleId: String, dependency: Dependency): Unit

  def copy: Project
}

trait Module {
  val base: File
  var id: String
  var name: String

  def addPath(path: Path): Unit
  def removePath(path: Path): Unit
}

trait Library {
  val id: LibraryId

  def addArtifact(artifact: Artifact): Unit
}

case class LibraryId(organization: String, name: String, version: String) {
  override def toString = s"$organization:$name:$version"
}

object LibraryId {
  def unmanagedJarsLibraryId(moduleId: String, configuration: Configuration) =
    LibraryId("unmanagedJars", moduleId, configuration.toString.toLowerCase)
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
  case class Library(id: LibraryId, configuration: Configuration) extends Dependency
  case class Module(id: String, configuration: Configuration) extends Dependency
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

