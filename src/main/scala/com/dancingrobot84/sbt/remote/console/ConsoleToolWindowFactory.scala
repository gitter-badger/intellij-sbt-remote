package com.dancingrobot84.sbt.remote
package console

import java.awt.BorderLayout
import javax.swing.JPanel

import com.dancingrobot84.sbt.remote.project.components.SessionListener
import com.dancingrobot84.sbt.remote.project.components.SessionListener.LogListener
import com.intellij.execution.console._
import com.intellij.execution.impl.ConsoleViewImpl.ClearAllAction
import com.intellij.execution.{ ui => UI }
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ ToolWindow, ToolWindowFactory }
import sbt.protocol.{ ExecutionFailure, ExecutionSuccess }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

/**
 * @author Nikolay Obedin
 * @since 4/2/15.
 */
class ConsoleToolWindowFactory extends ToolWindowFactory {
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val consoleView = new ConsoleView(project)
    val toolbarActions = new DefaultActionGroup()
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false)

    val panel = new JPanel(new BorderLayout())
    panel.add(toolbar.getComponent, BorderLayout.WEST)
    panel.add(consoleView.getComponent, BorderLayout.CENTER)

      def addAction(action: AnAction): Unit = {
        Option(action.getShortcutSet).foreach(ss => action.registerCustomShortcutSet(ss, consoleView.getComponent))
        toolbarActions.add(action)
      }

    val executionHandler = new ConsoleExecutionHandler(project)

    addAction(new ConsoleExecuteAction(consoleView, executionHandler))

    addAction(new AnAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM))
      override def update(e: AnActionEvent): Unit =
        e.getPresentation.setEnabled(!consoleView.isEditable && executionHandler.isCancellable())
      override def actionPerformed(e: AnActionEvent): Unit =
        executionHandler.cancel()
    })

    addAction(new ClearAllAction() {
      override def update(e: AnActionEvent): Unit =
        e.getPresentation.setEnabled(consoleView.getHistoryViewer.getDocument.getTextLength > 0)
      override def actionPerformed(e: AnActionEvent): Unit =
        consoleView.clear()
    })

    toolbar.setTargetComponent(panel)
    toolWindow.getComponent.add(panel)
  }
}

class ConsoleView(project: Project) extends LanguageConsoleImpl(project, "SBT Remote REPL", PlainTextLanguage.INSTANCE) {
  getConsoleEditor.setOneLineMode(true)

  SessionListener(project).foreach { listener =>
    listener.addLogListener(new LogListener {
      import SessionListener._
      override def onMessage(message: Message): Unit = {
        ApplicationManagerEx.getApplicationEx.invokeLater(new Runnable {
          override def run(): Unit = {
            message match {
              case Message.Stdout(msg) =>
                print(msg + "\n", UI.ConsoleViewContentType.NORMAL_OUTPUT)
              case Message.Log(level, msg) =>
                val contentType =
                  if (level == Logger.Level.Info)
                    UI.ConsoleViewContentType.SYSTEM_OUTPUT
                  else
                    UI.ConsoleViewContentType.ERROR_OUTPUT
                print(s"[${level.toString.toLowerCase}] $msg\n", contentType)
            }
            scrollToEnd()
          }
        })
      }

      override def onRemoval(): Unit = if (!project.isOpen) dispose()
    })
  }
}

class ConsoleExecutionHandler(project: Project) extends BaseConsoleExecuteActionHandler(false) {

  private var isTaskDonePromise: Option[Promise[Unit]] = None
  private var cancelPromise: Option[Promise[Unit]] = None

  def cancel(): Unit = cancelPromise.foreach(_.tryFailure(null))

  def isCancellable(): Boolean = cancelPromise.fold(false)(!_.isCompleted)

  override def execute(text: String, console: LanguageConsoleView): Unit = {
    isTaskDonePromise = Some(Promise())
    cancelPromise = Some(Promise())

    console.setEditable(false)
    val subscription = sbtConnectorFor(project.getBasePath).open({ client =>
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
    }, { (_, _) =>
      Unit
    })

    isTaskDonePromise.foreach(_.future.onComplete { _ =>
      console.setEditable(true)
      subscription.cancel()
    })
  }
}
