package com.dancingrobot84.sbt.remote
package external

import java.io.File

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class ProjectImportBuilder(projectDataManager: ProjectDataManager)
    extends AbstractExternalProjectImportBuilder[ImportControl](projectDataManager, new ImportControl, Id) {

  override def getName = Bundle("sbt.remote.name")

  override def getIcon = Bundle("sbt.remote.icon")

  override def doPrepare(context: WizardContext): Unit = {}

  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}

  override def getExternalProjectConfigToUse(file: File): File = file

  override def applyExtraSettings(context: WizardContext): Unit = {}

}
