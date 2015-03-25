package sbt

import sbt.serialization._

final case class Configuration(name: String,
                               description: String,
                               isPublic: Boolean,
                               extendsConfigs: List[Configuration],
                               transitive: Boolean)

object Configuration {
  implicit val pickler: Pickler[Configuration] with Unpickler[Configuration] = PicklerUnpickler.generate[Configuration]
}