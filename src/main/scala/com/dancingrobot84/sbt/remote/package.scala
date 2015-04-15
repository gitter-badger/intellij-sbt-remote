package com.dancingrobot84.sbt

import java.io.File
import javax.swing.Icon

import com.intellij.openapi.diagnostic
import com.intellij.openapi.util.IconLoader
import sbt.client.{ SbtClient, Subscription, SbtChannel, SbtConnector }
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
package object remote {

  val Log: Logger = diagnostic.Logger.getInstance(Bundle("sbt.remote.id"))

  implicit def string2icon(resourcePath: String): Icon =
    IconLoader.getIcon(resourcePath)

  private val connectorsPool = mutable.HashMap.empty[String, SbtConnector]

  def sbtConnectorFor(path: String): SbtConnector = connectorsPool.synchronized {
    connectorsPool.get(path) match {
      case Some(connector) =>
        connector
      case None =>
        val connector = new SbtConnector {
          private val delegate = SbtConnector("idea", "Intellij IDEA", new File(path))
          private var openedClient: Option[SbtClient] = None

          override def open(onConnect: (SbtClient) => Unit, onError: (Boolean, String) => Unit)(implicit ex: ExecutionContext): Subscription = {
              def onChannelConnect(channel: SbtChannel): Unit = openedClient.synchronized {
                openedClient = Some(openedClient.getOrElse(SbtClient(channel)))
                return openedClient.foreach(onConnect)
              }
              def onChannelDisconnect(reconnecting: Boolean, message: String): Unit = {
                onError(reconnecting, message)
                if (!reconnecting) connectorsPool.synchronized {
                  connectorsPool.remove(path)
                } else openedClient.synchronized {
                  openedClient.foreach(_.close)
                  openedClient = None
                }

              }
            openChannel(onChannelConnect, onChannelDisconnect)
          }

          override def openChannel(onConnect: (SbtChannel) => Unit, onError: (Boolean, String) => Unit)(implicit ex: ExecutionContext): Subscription = delegate.openChannel(onConnect, onError)

          override def close(): Unit = delegate.close
        }
        connectorsPool.put(path, connector)
        connector
    }
  }
}
