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
    override def onConnect(): Unit = isServerRunning.set(true)
    override def onDisconnect(): Unit = isServerRunning.set(false)
  })

  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val presentation = e.getPresentation
    if (isServerRunning.get) {
      presentation.setText(Bundle("sbt.remote.action.serverIsRunning"))
      presentation.setIcon(Bundle("sbt.remote.icon.serverIsRunning"))
    } else {
      presentation.setText(Bundle("sbt.remote.action.serverStopped"))
      presentation.setIcon(Bundle("sbt.remote.icon.serverIsStopped"))
    }
  }

  this.add(new AnAction() {
    getTemplatePresentation.setText(Bundle("sbt.remote.action.startServer"))

    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(!isServerRunning.get)

    override def actionPerformed(e: AnActionEvent): Unit = {
      SbtServerConnectionManager().ensureConnectionFor(project.getBasePath)
    }
  })

  this.add(new AnAction() {
    getTemplatePresentation.setText(Bundle("sbt.remote.action.stopServer"))

    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(isServerRunning.get)

    override def actionPerformed(e: AnActionEvent): Unit = {
      val connector = SbtServerConnectionManager().getSbtConnectorFor(project.getBasePath)
      connector.open({ client =>
        client.requestSelfDestruct()
      })
      connector.close()
    }
  })
}

