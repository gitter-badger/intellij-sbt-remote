package com.dancingrobot84.sbt.remote.external

import java.io.File

import com.dancingrobot84.sbt.remote.external
import com.dancingrobot84.sbt.remote.project.structure._
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.model.{ DataNode, ProjectKeys }
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.data.ScalaSdkData

/**
 * Implicit conversions from project.structure.traits to External System's DataNodes
 *
 * @author: Nikolay Obedin
 * @since: 12/03/2015.
 */
object DataNodeConversions {
  implicit class DataNodeProject(project: Project) {
    def toDataNode: DataNode[ProjectData] = {
      val baseFile = new File(project.base)
      val projectNode = new DataNode(
        ProjectKeys.PROJECT,
        new ProjectData(external.Id, project.name, baseFile.getAbsolutePath, baseFile.getAbsolutePath),
        null)

      val libraryNodes = project.libraries.map(_.toDataNode(projectNode))
      val moduleNodes = project.modules.map(_.toDataNode(projectNode))
      val dependencies = project.modules.flatMap(m => m.dependencies.map(d => (m, d)))

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

  implicit class DataNodeModule(module: Module) {
    def toDataNode(parent: DataNode[ProjectData]): DataNode[ModuleData] = {
      val ideModulePath = parent.getData
        .getIdeProjectFileDirectoryPath + "/.idea/modules"
      val moduleData = new ModuleData(
        module.id, external.Id, StdModuleTypes.JAVA.getId,
        module.name, ideModulePath, module.base.getCanonicalPath)
      val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)

      moduleData.setInheritProjectCompileOutputPath(false)
      moduleNode.addChild(module.paths.toDataNode(moduleNode))

      module.scalaSdk.foreach {
        case ScalaSdk(version, compilerClasspath) =>
          val sdkData = new ScalaSdkData(Id, Version(version), "", compilerClasspath, module.scalacOptions)
          moduleNode.addChild(new DataNode(ScalaSdkData.Key, sdkData, moduleNode))
      }

      val tasksNodes = module.tasks.map(_.toDataNode(moduleNode))
      tasksNodes.foreach(moduleNode.addChild)

      moduleNode
    }
  }

  implicit class DataNodeLibrary(library: Library) {
    def toDataNode(parent: DataNode[ProjectData]): DataNode[LibraryData] = {
      val lib = new LibraryData(external.Id, library.id.toString, false)
      library.artifacts.foreach {
        case Artifact.Binary(path) => lib.addPath(LibraryPathType.BINARY, path.getAbsolutePath)
        case Artifact.Source(path) => lib.addPath(LibraryPathType.SOURCE, path.getAbsolutePath)
        case Artifact.Doc(path)    => lib.addPath(LibraryPathType.DOC, path.getAbsolutePath)
      }
      new DataNode(ProjectKeys.LIBRARY, lib, parent)
    }
  }

  implicit class DataNodeTask(task: Task) {
    def toDataNode(parent: DataNode[ModuleData]): DataNode[TaskData] = {
      val taskData = new TaskData(Id, task.name, parent.getData.getLinkedExternalProjectPath, task.name)
      new DataNode(ProjectKeys.TASK, taskData, parent)
    }
  }

  implicit class DataNodeSetOfPath(paths: Set[Path]) {
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
        case path @ (Path.Resource(_) | Path.GenResource(_)) =>
          data.storePath(ExternalSystemSourceType.RESOURCE, path.base.getAbsolutePath)
        case path @ (Path.TestResource(_) | Path.GenTestResource(_)) =>
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

  implicit class DataNodeDependency(dependency: Dependency) {
    def toDataNode(parent: DataNode[ModuleData],
                   libraryNodes: Set[DataNode[LibraryData]],
                   moduleNodes: Set[DataNode[ModuleData]]): Option[DataNode[_ <: AbstractDependencyData[_]]] = {

        def addLibraryDependency(libraryId: Library.Id, configuration: Configuration): Option[DataNode[_ <: AbstractDependencyData[_]]] =
          libraryNodes.find(_.getData.getExternalName == libraryId.toString).map { libraryNode =>
            val libDepData = new LibraryDependencyData(parent.getData, libraryNode.getData, LibraryLevel.PROJECT)
            setScopeByConf(libDepData, configuration)
            new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libDepData, parent)
          }

        def addModuleDependency(dependencyId: String, configuration: Configuration): Option[DataNode[_ <: AbstractDependencyData[_]]] =
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
