package com.dancingrobot84.sbt.remote
package external
package controls

import com.intellij.openapi.externalSystem.util.{ ExternalSystemSettingsControl, PaintAwarePanel }

/**
 * @author Nikolay Obedin
 * @since 3/18/15.
 */
class SystemSettingsControl(initialSettings: SystemSettings)
    extends ExternalSystemSettingsControl[SystemSettings] {

  override def isModified = false

  override def showUi(show: Boolean): Unit = {}

  override def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit = {}

  override def disposeUIResources() {}

  override def apply(settings: SystemSettings): Unit = {}

  override def reset(): Unit = {}

  override def validate(settings: SystemSettings) = true
}
