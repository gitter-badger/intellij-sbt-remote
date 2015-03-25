package sbt

import sbt.serialization._

final case class ModuleID(organization: String,
                          name: String,
                          revision: String,
                          configurations: Option[String] = None)

object ModuleID {
  implicit val pickler: Pickler[ModuleID] with Unpickler[ModuleID] = PicklerUnpickler.generate[ModuleID]
}

