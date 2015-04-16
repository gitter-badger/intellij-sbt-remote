package com.dancingrobot84.sbt.remote
package external

import java.net.URL

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.{ externalSystem, project => idea }

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
final class ExternalSystemManager
    extends externalSystem.ExternalSystemManager[ProjectSettings, ProjectSettingsListener, SystemSettings, LocalSettings, ExecutionSettings]
    with externalSystem.ExternalSystemConfigurableAware {

  override def getSystemId = Id

  override def getSettingsProvider = SystemSettings.apply _

  override def getLocalSettingsProvider = LocalSettings.apply _

  override def getProjectResolverClass = classOf[ProjectResolver]

  override def getTaskManagerClass = classOf[TaskManager]

  override def getExternalProjectDescriptor = new OpenProjectFileChooserDescriptor(true) {
    override def isFileVisible(file: VirtualFile, showHidden: Boolean): Boolean =
      super.isFileVisible(file, showHidden) &&
        (file.isDirectory || file.getName.endsWith(".sbt"))

    override def isFileSelectable(file: VirtualFile): Boolean =
      super.isFileSelectable(file) && ImportUtil.canImportFrom(file)
  }

  override def getExecutionSettingsProvider = { (project: idea.Project, path: String) =>
    val projectSettings = Option(SystemSettings(project).getLinkedProjectSettings(path))
      .getOrElse(ProjectSettings())
    new ExecutionSettings(projectSettings.resolveClassifiers,
      projectSettings.resolveSbtClassifiers)
  }

  override def enhanceLocalProcessing(urls: java.util.List[URL]): Unit = {}

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {}

  override def getConfigurable(project: idea.Project): Configurable = {
    new ExternalSystemConfigurable(project)
  }
}
