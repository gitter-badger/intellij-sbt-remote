package com.dancingrobot84.sbt.remote.applicationComponents

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager.ConnectionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Handles pool of opened SbtConnectors and allows to receive notifications about their state changes.
 *
 * @author Nikolay Obedin
 * @since 4/16/15.
 */
class SbtServerConnectionManager extends ApplicationComponent.Adapter {
  private val connectorsPool = mutable.HashMap.empty[String, SbtConnectorEx]
  private val connectorsPoolLock = new Object

  private val listeners = mutable.HashMap.empty[String, mutable.Set[ConnectionListener]]
  private val listenersLock = new Object

  override def disposeComponent(): Unit = connectorsPoolLock.synchronized {
    connectorsPool.foreach { case (_, connector) => connector.close() }
  }

  /**
   * Ensure that sbt server connection for specified path is established
   */
  def ensureConnectionFor(path: String): Unit = getSbtConnectorFor(path)

  /**
   * Get running SbtConnector for specified path or create one
   */
  def getSbtConnectorFor(path: String): SbtConnectorEx = connectorsPoolLock.synchronized {
    connectorsPool.get(path) match {
      case Some(connector) =>
        connector
      case None =>
        val connector = new SbtConnectorEx(path)
        connector.open({ _ =>
          listenersLock.synchronized {
            listeners.get(path).foreach(_.foreach(_.onConnect))
          }
        }, { (reconnecting, _) =>
          listenersLock.synchronized {
            listeners.get(path).foreach(_.foreach(_.onDisconnect))
          }
          if (!reconnecting) connectorsPoolLock.synchronized {
            connectorsPool.remove(path) // TODO: maybe close connector?
          }
        })
        connectorsPool.put(path, connector)
        connector
    }
  }

  /**
   * Add SbtConnector state change listener for specified path
   * Listener will receive notification about current state of sbt server connection on adding.
   */
  def addConnectionListener(path: String, listener: ConnectionListener): Unit = {
    listenersLock.synchronized {
      listeners.getOrElseUpdate(path, mutable.Set.empty).add(listener)
    }
    connectorsPoolLock.synchronized {
      connectorsPool.get(path).fold(listener.onDisconnect) { connector =>
        if (connector.isConnected) listener.onConnect else listener.onDisconnect
      }
    }
  }

  def removeConnectionListener(path: String, listener: ConnectionListener): Unit = listenersLock.synchronized {
    listeners.get(path).foreach(_.remove(listener))
  }
}

object SbtServerConnectionManager {
  def apply() = ApplicationManager.getApplication.getComponent(classOf[SbtServerConnectionManager])

  trait ConnectionListener {
    /**
     * Will be called when SbtConnector is created AND connection is successfully established
     */
    def onConnect(): Unit

    /**
     * Will be called either when SbtConnector is not created yet OR it is destroyed OR it is trying to reconnect
     */
    def onDisconnect(): Unit
  }
}