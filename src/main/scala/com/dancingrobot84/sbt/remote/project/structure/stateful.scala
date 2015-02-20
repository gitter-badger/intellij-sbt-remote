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
    moduleNode.getData(ProjectKeys.MODULE).setInheritProjectCompileOutputPath(false)

    moduleNode.addChild(paths.toDataNode(moduleNode))
    moduleNode
  }
}

class StatefulLibrary {

}

object Helpers {

  implicit class RichSeqOfPath(paths: Seq[Path]) {
    def toDataNode(parent: DataNode[ModuleData]): DataNode[ContentRootData] = {
      val module = parent.getData(ProjectKeys.MODULE)
      val data = new ContentRootData(external.Id, module.getLinkedExternalProjectPath)
      paths.foreach {
        case Path.Source(base) =>
          data.storePath(ExternalSystemSourceType.SOURCE, base.getAbsolutePath)
        case Path.GenSource(base) =>
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, base.getAbsolutePath)
        case Path.TestSource(base) =>
          data.storePath(ExternalSystemSourceType.TEST, base.getAbsolutePath)
        case Path.GenTestSource(base) =>
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, base.getAbsolutePath)
        case path@(Path.Resource(_) | Path.GenResource(_))=>
          data.storePath(ExternalSystemSourceType.RESOURCE, path.base.getAbsolutePath)
        case path@(Path.TestResource(_) | Path.GenTestResource(_)) =>
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, path.base.getAbsolutePath)
        case Path.Exclude(base) =>
          data.storePath(ExternalSystemSourceType.EXCLUDED, base.getAbsolutePath)
        case Path.Output(base) =>
          module.setCompileOutputPath(ExternalSystemSourceType.SOURCE, base.getAbsolutePath)
        case Path.TestOutput(base) =>
          module.setCompileOutputPath(ExternalSystemSourceType.TEST, base.getAbsolutePath)
      }
      new DataNode(ProjectKeys.CONTENT_ROOT, data, parent)
    }
  }
}