package com.dancingrobot84.sbt.remote
package console

import java.awt.BorderLayout
import javax.swing.JPanel

import com.dancingrobot84.sbt.remote.project.components.SessionListener
import com.dancingrobot84.sbt.remote.project.components.SessionListener.LogListener
import com.intellij.execution.console.{ BaseConsoleExecuteActionHandler, ConsoleExecuteAction, LanguageConsoleImpl, LanguageConsoleView }
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

    val execAction = new ConsoleExecuteAction(consoleView, new ConsoleExecutionHandler(project))
    Option(execAction.getShortcutSet).foreach(ss => execAction.registerCustomShortcutSet(ss, consoleView.getComponent))
    toolbarActions.add(execAction)

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
  override def execute(text: String, console: LanguageConsoleView): Unit = {
    val donePromise = Promise[Unit]()
    console.setEditable(false)
    val subscription = sbtConnectorFor(project.getBasePath).open({ client =>
      for {
        id0 <- client.requestExecution(text, None)
      } {
        client.handleEvents {
          case ExecutionSuccess(id) if id == id0 =>
            donePromise.trySuccess(Unit)
          case ExecutionFailure(id) if id == id0 =>
            donePromise.trySuccess(Unit)
          case _ => // do nothing
        }
      }
    }, { (_, _) =>
      Unit
    })
    donePromise.future.onComplete { _ =>
      console.setEditable(true)
      subscription.cancel
    }
  }
}
