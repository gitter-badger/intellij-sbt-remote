package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.project.PlatformFacade
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.AbstractCollection
import scala.beans.BeanProperty
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.messages.{ Topic => ExternalSystemTopic }

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */

@State(
  name = "SbtRemoteSystemSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.PROJECT_FILE),
    new Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sbt-remote.xml",
      scheme = StorageScheme.DIRECTORY_BASED)
  )
)
final class SystemSettings(project: Project)
    extends AbstractExternalSystemSettings[SystemSettings, ProjectSettings, ProjectSettingsListener](Topic, project)
    with PersistentStateComponent[SystemSettings.State] {

  def checkSettings(old: ProjectSettings, current: ProjectSettings): Unit = {}

  def subscribe(listener: ExternalSystemSettingsListener[ProjectSettings]): Unit = {}

  def copyExtraSettingsFrom(settings: SystemSettings): Unit = {}

  def getState = {
    val s = new SystemSettings.State
    fillState(s)
    s
  }

  def loadState(state: SystemSettings.State): Unit =
    super[AbstractExternalSystemSettings].loadState(state)
}

object SystemSettings {
  def apply(project: Project) = ServiceManager.getService(project, classOf[SystemSettings])

  final class State
      extends AbstractExternalSystemSettings.State[ProjectSettings] {

    private val projectSettings = ContainerUtilRt.newTreeSet[ProjectSettings]()

    @AbstractCollection(surroundWithTag = false, elementTypes = Array(classOf[ProjectSettings]))
    override def getLinkedExternalProjectsSettings: java.util.Set[ProjectSettings] =
      projectSettings

    override def setLinkedExternalProjectsSettings(settings: java.util.Set[ProjectSettings]): Unit =
      Option(settings).foreach(projectSettings.addAll)
  }
}

final class ProjectSettings(
  @BeanProperty
  var resolveClassifiers: Boolean,
  @BeanProperty
  var resolveSbtClassifiers: Boolean)
    extends ExternalProjectSettings {

  def this() {
    this(false, false)
  }

  override def clone(): ExternalProjectSettings = {
    val result = new ProjectSettings(resolveClassifiers, resolveSbtClassifiers)
    copyTo(result)
    result
  }
}

object ProjectSettings {
  def apply() = new ProjectSettings
}

@State(
  name = "SbtRemoteLocalSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.WORKSPACE_FILE)
  )
)
final class LocalSettings(project: Project, platformFacade: PlatformFacade)
    extends AbstractExternalSystemLocalSettings(Id, project, platformFacade)
    with PersistentStateComponent[LocalSettings.State] {

  def getState = {
    val s = new LocalSettings.State
    fillState(s)
    s
  }

  def loadState(state: LocalSettings.State): Unit =
    super[AbstractExternalSystemLocalSettings].loadState(state)
}

object LocalSettings {
  def apply(project: Project) = ServiceManager.getService(project, classOf[LocalSettings])

  final class State
    extends AbstractExternalSystemLocalSettings.State
}

final class ExecutionSettings(
  val resolveClassifiers: Boolean,
  val resolveSbtClassifiers: Boolean)
    extends ExternalSystemExecutionSettings

trait ProjectSettingsListener
  extends ExternalSystemSettingsListener[ProjectSettings]

object Topic extends ExternalSystemTopic[ProjectSettingsListener]("", classOf[ProjectSettingsListener])
