package com.dancingrobot84.sbt.remote.external

import java.io.File

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import sbt.client.SbtConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

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
                         listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val connector = SbtConnector(configName = "idea",
                                 humanReadableName = "Intellij IDEA",
                                 directory = new File(projectPath))

    listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, "Connecting to SBT server..."))
    val p = Promise[DataNode[ProjectData]]()

    connector.open({ client =>
      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, "OK, connected, closing..."))
      Thread.sleep(2000)
      client.close()
      p.complete(Success(null))
    }, { (reconnecting, message) =>
      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message))
      Thread.sleep(2000)
      p.complete(Success(null))
    })

    Await.result(p.future, Duration.Inf)
  }

  def cancelTask(id: ExternalSystemTaskId,
                 listener: ExternalSystemTaskNotificationListener) = false
}
