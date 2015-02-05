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

  def getName = Bundle("sbt.remote.name")

  def getIcon = Bundle("sbt.remote.icon")

  def doPrepare(context: WizardContext): Unit = {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext): Unit = {}

}
