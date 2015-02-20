package com.dancingrobot84.sbt

import java.io.File
import javax.swing.Icon

import com.intellij.openapi.diagnostic
import com.intellij.openapi.util.IconLoader
import sbt.client.SbtConnector

import scala.language.implicitConversions

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
package object remote {

  val Log: Logger = diagnostic.Logger.getInstance(Bundle("sbt.remote.id"))

  implicit def string2icon(resourcePath: String): Icon =
    IconLoader.getIcon(resourcePath)

  def sbtConnectorFor(path: File): SbtConnector =
    SbtConnector("idea", "Intellij IDEA", path)
}
