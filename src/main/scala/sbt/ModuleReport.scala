package sbt

import java.io.File

import sbt.serialization._

final case class ModuleReport(val module: ModuleID,
                              val artifacts: Seq[(Artifact, File)],
                              val evicted: Boolean)

object ModuleReport {
  implicit val pickler: Pickler[ModuleReport] with Unpickler[ModuleReport] = PicklerUnpickler.generate[ModuleReport]
}