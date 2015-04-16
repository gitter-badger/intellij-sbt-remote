package com.dancingrobot84.sbt.remote.applicationComponents

import java.io.File

import sbt.client._

import scala.concurrent.ExecutionContext

/**
 * Extended version of SbtConnector. Additions include:
 *    - Reusing already opened client. Prevents from getting ChannelInUse exception
 *    - `isConnected` method
 * @author Nikolay Obedin
 * @since 4/16/15.
 */
class SbtConnectorEx(path: String) extends SbtConnector {

  private val delegate = SbtConnector("idea", "Intellij IDEA", new File(path))
  private var openedClient: Option[SbtClient] = None

  override def open(onConnect: (SbtClient) => Unit, onError: (Boolean, String) => Unit)(implicit ex: ExecutionContext): Subscription = {
      def onChannelConnect(channel: SbtChannel): Unit = openedClient.synchronized {
        openedClient = Some(openedClient.getOrElse(SbtClient(channel)))
        return openedClient.foreach(onConnect)
      }

      def onChannelDisconnect(reconnecting: Boolean, message: String): Unit = {
        onError(reconnecting, message)
        if (reconnecting) openedClient.synchronized {
          openedClient.foreach(_.close)
          openedClient = None
        }

      }
    openChannel(onChannelConnect, onChannelDisconnect)
  }

  override def openChannel(onConnect: (SbtChannel) => Unit, onError: (Boolean, String) => Unit)(
    implicit ex: ExecutionContext): Subscription =
    delegate.openChannel(onConnect, onError)

  override def close(): Unit = delegate.close

  def isConnected: Boolean = openedClient.synchronized(openedClient.isDefined)

  def open(onConnect: (SbtClient) => Unit)(implicit ex: ExecutionContext): Subscription =
    open(onConnect, (_, _) => Unit)
}

