package com.dancingrobot84.sbt

import javax.swing.Icon

import com.intellij.openapi.diagnostic
import com.intellij.openapi.util.IconLoader

import scala.language.implicitConversions

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
package object remote {

  val Log: Logger = diagnostic.Logger.getInstance(Bundle("sbt.remote.id"))

  implicit def string2icon(resourcePath: String): Icon =
    IconLoader.getIcon(resourcePath)
}
