package com.dancingrobot84.sbt.remote
package external

import java.io.File

import com.dancingrobot84.sbt.remote.project.extractors._
import com.dancingrobot84.sbt.remote.project.structure.{Project, ProjectRef, StatefulProject}
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, DataNode}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import sbt.client.SbtClient
import sbt.protocol.{LogMessage, LogStdErr, LogStdOut, LogEvent}
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

    def onConnect(client: SbtClient): Unit = {
      logger.info("Retrieving structure")

      client.handleEvents {
        case logE : LogEvent => logE.entry match {
          case LogStdOut(_) | LogStdErr(_) | LogMessage(_, _) =>
            logger.info(logE.entry.message)
          case _ =>
        }
        case _ =>
      }

      val projectRef = new ProjectRef {
        var project: Project = new StatefulProject(projectFile.getCanonicalFile.toURI, projectFile.getName)
      }

      val extraction = for {
        _ <- new DirectoriesExtractor().attach(client, projectRef, Log)._1
        _ <- new InternalDependenciesExtractor().attach(client, projectRef, Log)._1
        _ <- new ExternalDependenciesExtractor().attach(client, projectRef, Log)._1
      } yield Unit

      import DataNodeConversions._
      extraction.onComplete {
        case Success(_)   => projectPromise.success(projectRef.project.toDataNode)
        case Failure(exc) => projectPromise.failure(new ExternalSystemException(exc))
      }
    }

    logger.info("Connecting to SBT server")

    connector.open(onConnect, { (reconnect, reason) =>
      if (reconnect)
        logger.warn(reason)
      else
        projectPromise.failure(new ExternalSystemException(reason))
    })

    Await.result(projectPromise.future, Duration.Inf)
  }

  def cancelTask(id: ExternalSystemTaskId,
                 listener: ExternalSystemTaskNotificationListener) = true
}
