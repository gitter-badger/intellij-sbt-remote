package com.dancingrobot84.sbt.remote
package project
package structure

import java.io.File
import java.net.URI

import com.intellij.openapi.externalSystem.model.project.{ContentRootData, ExternalSystemSourceType, ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.module.StdModuleTypes

import scala.collection.mutable

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class StatefulProject(val base: URI, @volatile var name: String) extends Project {
  private val modules = mutable.Buffer.empty[StatefulModule]

  def addModule(name: String, base: File) = this.synchronized {
    modules.find(_.name == name).getOrElse {
      val module = new StatefulModule(base, name)
      modules += module
      module
    }
  }

  def findModule(name: String) = this.synchronized {
    modules.find(_.name == name)
  }

  def removeModule(name: String) = this.synchronized {
    findModule(name).foreach(modules -= _)
  }

  def toDataNode: DataNode[ProjectData] = {
    val baseFile = new File(base)
    val projectNode = new DataNode(ProjectKeys.PROJECT,
      new ProjectData(external.Id, name, baseFile.getAbsolutePath, baseFile.getAbsolutePath),
      null)
    modules.map(_.toDataNode(projectNode)).foreach(projectNode.addChild)
    projectNode
  }
}

class StatefulModule(val base: File, @volatile var name: String) extends Module {
  private val paths = mutable.Buffer.empty[Path]

  def addPath(path: Path) = this.synchronized {
    paths += path
  }

  def removePath(path: Path) = this.synchronized {
    paths -= path
  }

  def toDataNode(parent: DataNode[ProjectData]): DataNode[ModuleData] = {
    import com.dancingrobot84.sbt.remote.project.structure.Helpers._

    val ideModulePath = parent.getData(ProjectKeys.PROJECT).getIdeProjectFileDirectoryPath + "/.idea/modules"
    val moduleNode = new DataNode(
      ProjectKeys.MODULE,
      new ModuleData(name, external.Id, StdModuleTypes.JAVA.getId,
        name, ideModulePath, base.getAbsolutePath),
      parent)

    moduleNode.addChild(paths.toDataNode(moduleNode))
    moduleNode
  }
}

class StatefulLibrary {

}

object Helpers {

  implicit class RichSeqOfPath(paths: Seq[Path]) {
    def toDataNode(parent: DataNode[ModuleData]): DataNode[ContentRootData] = {
      val base = parent.getData(ProjectKeys.MODULE).getLinkedExternalProjectPath
      val data = new ContentRootData(external.Id, base)
      paths.foreach {
        case Path.Source(path) =>
          data.storePath(ExternalSystemSourceType.SOURCE, path.getAbsolutePath)
        case Path.GenSource(path) =>
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, path.getAbsolutePath)
        case Path.TestSource(path) =>
          data.storePath(ExternalSystemSourceType.TEST, path.getAbsolutePath)
        case Path.GenTestSource(path) =>
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, path.getAbsolutePath)
        case Path.Resource(path) =>
          data.storePath(ExternalSystemSourceType.RESOURCE, path.getAbsolutePath)
        case Path.GenResource(path) =>
          data.storePath(ExternalSystemSourceType.RESOURCE, path.getAbsolutePath)
        case Path.TestResource(path) =>
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, path.getAbsolutePath)
        case Path.GenTestResource(path) =>
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, path.getAbsolutePath)
        case Path.Exclude(path) =>
          data.storePath(ExternalSystemSourceType.EXCLUDED, path.getAbsolutePath)
      }
      new DataNode(ProjectKeys.CONTENT_ROOT, data, parent)
    }
  }
}