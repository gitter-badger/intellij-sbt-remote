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
class StatefulProject(val base: URI, var name: String) extends Project with Cloneable {
  private val modules   = mutable.Buffer.empty[StatefulModule]
  private val libraries = mutable.Buffer.empty[StatefulLibrary]
  private val dependencies = mutable.Set.empty[(StatefulModule, Dependency)]

  def addModule(id: String, base: File) =
    findModule(id).getOrElse {
      val module = new StatefulModule(base, id, id)
      modules += module
      module
    }

  def findModule(id: String) =
    modules.find(_.id == id)

  def removeModule(id: String) =
    findModule(id).foreach(modules -= _)

  def addLibrary(id: LibraryId): Library =
    findLibrary(id).getOrElse {
      val lib = new StatefulLibrary(id)
      libraries += lib
      lib
    }

  def findLibrary(id: LibraryId) =
    libraries.find(_.id == id)

  def removeLibrary(id: LibraryId): Unit =
    findLibrary(id).foreach(libraries -= _)

  def addDependency(moduleId: String, dependency: Dependency) =
    findModule(moduleId).foreach { m =>
      dependencies += Tuple2(m, dependency)
    }

  def removeDependency(moduleId: String, dependency: Dependency) =
    findModule(moduleId).foreach { m =>
      dependencies -= Tuple2(m, dependency)
    }

  def copy: Project = this.clone.asInstanceOf[Project]

  def toDataNode: DataNode[ProjectData] = {
    import Helpers._

    val baseFile = new File(base)
    val projectNode = new DataNode(
      ProjectKeys.PROJECT,
      new ProjectData(external.Id, name, baseFile.getAbsolutePath, baseFile.getAbsolutePath),
      null)

    val libraryNodes = libraries.map(_.toDataNode(projectNode))
    val moduleNodes = modules.map(_.toDataNode(projectNode))

    for {
      (module, dependency) <- dependencies
      moduleNode <- moduleNodes
      if moduleNode.getData.getId == module.id
      depNode <- dependency.toDataNode(moduleNode, libraryNodes, moduleNodes)
    } moduleNode.addChild(depNode)

    libraryNodes.foreach(projectNode.addChild)
    moduleNodes.foreach(projectNode.addChild)

    projectNode
  }
}

class StatefulModule(val base: File, var id: String, var name: String) extends Module {
  private val paths = mutable.Set.empty[Path]

  def addPath(path: Path) =
    paths += path

  def hasPath(path: Path) =
    paths.contains(path)

  def removePath(path: Path) =
    paths -= path

  def toDataNode(parent: DataNode[ProjectData]): DataNode[ModuleData] = {
    import Helpers._

    val ideModulePath = parent.getData(ProjectKeys.PROJECT)
                              .getIdeProjectFileDirectoryPath + "/.idea/modules"
    val moduleData = new ModuleData(
        id, external.Id, StdModuleTypes.JAVA.getId,
        name, ideModulePath, base.getAbsolutePath)
    val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)
    moduleData.setInheritProjectCompileOutputPath(false)

    moduleNode.addChild(paths.toDataNode(moduleNode))

    moduleNode
  }
}

class StatefulLibrary(val id: LibraryId) extends Library {
  private val artifacts = mutable.Set.empty[Artifact]

  def addArtifact(artifact: Artifact) =
    artifacts += artifact

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

  implicit class RichDependency(dependency: Dependency) {
    def toDataNode(parent: DataNode[ModuleData],
                   libraryNodes: Seq[DataNode[LibraryData]],
                   moduleNodes: Seq[DataNode[ModuleData]]): Option[DataNode[_ <: AbstractDependencyData[_]]] = {

      def addLibraryDependency
          (libraryId: LibraryId, configuration: Configuration):
          Option[DataNode[_ <: AbstractDependencyData[_]]] =
        libraryNodes.find(_.getData.getExternalName == libraryId.toString).map { libraryNode =>
          val libDepData = new LibraryDependencyData(parent.getData, libraryNode.getData, LibraryLevel.PROJECT)
          setScopeByConf(libDepData, configuration)
          new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libDepData, parent)
        }

      def addModuleDependency
          (dependencyId: String, configuration: Configuration):
          Option[DataNode[_ <: AbstractDependencyData[_]]] =
        moduleNodes.find(_.getData.getId == dependencyId).map { masterNode =>
          val moduleDepData = new ModuleDependencyData(parent.getData, masterNode.getData)
          setScopeByConf(moduleDepData, configuration)
          new DataNode(ProjectKeys.MODULE_DEPENDENCY, moduleDepData, parent)
        }

      def setScopeByConf(dependencyData: AbstractDependencyData[_], configuration: Configuration): Unit =
        configuration match {
          case Configuration.Compile  => dependencyData.setScope(DependencyScope.COMPILE)
          case Configuration.Test     => dependencyData.setScope(DependencyScope.TEST)
          case Configuration.Runtime  => dependencyData.setScope(DependencyScope.RUNTIME)
          case Configuration.Provided => dependencyData.setScope(DependencyScope.PROVIDED)
        }

      dependency match {
        case Dependency.Library(libId, conf) => addLibraryDependency(libId, conf)
        case Dependency.Module(depId, conf)  => addModuleDependency(depId, conf)
      }
    }
  }
}