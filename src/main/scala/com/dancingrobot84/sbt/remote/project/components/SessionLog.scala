package com.dancingrobot84.sbt.remote
package project
package components

import java.util.concurrent.CopyOnWriteArrayList

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager
import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager.ConnectionListener
import com.dancingrobot84.sbt.remote.external.SystemSettings
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.content.MessageView
import sbt.client.SbtClient
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Nikolay Obedin
 * @since 4/2/15.
 */
class SessionLog(project: Project) extends AbstractProjectComponent(project) { self =>

  import SessionLog._

  private val log = new CopyOnWriteArrayList[Message]
  private val listeners = new CopyOnWriteArrayList[LogListener]

  override def projectOpened(): Unit = Option(SystemSettings(project)).foreach { settings =>
    val connectionManager = SbtServerConnectionManager()
    connectionManager.addConnectionListener(project.getBasePath, new ConnectionListener {
      override def onConnect(): Unit =
        connectionManager.getSbtConnectorFor(project.getBasePath).open(self.onConnect, self.onFailure)
      override def onDisconnect(): Unit = {}
    })
    connectionManager.ensureConnectionFor(project.getBasePath)
  }

  override def projectClosed(): Unit =
    listeners.asScala.foreach(removeLogListener)

  def addLogListener(listener: LogListener): Unit = {
    log.asScala.foreach(listener.onMessage)
    listeners.add(listener)
  }

  def removeLogListener(listener: LogListener): Unit =
    listeners.remove(listener)

  private def addMessage(msg: Message): Unit = {
    log.add(msg)
    listeners.asScala.foreach(_.onMessage(msg))
  }

  private def onConnect(client: SbtClient): Unit = {
    client.handleEvents {
      case logE: LogEvent if !logE.entry.message.startsWith("Read from stdout:") =>
        logE.entry match {
          case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
            addMessage(Message.Stdout(logE.entry.message))
          case LogMessage("debug", _) => // ignore
          case LogMessage(level, message) =>
            addMessage(Message.Log(Logger.Level.fromString(level), message))
          case _ => // ignore
        }
      case _ => // ignore
    }
  }

  private def onFailure(isReconnecting: Boolean, cause: String): Unit =
    addMessage(Message.Log(Logger.Level.Warn, cause))
}

object SessionLog {
  def apply(project: Project): Option[SessionLog] =
    Option(project.getComponent(classOf[SessionLog]))

  sealed trait Message {
    val message: String
  }

  object Message {
    case class Log(level: Logger.Level, message: String) extends Message
    case class Stdout(message: String) extends Message
  }

  trait LogListener {
    def onMessage(msg: Message): Unit
  }
}
