package com.dancingrobot84.sbt.remote.project.extractors

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.protocol._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * @author: Nikolay Obedin
 * @since: 3/11/15.
 */
abstract class ExternalDependenciesExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
      _ <- watchTaskKey[sbt.UpdateReport]("update")(updateWatcher)
    } yield Unit
  }

  private def updateWatcher(key: ScopedKey, result: Try[sbt.UpdateReport])(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(updateReport) => ifProjectAccepted(key.scope.project) { p =>
      regroupConfigurationReports(updateReport.configurations).foreach {
        case (moduleReport, confs) =>
          confs.foreach(conf => addLibraryDependency(p.name, moduleReport, conf))
      }
      updateReport.configurations.find(_.configuration == "scala-tool").foreach(c => setupScalaSdk(p.name, c.modules))
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving 'update' key", exc)
  }

  private def regroupConfigurationReports(reports: Seq[sbt.ConfigurationReport]): Map[sbt.ModuleReport, Set[Configuration]] = {
    val result = mutable.HashMap.empty[sbt.ModuleReport, Set[Configuration]]
    reports.foreach { report =>
      Configuration.fromString(report.configuration).foreach { conf =>
        report.modules.foreach { module =>
          val moduleConfs = result.getOrElse(module, Set.empty)
          result.put(module, moduleConfs + conf)
        }
      }
    }
    result.mapValues { confs =>
      if (confs == Set(Configuration.Test, Configuration.Compile, Configuration.Runtime))
        Set[Configuration](Configuration.Compile)
      else if (confs == Set(Configuration.Test, Configuration.Compile))
        Set[Configuration](Configuration.Provided)
      else
        confs
    }.toMap
  }

  private def addLibraryDependency(moduleId: Module.Id, moduleReport: sbt.ModuleReport, configuration: Configuration)(
    implicit ctx: Extractor.Context): Unit = {
    val libId = Library.Id.fromSbtModuleId(moduleReport.module)
    val artifacts = moduleReport.artifacts.map(af => Artifact.Binary(af._2)).toSet
    if (artifacts.isEmpty) return

    withProject { project =>
      project.modules.find(_.id == moduleId).foreach { module =>
        val allLibs = project.libraries.filter(_.id ~= libId)
        val lastVersion = allLibs.foldLeft(-1)(_ max _.id.internalVersion)
        val libInProject = allLibs.find(_.binaries == artifacts)
          .getOrElse(project.addLibrary(libId.copy(internalVersion = lastVersion + 1)))
        artifacts.foreach { artifact =>
          libInProject.addArtifact(artifact)
          logger.warn(s"Library '${libInProject.id}' adds '${artifact.file}' to itself")
        }
        module.addDependency(Dependency.Library(libInProject.id, configuration))
      }
    }
  }

  private def setupScalaSdk(moduleId: Module.Id, modules: Seq[sbt.ModuleReport])(
    implicit ctx: Extractor.Context): Unit = {
    val classpathLibrariesNames = Seq("scala-library", "scala-compiler", "scala-reflect")
    val classpathModules = modules.filter(m => classpathLibrariesNames.contains(m.module.name))
    classpathModules.find(_.module.name == "scala-library").foreach { scalaLibModule =>
      val classpathFiles = classpathModules.flatMap(_.artifacts.map(_._2))
      val scalaSdk = ScalaSdk(scalaLibModule.module.revision, classpathFiles)
      withProject { project =>
        project.modules.find(_.id == moduleId).foreach(_.scalaSdk = Some(scalaSdk))
      }
    }
  }
}
