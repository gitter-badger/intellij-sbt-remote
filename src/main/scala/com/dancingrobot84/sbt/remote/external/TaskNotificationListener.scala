package com.dancingrobot84.sbt.remote.external

import com.intellij.notification.{ NotificationType, Notification, Notifications }
import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskType, ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskNotificationListener }

/**
 * @author Nikolay Obedin
 * @since 4/15/15.
 */
class TaskNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean): Unit =
    if (id.getType == ExternalSystemTaskType.RESOLVE_PROJECT &&
      id.getProjectSystemId == Id) {
      Notifications.Bus.notify(new Notification("sbt-remote", "SBT Remote", text, NotificationType.WARNING))
    }
}
