package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.util.{ ExternalSystemSettingsControl, PaintAwarePanel }
import com.intellij.openapi.project.ProjectManager

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class ImportControl
    extends AbstractImportFromExternalSystemControl[ProjectSettings, ProjectSettingsListener, SystemSettings](
      Id, SystemSettings(ProjectManager.getInstance().getDefaultProject), ProjectSettings()) {

  def onLinkedProjectPathChange(newPath: String): Unit = {}

  def createProjectSettingsControl(settings: ProjectSettings) = new controls.ProjectSettingsControl(settings)

  def createSystemSettingsControl(settings: SystemSettings) = new controls.SystemSettingsControl(settings)
}
