package com.dancingrobot84.sbt.remote
package external

import com.dancingrobot84.sbt.remote.Logger.Level
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.{ ExternalSystemTaskId, ExternalSystemTaskNotificationListener }
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.module.{ Module, ModuleManager }
import com.intellij.openapi.project.Project
import sbt.client.SbtClient
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future, Promise }

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class TaskManager
    extends ExternalSystemTaskManager[ExecutionSettings] {

  import TaskManager._

  private var executor: Option[TasksExecutor] = None

  override def executeTasks(id: ExternalSystemTaskId,
                            taskNames: java.util.List[String],
                            projectPath: String,
                            settings: ExecutionSettings,
                            vmOptions: java.util.List[String],
                            scriptParameters: java.util.List[String],
                            debuggerSetup: String,
                            listener: ExternalSystemTaskNotificationListener): Unit = {

    val runAsIs = scriptParameters.contains("as-is")
    val moduleName = Option(id.findProject)
      .flatMap(findModuleByPath(_, projectPath))
      .flatMap(getModuleName)

    val logger = new Logger {
      def log(msg: String, level: Logger.Level, cause: Option[Throwable]): Unit = {
        if (msg.startsWith("Read from stdout:")) // TODO: write SBT guys about duplicating messages
          return
        val isStdOut = level match {
          case Level.Debug | Level.Info => true
          case Level.Error | Level.Warn => false
        }
        val loggerLevel = if (level == Level.Debug) "" else s"[${level.toString.toLowerCase}] "
        listener.onTaskOutput(id, s"$loggerLevel$msg\n", isStdOut)
      }
    }

    if (!runAsIs && moduleName.isEmpty)
      throw new ExternalSystemException("Project scope for current configuration is not set")

    executor = Some(new TasksExecutor(
      projectPath, if (runAsIs) None else moduleName,
      taskNames.asScala, settings, logger))
    executor.foreach(e => Await.ready(e.run(), Duration.Inf))
  }

  private def findModuleByPath(project: Project, path: String): Option[Module] = {
    Option(ModuleManager.getInstance(project)).flatMap(_.getModules.find { module =>
      Option(module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY)).fold(false)(_ == path)
    })
  }

  private def getModuleName(module: Module): Option[String] =
    Option(module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_ID_KEY))

  override def cancelTask(id: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener) =
    executor.fold(true)(_.cancel())
}

object TaskManager {
  sealed trait CurrentTask
  case object EmptyCurrentTask extends CurrentTask
  case class ConcreteCurrentTask(id: Long, name: String) extends CurrentTask

  final class TasksExecutor(projectPath: String,
                            moduleName: Option[String],
                            taskNames: Seq[String],
                            settings: ExecutionSettings,
                            logger: Logger) {

    private var tasks = moduleName match {
      case Some(m) => taskNames.map(t => s"$m/$t")
      case None    => taskNames
    }
    private val isDonePromise = Promise[Unit]()
    private val shouldCancelPromise = Promise[Unit]()

    def run(): Future[Unit] = {
      val connector = sbtConnectorFor(projectPath)
      var currentTask: CurrentTask = EmptyCurrentTask

        def onConnect(client: SbtClient): Unit = {
          client.handleEvents {
            case logE: LogEvent => logE.entry match {
              case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
                logger.debug(logE.entry.message)
              case LogMessage("info", _) =>
                logger.info(logE.entry.message)
              case LogMessage("warn", _) =>
                logger.warn(logE.entry.message)
              case LogMessage("error", _) =>
                logger.error(logE.entry.message)
              case _ => // ignore
            }
            case ExecutionStarting(id) => currentTask match {
              case ConcreteCurrentTask(taskId, name) if taskId == id =>
                logger.info(s"'$name' is starting")
              case _ =>
            }
            case ExecutionSuccess(id) => currentTask match {
              case ConcreteCurrentTask(taskId, name) if taskId == id =>
                logger.info(s"'$name' is finished successfully")
                executeNextTask()
              case _ =>
            }
            case ExecutionFailure(id) => currentTask match {
              case ConcreteCurrentTask(taskId, name) if taskId == id =>
                val msg = s"'$name' failed. Aborting."
                logger.error(msg)
                isDonePromise.tryFailure(new ExternalSystemException(msg))
              case _ =>
            }
            case _ => // ignore
          }

          shouldCancelPromise.future.onFailure {
            case exc: Exception =>
              currentTask.synchronized {
                tasks = Seq.empty
                currentTask match {
                  case ConcreteCurrentTask(id, _) => client.cancelExecution(id).collect {
                    case false => isDonePromise.failure(exc)
                  }
                  case _ => isDonePromise.failure(exc)
                }
              }
          }

            def executeNextTask(): Unit = currentTask.synchronized {
              if (tasks.isEmpty) {
                isDonePromise.trySuccess(Unit)
              } else {
                val task = tasks.head
                tasks = tasks.tail
                val id = Await.result(client.requestExecution(task, None), Duration.Inf)
                currentTask = ConcreteCurrentTask(id, task)
              }
            }

          executeNextTask()
        }

      connector.open(onConnect, { (reconnect, reason) =>
        if (reconnect)
          logger.warn(reason)
        else
          isDonePromise.tryFailure(new ExternalSystemException(reason))
      })

      isDonePromise.future
    }

    def cancel(): Boolean = {
      shouldCancelPromise.tryFailure(new ExternalSystemException("Task execution was cancelled"))
      isDonePromise.isCompleted
    }
  }
}