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
import sbt.protocol._
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

  import ProjectResolver._

  private var executor: Option[ResolutionExecutor] = None

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreview: Boolean,
                                  settings: ExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val logger = new Logger {
      def log(msg: String, level: Logger.Level, cause: Option[Throwable]): Unit = {
        listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, msg))
        if (level == Logger.Level.Warn || level == Logger.Level.Error)
          listener.onTaskOutput(id, msg, false)
      }
    }

    executor = Some(new ResolutionExecutor(projectPath, settings, isPreview, logger))
    executor.fold(null.asInstanceOf[DataNode[ProjectData]])(e => Await.result(e.run(), Duration.Inf))
  }

  override def cancelTask(id: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    executor.fold(false)(_.cancel())
}

object ProjectResolver {
  final class ResolutionExecutor(projectPath: String,
                                 settings: ExecutionSettings,
                                 isPreview: Boolean,
                                 logger: Logger) {

    private val projectPromise = Promise[DataNode[ProjectData]]()
    private val cancelPromise = Promise[Unit]()

    cancelPromise.future.onFailure {
      case exc: Exception => projectPromise.tryFailure(exc)
    }

    def run(): Future[DataNode[ProjectData]] = {
        def onConnect(client: SbtClient): Unit = {
          client.handleEvents {
            case logE: LogEvent => logE.entry match {
              case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
                Log.warn(logE.entry.message)
              case LogMessage(level, _) =>
                Log.warn(s"[$level] ${logE.entry.message}")
              case _ =>
            }
            case _ =>
          }

          val projectFile = new File(projectPath)
          val projectRef = new ProjectRef {
            var project: Project = new StatefulProject(projectFile.getCanonicalFile.toURI, projectFile.getName)
          }

          val basicsExtractor = new BasicsExtractor with SynchronizedContext

          val mandatoryExtractors = Seq(
            new InternalDependenciesExtractor with SynchronizedContext,
            new TasksExtractor with SynchronizedContext)

          val optionalExtractors =
            if (isPreview)
              Seq.empty
            else if (settings.resolveClassifiers)
              Seq(new ExternalDependenciesExtractor with SynchronizedContext,
                new ClassifiersExtractor with SynchronizedContext)
            else
              Seq(new ExternalDependenciesExtractor with SynchronizedContext)

          val extraction = for {
            _ <- basicsExtractor.attachOnce(client, projectRef, logger)
            _ <- Future.sequence(mandatoryExtractors.map(_.attachOnce(client, projectRef, logger)))
            _ <- Future.sequence(optionalExtractors.map(_.attachOnce(client, projectRef, logger)))
          } yield Unit

          extraction.onComplete { _ =>
            basicsExtractor.detach()
            mandatoryExtractors.foreach(_.detach())
            optionalExtractors.foreach(_.detach())
          }

          import DataNodeConversions._
          extraction.onComplete {
            case Success(_)   => projectPromise.tryComplete(Try(projectRef.project.toDataNode))
            case Failure(exc) => projectPromise.tryFailure(new ExternalSystemException(exc))
          }
        }

      logger.info("Connecting to SBT server...")

      val subscription = sbtConnectorFor(projectPath).open(onConnect, { (reconnect, reason) =>
        if (reconnect)
          logger.warn(reason)
        else
          projectPromise.tryFailure(new ExternalSystemException(reason))
      })

      projectPromise.future.onComplete(_ => subscription.cancel())
      projectPromise.future
    }

    def cancel(): Boolean = {
      cancelPromise.tryFailure(new ImportCanceledException("Import cancelled"))
      projectPromise.isCompleted
    }
  }
}
