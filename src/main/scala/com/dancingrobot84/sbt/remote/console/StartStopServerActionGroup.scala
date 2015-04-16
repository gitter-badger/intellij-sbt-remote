package com.dancingrobot84.sbt.remote
package console

import java.util.concurrent.atomic.AtomicBoolean

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager
import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager.ConnectionListener
import com.intellij.openapi.actionSystem.{ AnAction, AnActionEvent, DefaultActionGroup }
import com.intellij.openapi.project.Project

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Nikolay Obedin
 * @since 4/16/15.
 */
class StartStopServerActionGroup(project: Project) extends DefaultActionGroup(null, true) {

  private val isServerRunning = new AtomicBoolean

  SbtServerConnectionManager().addConnectionListener(project.getBasePath, new ConnectionListener {
    override def onConnect: Unit = isServerRunning.set(true)
    override def onDisconnect: Unit = isServerRunning.set(false)
  })

  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val presentation = e.getPresentation
    if (isServerRunning.get) {
      presentation.setText("Server is running")
      presentation.setIcon(Bundle("sbt.remote.server.running"))
    } else {
      presentation.setText("Server is stopped")
      presentation.setIcon(Bundle("sbt.remote.server.stopped"))
    }
  }

  this.add(new AnAction() {
    getTemplatePresentation.setText("Start Server")

    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(!isServerRunning.get)

    override def actionPerformed(e: AnActionEvent): Unit = {
      SbtServerConnectionManager().getSbtConnectorFor(project.getBasePath)
    }
  })

  this.add(new AnAction() {
    getTemplatePresentation.setText("Stop Server")

    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(isServerRunning.get)

    override def actionPerformed(e: AnActionEvent): Unit = {
      val connector = SbtServerConnectionManager().getSbtConnectorFor(project.getBasePath)
      connector.open({ client =>
        client.requestSelfDestruct()
      }, { (_, _) => Unit })
      connector.close()
    }
  })
}

