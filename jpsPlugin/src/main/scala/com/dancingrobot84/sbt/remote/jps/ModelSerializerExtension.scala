package com.dancingrobot84.sbt.remote.jps

import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jdom.filter.ElementFilter
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.{ JpsProjectExtensionSerializer, JpsModelSerializerExtension }
import java.util

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/20/15.
 */
class ModelSerializerExtension extends JpsModelSerializerExtension {
  override def getProjectExtensionSerializers: util.List[_ <: JpsProjectExtensionSerializer] = {
    val sbtRemoteSerializer = new JpsProjectExtensionSerializer("sbt-remote.xml", "SbtRemoteSystemSettings") {
      override def loadExtension(e: JpsProject, componentTag: Element): Unit = {
        componentTag.getDescendants(new ElementFilter("option")).asScala.find {
          c => Option(c.getAttribute("name")).fold(false)(_.getValue == "externalProjectPath")
        }.flatMap(e => Option(e.getAttribute("value")).map(_.getValue)).foreach { path =>
          val settings = new SbtRemoteProjectSettings(path)
          e.getContainer.setChild(SbtRemoteProjectSettings.Role, settings)
        }
      }

      override def saveExtension(e: JpsProject, componentTag: Element): Unit = {}
    }
    Seq(sbtRemoteSerializer).asJava
  }
}

