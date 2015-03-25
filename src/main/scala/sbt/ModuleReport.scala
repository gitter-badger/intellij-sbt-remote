package sbt

import java.io.File

import sbt.serialization._

final class ModuleReport(val module: ModuleID,
                         val artifacts: Seq[(Artifact, File)],
                         val missingArtifacts: Seq[Artifact],
                         val status: Option[String],
                         val evicted: Boolean,
                         val configurations: Seq[String])

object ModuleReport {
  implicit val pickler: Pickler[ModuleReport] with Unpickler[ModuleReport] = PicklerUnpickler.generate[ModuleReport]
}