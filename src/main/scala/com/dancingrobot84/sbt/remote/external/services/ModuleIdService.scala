package com.dancingrobot84.sbt.remote
package external
package services

import java.net.URI
import java.util

import com.dancingrobot84.sbt.remote.external.services.ModuleIdService.ModuleIdData
import com.intellij.openapi.externalSystem.model.{ DataNode, ProjectKeys, Key, ProjectSystemId }
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import com.intellij.openapi.externalSystem.service.project.{ ProjectStructureHelper, PlatformFacade }
import com.intellij.openapi.project.Project

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/21/15.
 */
class ModuleIdService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
    extends ProjectDataService[ModuleIdData, Nothing] {

  override def getTargetDataKey: Key[ModuleIdData] = ModuleIdService.DataKey

  override def removeData(toRemove: util.Collection[_ <: Nothing], project: Project, synchronous: Boolean): Unit = {}

  override def importData(toImport: util.Collection[DataNode[ModuleIdData]], project: Project, synchronous: Boolean): Unit =
    toImport.asScala.foreach { dataNode =>
      val moduleData = dataNode.getData(getTargetDataKey)
      for {
        systemSettings <- Option(SystemSettings(project))
        projectSettings <- Option(systemSettings.getLinkedProjectSettings(moduleData.projectBase))
      } {
        val qualifiedName = s"{${moduleData.build}}${moduleData.id}"
        projectSettings.moduleNameToQualifiedNameMap.put(moduleData.name, qualifiedName)
      }
    }
}

object ModuleIdService {
  case class ModuleIdData(owner: ProjectSystemId, projectBase: String, name: String, id: String, build: URI)
    extends AbstractExternalEntityData(owner)

  val DataKey: Key[ModuleIdData] = new Key(classOf[ModuleIdData].getName, ProjectKeys.MODULE.getProcessingWeight + 1)
}
