package com.dancingrobot84.sbt.remote.project.structure

import java.io.File
import java.net.URI

/**
 * @author: Nikolay Obedin
 * @since: 2/12/15.
 */

trait Project {
  val base: URI
  var name: String

  def addModule(name: String, base: File): Module
  def findModule(name: String): Option[Module]
  def removeModule(name: String): Unit
}

trait Module {
  val base: File
  var name: String

  def addPath(path: Path): Unit
  def removePath(path: Path): Unit
}

trait Library {
  val id: LibraryId
  val artifacts: Seq[Artifact]
}

case class LibraryId(organization: String, name: String, version: String)

sealed trait Path
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
}

sealed trait Dependency
object Dependency {
  case class Library(id: LibraryId, configuration: Configuration)
  case class Module(base: File, configuration: Configuration)
}

sealed trait Configuration
object Configuration {
  object Test     extends Configuration
  object Compile  extends Configuration
  object Runtime  extends Configuration
  object Provided extends Configuration
}

sealed trait Artifact
object Artifact {
  case class Binary(file: File) extends Artifact
  case class Source(file: File) extends Artifact
  case class Doc(file: File) extends Artifact
}

