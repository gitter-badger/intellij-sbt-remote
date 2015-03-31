package com.dancingrobot84.sbt.remote
package external

import java.io.File

import com.dancingrobot84.sbt.remote.project.extractors._
import com.dancingrobot84.sbt.remote.project.structure.{ Project, ProjectRef, StatefulProject }
import com.intellij.openapi.externalSystem.model.{ ExternalSystemException, DataNode }
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener }
import com.intellij.openapi.externalSystem.service.ImportCanceledException
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import sbt.client.{ SbtConnector, SbtClient }
import sbt.protocol.{ LogMessage, LogStdErr, LogStdOut, LogEvent }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Future, Await, Promise }
import scala.util.{ Try, Failure, Success }

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class ProjectResolver
    extends ExternalSystemProjectResolver[ExecutionSettings] {

  private var projectPromise: Option[Promise[DataNode[ProjectData]]] = None
  private var connector: Option[SbtConnector] = None

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreview: Boolean,
                                  settings: ExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    connector = Some(sbtConnectorFor(projectPath))
    projectPromise = Some(Promise())

    val logger = new Logger {
      def log(msg: String, level: Logger.Level, cause: Option[Throwable]): Unit =
        listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, msg))
    }

      def onConnect(client: SbtClient): Unit = {
        logger.info("Retrieving structure")

        client.handleEvents {
          case logE: LogEvent => logE.entry match {
            case LogStdOut(_) | LogStdErr(_) | LogMessage(_, _) =>
              logger.info(logE.entry.message)
            case _ =>
          }
          case _ =>
        }

        val projectFile = new File(projectPath)
        val projectRef = new ProjectRef {
          var project: Project = new StatefulProject(projectFile.getCanonicalFile.toURI, projectFile.getName)
        }

        val extractors =
          if (isPreview)
            Seq(new InternalDependenciesExtractor with SynchronizedContext)
          else if (settings.resolveClassifiers)
            Seq(new InternalDependenciesExtractor with SynchronizedContext,
              new ExternalDependenciesExtractor with SynchronizedContext,
              new ClassifiersExtractor with SynchronizedContext)
          else
            Seq(new InternalDependenciesExtractor with SynchronizedContext,
              new ExternalDependenciesExtractor with SynchronizedContext)

        val extraction = for {
          _ <- (new DirectoriesExtractor with SynchronizedContext).attach(client, projectRef, Log)._1
          _ <- Future.sequence(extractors.map(_.attach(client, projectRef, Log)._1))
        } yield Unit

        import DataNodeConversions._
        extraction.onComplete {
          case Success(_)   => projectPromise.foreach(_.tryComplete(Try(projectRef.project.toDataNode)))
          case Failure(exc) => projectPromise.foreach(_.tryFailure(new ExternalSystemException(exc)))
        }
      }

    logger.info("Connecting to SBT server")

    connector.foreach(_.open(onConnect, { (reconnect, reason) =>
      if (reconnect)
        logger.warn(reason)
      else
        projectPromise.foreach(_.tryFailure(new ExternalSystemException(reason)))
    }))

    projectPromise.map(p => Await.result(p.future, Duration.Inf)).getOrElse(null)
  }

  override def cancelTask(id: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener) = {
    projectPromise.foreach(_.tryFailure(new IllegalStateException("Import cancelled")))
    connector.foreach(_.close())
    projectPromise.map(_.isCompleted).getOrElse(true)
  }
}
