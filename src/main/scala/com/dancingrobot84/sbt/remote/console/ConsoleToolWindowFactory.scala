package com.dancingrobot84.sbt.remote.console

import com.dancingrobot84.sbt.remote.project.components.SessionListener
import com.dancingrobot84.sbt.remote.project.components.SessionListener.LogListener
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory, ToolWindowType}

/**
 * @author Nikolay Obedin
 * @since 4/2/15.
 */
class ConsoleToolWindowFactory extends ToolWindowFactory {
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val view = new ConsoleView(project)
    toolWindow.getComponent.add(view.getComponent)
  }
}

class ConsoleView(project: Project) extends LanguageConsoleImpl(project, "SBT Remote REPL", PlainTextLanguage.INSTANCE) {
  SessionListener(project).foreach { listener =>
    listener.addLogListener(new LogListener {
      override def onMessage(msg: String): Unit = {
        ApplicationManagerEx.getApplicationEx.invokeLater(new Runnable {
          override def run(): Unit = {
            if (!msg.startsWith("Read from stdout:"))
              print(msg + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
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