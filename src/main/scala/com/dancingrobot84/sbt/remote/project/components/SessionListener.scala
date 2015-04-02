package com.dancingrobot84.sbt.remote
package project
package components

import java.util.concurrent.{ConcurrentSkipListSet, CopyOnWriteArrayList, ConcurrentHashMap}

import com.dancingrobot84.sbt.remote.external.SystemSettings
import com.dancingrobot84.sbt.remote.project.components.SessionListener.LogListener
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import sbt.client.{SbtClient, SbtConnector}
import sbt.protocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/2/15.
 */
class SessionListener(project: Project) extends AbstractProjectComponent(project) {

  private var connector: Option[SbtConnector] = None
  private val log = new CopyOnWriteArrayList[String]
  private val listeners = new CopyOnWriteArrayList[LogListener]

  override def projectOpened(): Unit = Option(SystemSettings(project)).foreach { settings =>
    connector = Some(sbtConnectorFor(project.getBasePath))
    connector.foreach(_.open(onConnect, onFailure))
  }

  override def projectClosed(): Unit = {
    connector.foreach(_.close)
    listeners.asScala.foreach(_.onRemoval)
    listeners.clear()
  }

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
      case logE: LogEvent => logE.entry match {
        case LogStdOut(_) | LogStdErr(_) | LogSuccess(_) | LogTrace(_, _) =>
          log.add(logE.entry.message)
          listeners.asScala.foreach(_.onMessage(logE.entry.message))
        case LogMessage("info", _) =>
          log.add(logE.entry.message)
          listeners.asScala.foreach(_.onMessage(logE.entry.message))
        case LogMessage("warn", _) =>
          log.add(logE.entry.message)
          listeners.asScala.foreach(_.onMessage(logE.entry.message))
        case LogMessage("error", _) =>
          log.add(logE.entry.message)
          listeners.asScala.foreach(_.onMessage(logE.entry.message))
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

  trait LogListener {
    def onMessage(msg: String): Unit
    def onRemoval(): Unit
  }
}
