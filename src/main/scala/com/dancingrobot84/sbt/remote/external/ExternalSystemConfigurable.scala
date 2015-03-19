package com.dancingrobot84.sbt.remote
package external

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.{ project => idea }

/**
 * @author Nikolay Obedin
 * @since 3/18/15.
 */
class ExternalSystemConfigurable(project: idea.Project)
    extends AbstractExternalSystemConfigurable[ProjectSettings, ProjectSettingsListener, SystemSettings](project, Id) {

  override def createProjectSettingsControl(settings: ProjectSettings) =
    new controls.ProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: SystemSettings) =
    new controls.SystemSettingsControl(settings)

  override def newProjectSettings() = ProjectSettings()

  override def getId = "sbt.remote.project.settings.configurable"

  override def getHelpTopic = null
}
