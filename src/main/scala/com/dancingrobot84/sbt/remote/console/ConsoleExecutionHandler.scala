package com.dancingrobot84.sbt.remote.console

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager
import com.intellij.execution.console.{ BaseConsoleExecuteActionHandler, LanguageConsoleView }
import com.intellij.openapi.project.Project
import sbt.protocol.{ ExecutionFailure, ExecutionSuccess }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

/**
 * @author Nikolay Obedin
 * @since 4/16/15.
 */
class ConsoleExecutionHandler(project: Project) extends BaseConsoleExecuteActionHandler(false) {

  private var isTaskDonePromise: Option[Promise[Unit]] = None
  private var cancelPromise: Option[Promise[Unit]] = None

  def cancel(): Unit = cancelPromise.foreach(_.tryFailure(null))

  def isCancellable(): Boolean = cancelPromise.fold(false)(!_.isCompleted)

  override def isEmptyCommandExecutionAllowed: Boolean = false

  override def execute(text: String, console: LanguageConsoleView): Unit = {
    isTaskDonePromise = Some(Promise())
    cancelPromise = Some(Promise())

    console.setEditable(false)
    val subscription = SbtServerConnectionManager().makeSbtConnectorFor(project.getBasePath).open({ client =>
      for {
        id0 <- client.requestExecution(text, None)
      } {
        cancelPromise.foreach(_.future.onFailure {
          case _ => client.cancelExecution(id0).onSuccess {
            case false => isTaskDonePromise.foreach(_.trySuccess(Unit))
          }
        })
        client.handleEvents {
          case ExecutionSuccess(id) if id == id0 =>
            isTaskDonePromise.foreach(_.trySuccess(Unit))
          case ExecutionFailure(id) if id == id0 =>
            isTaskDonePromise.foreach(_.trySuccess(Unit))
          case _ => // do nothing
        }
      }
    })

    isTaskDonePromise.foreach(_.future.onComplete { _ =>
      console.setEditable(true)
      subscription.cancel()
    })
  }
}

