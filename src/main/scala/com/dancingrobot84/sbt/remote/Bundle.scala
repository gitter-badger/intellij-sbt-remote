package com.dancingrobot84.sbt.remote

import java.lang.ref.{ Reference, SoftReference }
import java.util.ResourceBundle

import com.intellij.CommonBundle

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
object Bundle {
  def apply(key: String, params: AnyRef*) = CommonBundle.message(get(), key, params: _*)

  private var ourBundle: Reference[ResourceBundle] = null
  private val BUNDLE = "com.dancingrobot84.sbt.remote.Bundle"

  private def get(): ResourceBundle = {
    var bundle: ResourceBundle = null
    if (ourBundle != null) bundle = ourBundle.get
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE)
      ourBundle = new SoftReference[ResourceBundle](bundle)
    }
    bundle
  }
}
