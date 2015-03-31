package com.dancingrobot84.sbt.remote
package external

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener }
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.module.{ Module, ModuleManager }
import com.intellij.openapi.project.Project
import sbt.client.{ SbtClient, SbtConnector }
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class TaskManager
    extends ExternalSystemTaskManager[ExecutionSettings] {

  private var donePromise: Option[Promise[Unit]] = None
  private var connector: Option[SbtConnector] = None

  override def executeTasks(id: ExternalSystemTaskId,
                            taskNames: java.util.List[String],
                            projectPath: String,
                            settings: ExecutionSettings,
                            vmOptions: java.util.List[String],
                            scriptParameters: java.util.List[String],
                            debuggerSetup: String,
                            listener: ExternalSystemTaskNotificationListener): Unit =
    findModuleByPath(id.findProject(), projectPath).foreach { module =>
      val moduleName = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_ID_KEY)
      val tasks = taskNames.asScala.map(t => s"${moduleName}/$t")
      donePromise = Some(Promise[Unit])
      connector = Option(sbtConnectorFor(id.findProject().getBasePath))

      val logger = new Logger {
        def log(msg: String, level: Logger.Level, cause: Option[Throwable]): Unit =
          listener.onTaskOutput(id, msg + "\n", true)
      }

        def onConnect(client: SbtClient): Unit = {
          client.handleEvents {
            case logE: LogEvent => logE.entry match {
              case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) |
                   LogMessage("info", _) | LogMessage("warn", _) |
                   LogMessage("error", _) =>
                if (!logE.entry.message.startsWith("Read from stdout:"))
                  logger.info(logE.entry.message)
              case _ =>
//                logger.info(logE.toString)
            }
            case ExecutionWaiting(id, _, _) =>
              logger.info(s"Task $id is waiting to be started")
            case ExecutionStarting(id) =>
              logger.info(s"Task $id is starting")
            case ExecutionSuccess(id) =>
              logger.info(s"Task $id finished successfully")
            case ExecutionFailure(id) =>
              logger.info(s"Task $id failed")
            case a =>
//              logger.info(a.toString)
          }

          tasks.map(t => client.requestExecution(t, None))
        }

      connector.foreach(_.open(onConnect, { (reconnect, reason) =>
        if (reconnect)
          logger.warn(reason)
        else
          donePromise.foreach(_.tryFailure(new ExternalSystemException(reason)))
      }))

      donePromise.map(p => Await.ready(p.future, Duration.Inf)).getOrElse(Unit)
    }

  private def findModuleByPath(project: Project, path: String): Option[Module] = {
    Option(ModuleManager.getInstance(project)).flatMap(_.getModules.find { module =>
      Option(module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY)).fold(false)(_ == path)
    })
  }

  override def cancelTask(id: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener) = {
    donePromise.foreach(_.tryFailure(new Error("Cancelled")))
    donePromise.fold(true)(_.isCompleted)
  }

}
