package sbt

import java.io.File

import sbt.serialization._

/**
 * @author Nikolay Obedin
 * @since 3/25/15.
 */
final class UpdateReport(val cachedDescriptor: File,
                         val configurations: Seq[ConfigurationReport])

object UpdateReport {
  private val vectorConfigurationReportPickler = implicitly[Pickler[Vector[ConfigurationReport]]]
  private val vectorConfigurationReportUnpickler = implicitly[Unpickler[Vector[ConfigurationReport]]]

  implicit val pickler: Pickler[UpdateReport] with Unpickler[UpdateReport] = new Pickler[UpdateReport] with Unpickler[UpdateReport] {
    val tag = implicitly[FastTypeTag[UpdateReport]]
    val fileTag = implicitly[FastTypeTag[File]]
    val vectorConfigurationReportTag = implicitly[FastTypeTag[Vector[ConfigurationReport]]]
    def pickle(a: UpdateReport, builder: PBuilder): Unit = {
      builder.pushHints()
      builder.hintTag(tag)
      builder.beginEntry(a)
      builder.putField("cachedDescriptor", { b =>
        b.hintTag(fileTag)
        filePickler.pickle(a.cachedDescriptor, b)
      })
      builder.putField("configurations", { b =>
        b.hintTag(vectorConfigurationReportTag)
        vectorConfigurationReportPickler.pickle(a.configurations.toVector, b)
      })
      builder.endEntry()
      builder.popHints()
    }

    def unpickle(tpe: String, reader: PReader): Any = {
      reader.pushHints()
      reader.hintTag(tag)
      reader.beginEntry()
      val cachedDescriptor = filePickler.unpickleEntry(reader.readField("cachedDescriptor")).asInstanceOf[File]
      val configurations = vectorConfigurationReportUnpickler.unpickleEntry(reader.readField("configurations")).asInstanceOf[Vector[ConfigurationReport]]
      val result = new UpdateReport(cachedDescriptor, configurations)
      reader.endEntry()
      reader.popHints()
      result
    }
  }
}
