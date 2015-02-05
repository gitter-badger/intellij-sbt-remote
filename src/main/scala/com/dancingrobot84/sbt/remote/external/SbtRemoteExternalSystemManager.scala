package com.dancingrobot84.sbt.remote.external

import java.net.URL

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
final class SbtRemoteExternalSystemManager
  extends ExternalSystemManager[ProjectSettings, ProjectSettingsListener, SystemSettings, LocalSettings, ExecutionSettings] {

  def getSystemId = Id

  def getSettingsProvider = SystemSettings.apply _

  def getLocalSettingsProvider = LocalSettings.apply _

  def getProjectResolverClass = classOf[ProjectResolver]

  def getTaskManagerClass = classOf[TaskManager]

  def getExternalProjectDescriptor = new OpenProjectFileChooserDescriptor(true) {
    override def isFileVisible(file: VirtualFile, showHidden: Boolean): Boolean =
      super.isFileVisible(file, showHidden) &&
        (file.isDirectory || file.getName.endsWith(".sbt"))

    override def isFileSelectable(file: VirtualFile): Boolean =
      super.isFileSelectable(file) && ImportUtil.canImportFrom(file)
  }

  def getExecutionSettingsProvider = { (project: Project, path: String) =>
    new ExecutionSettings
  }

  def enhanceLocalProcessing(urls: java.util.List[URL]): Unit = {}

  def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {}
}
