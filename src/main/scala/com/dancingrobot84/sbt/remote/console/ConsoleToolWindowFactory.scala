package com.dancingrobot84.sbt.remote
package console

import java.awt.BorderLayout
import java.util
import javax.swing.JPanel

import com.dancingrobot84.sbt.remote.project.components.SessionListener
import com.dancingrobot84.sbt.remote.project.components.SessionListener.LogListener
import com.intellij.execution.console.{BaseConsoleExecuteActionHandler, ConsoleExecuteAction, LanguageConsoleImpl, LanguageConsoleView}
import com.intellij.execution.impl.ConsoleViewImpl.ClearAllAction
import com.intellij.execution.{ ui => UI }
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._

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
      override def onMessage(msg: String): Unit = {
        ApplicationManagerEx.getApplicationEx.invokeLater(new Runnable {
          override def run(): Unit = {
            if (!msg.startsWith("Read from stdout:"))
              print(msg + "\n", UI.ConsoleViewContentType.NORMAL_OUTPUT)
            scrollToEnd()
          }
        })
      }

      override def onRemoval(): Unit = {
        if (!project.isOpen)
          dispose()
      }
    })
  }
}

class ConsoleExecutionHandler(project: Project) extends BaseConsoleExecuteActionHandler(false) {
  override def execute(text: String, console: LanguageConsoleView): Unit = {
    sbtConnectorFor(project.getBasePath).open({ client =>
      client.requestExecution(text, None)
    }, { (_, _) =>
      Unit
    })
  }
}