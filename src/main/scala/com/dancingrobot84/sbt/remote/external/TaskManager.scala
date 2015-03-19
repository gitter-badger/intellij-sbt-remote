package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskId, ExternalSystemTaskNotificationListener }
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class TaskManager
    extends ExternalSystemTaskManager[ExecutionSettings] {

  override def executeTasks(id: ExternalSystemTaskId,
                            taskNames: java.util.List[String],
                            projectPath: String,
                            settings: ExecutionSettings,
                            vmOptions: java.util.List[String],
                            scriptParameters: java.util.List[String],
                            debuggerSetup: String,
                            listener: ExternalSystemTaskNotificationListener) = {}

  override def cancelTask(id: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener) = false

}
