package com.dancingrobot84.sbt.remote.project.extractors

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.ModuleReport
import sbt.protocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * @author: Nikolay Obedin
 * @since: 3/11/15.
 */
class ExternalDependenciesExtractor extends Extractor.Adapter {

  def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
      _ <- watchTaskKey[sbt.UpdateReport]("update")(updateWatcher)
    } yield Unit
  }

  private def updateWatcher
      (key: ScopedKey, result: Try[sbt.UpdateReport])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(updateReport) => ifProjectAccepted(key.scope.project) { p =>
      updateReport.configurations.foreach { confReport =>
        Configuration.fromString(confReport.configuration).foreach { conf =>
          confReport.modules.foreach(m => addLibraryDependency(p.name, m, conf))
        }
      }
    }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving 'update' key", exc)
  }

  private def addLibraryDependency
      (moduleId: Module.Id, moduleReport: ModuleReport, configuration: Configuration)
      (implicit ctx: Extractor.Context): Unit = {
    val libId = Library.Id.fromSbtModuleId(moduleReport.module)
    val artifacts = moduleReport.artifacts.map(af => Artifact.Binary(af._2)).toSet
    withProject { project =>
      project.modules.find(_.id == moduleId).foreach { module =>
        val allLibs      = project.libraries.find(_.id ~= libId)
        val lastVersion  = allLibs.foldLeft(-1)(_ max _.id.internalVersion)
        val libInProject = allLibs.filter(_.artifacts == artifacts).headOption
                                  .getOrElse(project.addLibrary(libId.copy(internalVersion = lastVersion + 1)))
        artifacts.foreach(libInProject.addArtifact)
        module.addDependency(Dependency.Library(libInProject.id, configuration))
      }
    }
  }
}
