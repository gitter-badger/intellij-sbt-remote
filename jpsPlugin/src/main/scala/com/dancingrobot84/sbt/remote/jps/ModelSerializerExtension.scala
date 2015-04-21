package com.dancingrobot84.sbt.remote.jps

import java.util

import org.jdom.Element
import org.jdom.output.XMLOutputter
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.{JpsModelSerializerExtension, JpsProjectExtensionSerializer}

import scala.collection.JavaConverters._
import scala.xml._

/**
 * @author Nikolay Obedin
 * @since 4/20/15.
 */
class ModelSerializerExtension extends JpsModelSerializerExtension {
  override def getProjectExtensionSerializers: util.List[_ <: JpsProjectExtensionSerializer] = {
    val sbtRemoteSerializer = new JpsProjectExtensionSerializer("sbt-remote.xml", "SbtRemoteSystemSettings") {
      override def loadExtension(e: JpsProject, componentTag: Element): Unit = {
        val scalaXml = XML.loadString(new XMLOutputter().outputString(componentTag))
        val settings = ModelSerializerExtension.deserializeProjectSettings(scalaXml)
        settings.foreach(e.getContainer.setChild(SbtRemoteProjectSettings.Role, _))
      }
      override def saveExtension(e: JpsProject, componentTag: Element): Unit = {}
    }
    Seq(sbtRemoteSerializer).asJava
  }
}

object ModelSerializerExtension {
  def deserializeProjectSettings(from: Elem): Option[SbtRemoteProjectSettings] = {
    val options = (from \\ "ProjectSettings" \\ "option")
    for {
      projectPath <- options.find(_ \@ "name" == "externalProjectPath").map(_ \@ "value")
      moduleNameMap <- options.find(_ \@ "name" == "moduleNameToQualifiedNameMap").map { mapNode =>
        (mapNode \\ "entry").map { entryNode => (entryNode \@ "key", entryNode \@ "value")}.toMap
      }
    } yield new SbtRemoteProjectSettings(projectPath, moduleNameMap)
  }
}