package com.dancingrobot84.sbt.remote
package project
package structure

import java.io.File
import java.net.URI

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import scala.collection.mutable

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class StatefulProject(val base: URI, @volatile var name: String) extends Project {
  private val modules   = mutable.Buffer.empty[StatefulModule]
  private val libraries = mutable.Buffer.empty[StatefulLibrary]

  def addModule(id: String, base: File) = this.synchronized {
    findModule(id).getOrElse {
      val module = new StatefulModule(base, id, id)
      modules += module
      module
    }
  }

  def findModule(id: String) = this.synchronized {
    modules.find(_.id == id)
  }

  def removeModule(id: String) = this.synchronized {
    findModule(id).foreach(modules -= _)
  }

  def addLibrary(id: LibraryId): Library = this.synchronized {
    findLibrary(id).getOrElse {
      val lib = new StatefulLibrary(id)
      libraries += lib
      lib
    }
  }

  def findLibrary(id: LibraryId) = this.synchronized {
    libraries.find(_.id == id)
  }

  def removeLibrary(id: LibraryId): Unit = this.synchronized {
    findLibrary(id).foreach(libraries -= _)
  }

  def toDataNode: DataNode[ProjectData] = {
    val baseFile = new File(base)
    val projectNode = new DataNode(
      ProjectKeys.PROJECT,
      new ProjectData(external.Id, name, baseFile.getAbsolutePath, baseFile.getAbsolutePath),
      null)
    libraries.map(_.toDataNode(projectNode)).foreach(projectNode.addChild)
    modules.map(_.toDataNode(projectNode)).foreach(projectNode.addChild)
    projectNode
  }
}

class StatefulModule(val base: File, @volatile var id: String, @volatile var name: String) extends Module {
  private val paths = mutable.Set.empty[Path]
  private val deps  = mutable.Set.empty[Dependency]

  def addPath(path: Path) = this.synchronized {
    paths += path
  }

  def removePath(path: Path) = this.synchronized {
    paths -= path
  }

  def addDependency(dependency: Dependency) = this.synchronized {
    deps += dependency
  }

  def removeDependency(dependency: Dependency) = this.synchronized {
    deps -= dependency
  }

  def toDataNode(parent: DataNode[ProjectData]): DataNode[ModuleData] = {
    import com.dancingrobot84.sbt.remote.project.structure.Helpers._

    val ideModulePath = parent.getData(ProjectKeys.PROJECT)
                              .getIdeProjectFileDirectoryPath + "/.idea/modules"
    val moduleData = new ModuleData(
        id, external.Id, StdModuleTypes.JAVA.getId,
        name, ideModulePath, base.getAbsolutePath)
    val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)
    moduleData.setInheritProjectCompileOutputPath(false)

    import scala.collection.JavaConverters._
    deps.foreach {
      case Dependency.Library(libId, conf) =>
        for {
          node <- parent.getChildren.asScala
          lib  <- Option(node.getData(ProjectKeys.LIBRARY))
          if lib.getExternalName == libId.toString
        } {
          val libDepData = new LibraryDependencyData(moduleData, lib, LibraryLevel.PROJECT)
          conf match {
            case Configuration.Compile => libDepData.setScope(DependencyScope.COMPILE)
            case Configuration.Test => libDepData.setScope(DependencyScope.TEST)
            case _ => // TODO: implement
          }
          moduleNode.addChild(new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libDepData, moduleNode))
        }
      case _ => // TODO: implement
    }

    moduleNode.addChild(paths.toDataNode(moduleNode))

    moduleNode
  }
}

class StatefulLibrary(val id: LibraryId) extends Library {
  private val artifacts = mutable.Set.empty[Artifact]

  def addArtifact(artifact: Artifact) = this.synchronized {
    artifacts += artifact
  }

  def toDataNode(parent: DataNode[ProjectData]): DataNode[LibraryData] = {
    val lib = new LibraryData(external.Id, id.toString, false)
    artifacts.foreach {
      case Artifact.Binary(path) => lib.addPath(LibraryPathType.BINARY, path.getAbsolutePath)
      case Artifact.Source(path) => lib.addPath(LibraryPathType.SOURCE, path.getAbsolutePath)
      case Artifact.Doc(path)    => lib.addPath(LibraryPathType.DOC, path.getAbsolutePath)
    }
    new DataNode(ProjectKeys.LIBRARY, lib, parent)
  }
}

object Helpers {

  implicit class RichSetOfPath(paths: mutable.Set[Path]) {
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