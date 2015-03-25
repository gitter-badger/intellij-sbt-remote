package sbt

import sbt.serialization._

final class ConfigurationReport(val configuration: String,
                                val modules: Seq[ModuleReport])
object ConfigurationReport {
  implicit val pickler: Pickler[ConfigurationReport] with Unpickler[ConfigurationReport] = PicklerUnpickler.generate[ConfigurationReport]
}
