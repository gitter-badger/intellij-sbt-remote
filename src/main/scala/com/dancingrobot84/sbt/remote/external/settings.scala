package com.dancingrobot84.sbt.remote.external

import java.util

import com.dancingrobot84.sbt.remote.Bundle
import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.project.PlatformFacade
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.messages.{ Topic => ExternalSystemTopic }
import com.intellij.util.xmlb.annotations.{ MapAnnotation, AbstractCollection }

import scala.beans.BeanProperty

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

  override def checkSettings(old: ProjectSettings, current: ProjectSettings): Unit = {}

  override def subscribe(listener: ExternalSystemSettingsListener[ProjectSettings]): Unit = {
    val adapter = new DelegatingExternalSystemSettingsListener[ProjectSettings](listener) with ProjectSettingsListener
    getProject.getMessageBus.connect(getProject).subscribe(Topic, adapter)
  }

  override def copyExtraSettingsFrom(settings: SystemSettings): Unit = {}

  override def getState = {
    val s = new SystemSettings.State
    fillState(s)
    s
  }

  override def loadState(state: SystemSettings.State): Unit =
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
  @BeanProperty var resolveClassifiers: Boolean,
  @BeanProperty var resolveSbtClassifiers: Boolean)
    extends ExternalProjectSettings {

  @BeanProperty
  @MapAnnotation
  var moduleNameToQualifiedNameMap: util.Map[String, String] = new util.HashMap[String, String]()

  def this() {
    this(false, false)
  }

  override def clone(): ExternalProjectSettings = {
    val result = new ProjectSettings(resolveClassifiers, resolveSbtClassifiers)
    result.moduleNameToQualifiedNameMap.putAll(moduleNameToQualifiedNameMap)
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

  override def getState = {
    val s = new LocalSettings.State
    fillState(s)
    s
  }

  override def loadState(state: LocalSettings.State): Unit =
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

object Topic extends ExternalSystemTopic[ProjectSettingsListener](Bundle("sbt.remote.name"), classOf[ProjectSettingsListener])
