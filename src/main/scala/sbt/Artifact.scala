package sbt

import sbt.serialization._

final case class Artifact(name: String,
                          `type`: String,
                          extension: String,
                          classifier: Option[String],
                          configurations: Iterable[Configuration])

object Artifact {
  private val optStringPickler = implicitly[Pickler[Option[String]]]
  private val optStringUnpickler = implicitly[Unpickler[Option[String]]]
  private val vectorConfigurationPickler = implicitly[Pickler[Vector[Configuration]]]
  private val vectorConfigurationUnpickler = implicitly[Unpickler[Vector[Configuration]]]

  implicit val pickler: Pickler[Artifact] = new Pickler[Artifact] {
    val tag = implicitly[FastTypeTag[Artifact]]
    val stringTag = implicitly[FastTypeTag[String]]
    val optionStringTag = implicitly[FastTypeTag[Option[String]]]
    val vectorConfigurationTag = implicitly[FastTypeTag[Vector[Configuration]]]
    val stringStringMapTag = implicitly[FastTypeTag[Map[String, String]]]
    def pickle(a: Artifact, builder: PBuilder): Unit = {
      builder.pushHints()
      builder.hintTag(tag)
      builder.beginEntry(a)
      builder.putField("name", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.name, b)
      })
      builder.putField("type", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.`type`, b)
      })
      builder.putField("extension", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.extension, b)
      })
      builder.putField("classifier", { b =>
        b.hintTag(optionStringTag)
        optStringPickler.pickle(a.classifier, b)
      })
      builder.putField("configurations", { b =>
        b.hintTag(vectorConfigurationTag)
        vectorConfigurationPickler.pickle(a.configurations.toVector, b)
      })
      builder.endEntry()
      builder.popHints()
    }
  }
  implicit val unpickler: Unpickler[Artifact] = new Unpickler[Artifact] {
    val tag = implicitly[FastTypeTag[Artifact]]
    def unpickle(tpe: String, reader: PReader): Any = {
      reader.pushHints()
      // reader.hintTag(tag)
      reader.beginEntry()
      val name = stringPickler.unpickleEntry(reader.readField("name")).asInstanceOf[String]
      val tp = stringPickler.unpickleEntry(reader.readField("type")).asInstanceOf[String]
      val extension = stringPickler.unpickleEntry(reader.readField("extension")).asInstanceOf[String]
      val classifier = optStringUnpickler.unpickleEntry(reader.readField("classifier")).asInstanceOf[Option[String]]
      val configurations = vectorConfigurationUnpickler.unpickleEntry(reader.readField("configurations")).asInstanceOf[Vector[Configuration]]
      val result = Artifact(name, tp, extension, classifier, configurations)
      reader.endEntry()
      reader.popHints()
      result
    }
  }
}

