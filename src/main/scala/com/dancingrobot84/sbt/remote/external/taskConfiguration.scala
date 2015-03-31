package com.dancingrobot84.sbt.remote
package external

import javax.swing.{ JComponent, Icon }

import com.dancingrobot84.sbt.remote.external.controls.TaskRunConfigurationForm
import com.intellij.execution.configurations.{ RunConfiguration, ConfigurationFactory }
import com.intellij.openapi.externalSystem.service.execution.{ ExternalSystemRunConfiguration, AbstractExternalSystemRuntimeConfigurationProducer, AbstractExternalSystemTaskConfigurationType }
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * @author Nikolay Obedin
 * @since 3/27/15.
 */
class TaskConfigurationType extends AbstractExternalSystemTaskConfigurationType(Id) {
  override def getIcon: Icon = Bundle("sbt.remote.icon")

  override def getFactory: ConfigurationFactory = new ConfigurationFactory(this) {
    override def createTemplateConfiguration(project: Project): RunConfiguration =
      new TaskRunConfiguration(project, this)
  }

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(getFactory)
}

object TaskConfigurationType {
  def apply(): AbstractExternalSystemTaskConfigurationType =
    ExternalSystemUtil.findConfigurationType(Id)
}

class TaskConfigurationProducer extends AbstractExternalSystemRuntimeConfigurationProducer(TaskConfigurationType())

class TaskRunConfiguration(project: Project, configurationFactory: ConfigurationFactory)
    extends ExternalSystemRunConfiguration(Id, project, configurationFactory, "") {
  override def getConfigurationEditor: SettingsEditor[ExternalSystemRunConfiguration] =
    new TaskRunConfigurationEditor(project)
}

class TaskRunConfigurationEditor(project: Project) extends SettingsEditor[ExternalSystemRunConfiguration] {

  private val editorForm = new TaskRunConfigurationForm(project)

  override def createEditor(): JComponent = editorForm.getMainPanel

  override def resetEditorFrom(s: ExternalSystemRunConfiguration): Unit = {
    editorForm.setProjectPath(s.getSettings.getExternalProjectPath)
    editorForm.setTasks(s.getSettings.getTaskNames)
    editorForm.setRunInGlobalScope(s.getSettings.getScriptParameters.contains("global"))
  }

  override def applyEditorTo(s: ExternalSystemRunConfiguration): Unit = {
    s.getSettings.setExternalProjectPath(editorForm.getProjectPath);
    s.getSettings.setTaskNames(editorForm.getTasks)
    if (editorForm.getRunInGlobalScope)
      s.getSettings.setScriptParameters("global")
    else
      s.getSettings.setScriptParameters("")

    s.getSettings.setVmOptions("");
    s.getSettings.setTaskDescriptions(new java.util.ArrayList[String])
  }
}