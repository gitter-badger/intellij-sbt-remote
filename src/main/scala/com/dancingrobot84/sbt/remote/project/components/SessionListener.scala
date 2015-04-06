package com.dancingrobot84.sbt.remote
package project
package components

import java.util.concurrent.CopyOnWriteArrayList

import com.dancingrobot84.sbt.remote.external.SystemSettings
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import sbt.client.SbtClient
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Nikolay Obedin
 * @since 4/2/15.
 */
class SessionListener(project: Project) extends AbstractProjectComponent(project) {

  import SessionListener._

  private val log = new CopyOnWriteArrayList[Message]
  private val listeners = new CopyOnWriteArrayList[LogListener]

  override def projectOpened(): Unit = Option(SystemSettings(project)).foreach { settings =>
    sbtConnectorFor(project.getBasePath).open(onConnect, onFailure)
  }

  override def projectClosed(): Unit =
    listeners.asScala.foreach(removeLogListener)

  def addLogListener(listener: LogListener): Unit = {
    log.asScala.foreach(listener.onMessage)
    listeners.add(listener)
  }

  def removeLogListener(listener: LogListener): Unit = {
    listener.onRemoval()
    listeners.remove(listener)
  }

  private def onConnect(client: SbtClient): Unit = {
    client.handleEvents {
      case logE: LogEvent if !logE.entry.message.startsWith("Read from stdout:") =>
        logE.entry match {
          case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
            val msg = Message.Stdout(logE.entry.message)
            log.add(msg)
            listeners.asScala.foreach(_.onMessage(msg))
          case LogMessage("info", _) =>
            val msg = Message.Log(Logger.Level.Info, logE.entry.message)
            log.add(msg)
            listeners.asScala.foreach(_.onMessage(msg))
          case LogMessage("warn", _) =>
            val msg = Message.Log(Logger.Level.Warn, logE.entry.message)
            log.add(msg)
            listeners.asScala.foreach(_.onMessage(msg))
          case LogMessage("error", _) =>
            val msg = Message.Log(Logger.Level.Error, logE.entry.message)
            log.add(msg)
            listeners.asScala.foreach(_.onMessage(msg))
          case _ => // ignore
        }
      case _ => // ignore
    }
  }

  private def onFailure(isReconnecting: Boolean, cause: String): Unit = {
  }
}

object SessionListener {
  def apply(project: Project): Option[SessionListener] =
    Option(project.getComponent(classOf[SessionListener]))

  sealed trait Message {
    val message: String
  }

  object Message {
    case class Log(level: Logger.Level, message: String) extends Message
    case class Stdout(message: String) extends Message
  }

  trait LogListener {
    def onMessage(msg: Message): Unit
    def onRemoval(): Unit
  }
}
