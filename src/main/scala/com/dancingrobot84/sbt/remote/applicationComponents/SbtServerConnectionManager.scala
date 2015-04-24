package com.dancingrobot84.sbt.remote
package applicationComponents

import java.io._

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager.ConnectionListener
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry

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

  override def initComponent(): Unit = {
    // FIXME: hack to feed sbt-launcher with our local repo
    val pluginPath = PluginManager.getPlugin(PluginId.getId("com.dancingrobot84.intellij-sbt-remote")).getPath
    val repo = s"idea: file://$pluginPath/server, [organization]/[module]/[revision]/[artifact].[ext]"

    Registry.get(external.Id.getId + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX).setValue(true);

    val properties = {
      val props = sbt.IO.readStream(getClass.getClassLoader.getResourceAsStream("sbt-server.properties")).split('\n')
      val (before, after) = props.splitAt(props.indexOf("[repositories]") + 1)
      (before ++ Seq(s"  $repo") ++ after).mkString("\n")
    }

    val propertiesFile = new File(pluginPath, "sbt-server.fixed.properties")
    sbt.IO.write(propertiesFile, properties)
    System.setProperty("sbt.server.properties.file", propertiesFile.getAbsolutePath)
  }

  override def disposeComponent(): Unit = connectorsPoolLock.synchronized {
    connectorsPool.foreach { case (_, connector) => connector.close() }
  }

  /**
   * Ensure that sbt server connection for specified path is established
   */
  def ensureConnectionFor(path: String): Unit = makeSbtConnectorFor(path)

  /**
   * Get running SbtConnector for specified path or create one
   */
  def makeSbtConnectorFor(path: String): SbtConnectorEx = connectorsPoolLock.synchronized {
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
            connectorsPool.remove(path).foreach(_.close)
          }
        })
        connectorsPool.put(path, connector)
        connector
    }
  }

  /**
   * Check whether there is connection established for specified path
   */
  def getSbtConnectorFor(path: String): Option[SbtConnectorEx] =
    connectorsPoolLock.synchronized(connectorsPool.get(path))

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