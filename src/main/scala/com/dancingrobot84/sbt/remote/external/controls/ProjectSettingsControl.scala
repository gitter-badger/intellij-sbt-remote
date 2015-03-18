package com.dancingrobot84.sbt.remote
package external
package controls

import javax.swing.JCheckBox

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._

/**
 * @author Nikolay Obedin
 * @since 3/18/15.
 */
class ProjectSettingsControl(initialSettings: ProjectSettings)
    extends AbstractExternalProjectSettingsControl[ProjectSettings](initialSettings) {

  private val resolveClassifiersCheckBox = new JCheckBox(Bundle("sbt.remote.settings.resolveClassifiers"))
  private val resolveSbtClassifiersCheckBox = new JCheckBox(Bundle("sbt.remote.settings.resolveSbtClassifiers"))

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {
    content.add(resolveClassifiersCheckBox, getFillLineConstraints(indentLevel))
    content.add(resolveSbtClassifiersCheckBox, getFillLineConstraints(indentLevel))
  }

  override def isExtraSettingModified = {
    val settings = getInitialSettings
    resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveClassifiers
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings
    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  override def applyExtraSettings(settings: ProjectSettings) {
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
  }

  def validate(sbtProjectSettings: ProjectSettings) = true
}
