package com.dancingrobot84.sbt.remote
package project
package components

import java.util.concurrent.CopyOnWriteArrayList

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager
import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager.ConnectionListener
import com.dancingrobot84.sbt.remote.external.SystemSettings
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import sbt.client.{Subscription, SbtClient}
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Stores each message sent by sbt server either to log (except "debug") or to stdout/stderr while project is open.
 * Log is persistent during multiple reconnections to sbt server, but will be erased after project is closed.
 *
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
      @volatile private var logSubscription: Option[Subscription] = None
      override def onConnect(): Unit =
        logSubscription = Some(connectionManager.makeSbtConnectorFor(project.getBasePath).open(self.onConnect, self.onFailure))
      override def onDisconnect(): Unit =
        logSubscription.foreach(_.cancel())
    })
    connectionManager.ensureConnectionFor(project.getBasePath)
  }

  override def projectClosed(): Unit =
    listeners.asScala.foreach(removeLogListener)

  /**
   * Add listener to session log.
   * Listener will receive all previous messages on adding.
   */
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
      case logEvent: LogEvent if !logEvent.entry.message.startsWith("Read from stdout:") =>
        logEvent.entry match {
          case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
            addMessage(Message.Stdout(logEvent.entry.message))
          case LogMessage(level, message) if level != LogMessage.DEBUG =>
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
