package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class ProjectResolver
  extends ExternalSystemProjectResolver[ExecutionSettings] {

  def resolveProjectInfo(id: ExternalSystemTaskId,
                         projectPath: String,
                         isPreview: Boolean,
                         settings: ExecutionSettings,
                         listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = null

  def cancelTask(id: ExternalSystemTaskId,
                 listener: ExternalSystemTaskNotificationListener) = false
}
