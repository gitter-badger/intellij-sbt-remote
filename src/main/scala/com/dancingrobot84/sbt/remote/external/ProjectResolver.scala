package com.dancingrobot84.sbt.remote
package external

import java.io.File

import com.dancingrobot84.sbt.remote.project.extractors._
import com.dancingrobot84.sbt.remote.project.structure.StatefulProject
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import sbt.protocol.LogEvent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success}

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
    val projectFile = new File(projectPath)
    val connector = sbtConnectorFor(projectFile)
    val projectPromise = Promise[DataNode[ProjectData]]()

    val logger = new Logger {
      def log(msg: String, level: Logger.Level, cause: Option[Throwable]): Unit =
        listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, msg))
    }

    logger.info("Connecting to SBT server")

    connector.open({ client =>
      logger.info("Retrieving structure")

      client.handleEvents {
        case logE : LogEvent =>
          logger.info(logE.entry.message)
        case _ =>
      }

      val project = new StatefulProject(projectFile.toURI, projectFile.getName)
      val (initFuture, _) = new SourceDirsExtractor().attach(Extractor.Context(client, project, Log))

      initFuture.onComplete {
        case Success(_)   =>
          new DependenciesExtractor().attach(Extractor.Context(client, project, Log))._1.onComplete { _ =>
            projectPromise.success(project.toDataNode)
          }
        case Failure(err) =>
          projectPromise.failure(err)
      }
    }, { (reconnect, reason) =>
      if (reconnect)
        logger.warn(reason)
      else
        projectPromise.failure(new Error(reason))
    })

    Await.result(projectPromise.future, Duration.Inf)
  }

  def cancelTask(id: ExternalSystemTaskId,
                 listener: ExternalSystemTaskNotificationListener) = true
}
