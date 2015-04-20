package com.dancingrobot84.sbt.remote.jps

import org.jetbrains.jps.model.{ JpsProject, JpsElementChildRole }
import org.jetbrains.jps.model.ex.JpsElementBase

/**
 * @author Nikolay Obedin
 * @since 4/20/15.
 */

class SbtRemoteProjectSettings(var projectPath: String) extends JpsElementBase[SbtRemoteProjectSettings] {
  override def applyChanges(modified: SbtRemoteProjectSettings): Unit =
    projectPath = modified.projectPath

  override def createCopy(): SbtRemoteProjectSettings =
    new SbtRemoteProjectSettings(projectPath)
}

object SbtRemoteProjectSettings {
  val Role = new JpsElementChildRole[SbtRemoteProjectSettings]

  def get(project: JpsProject): Option[SbtRemoteProjectSettings] =
    Option(project.getContainer.getChild(Role))
}
