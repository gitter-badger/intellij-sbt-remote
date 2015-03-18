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

  def createProjectSettingsControl(settings: ProjectSettings) = new controls.ProjectSettingsControl(settings)

  def createSystemSettingsControl(settings: SystemSettings) = new controls.SystemSettingsControl(settings)

  def newProjectSettings() = ProjectSettings()

  def getId = "sbt.remote.project.settings.configurable"

  def getHelpTopic = null
}
