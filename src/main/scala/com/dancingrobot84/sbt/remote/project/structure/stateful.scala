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
  private val dependencies = mutable.Set.empty[(StatefulModule, Dependency)]

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

  def addDependency(moduleId: String, dependency: Dependency) = this.synchronized {
    findModule(moduleId).foreach { m =>
      dependencies += Tuple2(m, dependency)
    }
  }

  def removeDependency(moduleId: String, dependency: Dependency) = this.synchronized {
    findModule(moduleId).foreach { m =>
      dependencies -= Tuple2(m, dependency)
    }
  }

  def toDataNode: DataNode[ProjectData] = {
    val baseFile = new File(base)
    val projectNode = new DataNode(
      ProjectKeys.PROJECT,
      new ProjectData(external.Id, name, baseFile.getAbsolutePath, baseFile.getAbsolutePath),
      null)

    val libraryNodes = libraries.map(_.toDataNode(projectNode))
    val moduleNodes = modules.map(_.toDataNode(projectNode))

    dependencies.foreach { case (module, dependency) =>
      dependency match {
        case Dependency.Library(libId, conf) =>
          for {
            moduleNode <- moduleNodes
            if moduleNode.getData.getId == module.id
            libraryNode <- libraryNodes
            if libraryNode.getData.getExternalName == libId.toString
          } {
            val libDepData = new LibraryDependencyData(moduleNode.getData, libraryNode.getData, LibraryLevel.PROJECT)
            conf match {
              case Configuration.Compile => libDepData.setScope(DependencyScope.COMPILE)
              case Configuration.Test => libDepData.setScope(DependencyScope.TEST)
              case _ => // TODO: implement
            }
            moduleNode.addChild(new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libDepData, moduleNode))
          }
        case Dependency.Module(depBase, conf) =>
          for {
            dependentNode <- moduleNodes
            if dependentNode.getData.getId == module.id
            masterModule <- modules.filter(_.hasPath(Path.Output(depBase)))
            masterNode   <- moduleNodes
            if masterNode.getData.getId == masterModule.id
          } {
            val moduleDepData = new ModuleDependencyData(dependentNode.getData, masterNode.getData)
            conf match {
              case Configuration.Compile => moduleDepData.setScope(DependencyScope.COMPILE)
              case Configuration.Test => moduleDepData.setScope(DependencyScope.TEST)
              case _ => // TODO: implement
            }
            dependentNode.addChild(new DataNode(ProjectKeys.MODULE_DEPENDENCY, moduleDepData, dependentNode))
          }
        }
      }

    libraryNodes.foreach(projectNode.addChild)
    moduleNodes.foreach(projectNode.addChild)

    projectNode
  }
}

class StatefulModule(val base: File, @volatile var id: String, @volatile var name: String) extends Module {
  private val paths = mutable.Set.empty[Path]

  def addPath(path: Path) = this.synchronized {
    paths += path
  }

  def hasPath(path: Path) = this.synchronized {
    paths.contains(path)
  }

  def removePath(path: Path) = this.synchronized {
    paths -= path
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