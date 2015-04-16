package com.dancingrobot84.sbt.remote
package external

import com.intellij.notification.{ Notification, NotificationType, Notifications }
import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskType }

/**
 * @author Nikolay Obedin
 * @since 4/15/15.
 */
class TaskNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean): Unit =
    if (id.getType == ExternalSystemTaskType.RESOLVE_PROJECT &&
      id.getProjectSystemId == Id) {
      Notifications.Bus.notify(new Notification(Bundle("sbt.remote.id"), Bundle("sbt.remote.name"), text, NotificationType.WARNING))
    }
}
