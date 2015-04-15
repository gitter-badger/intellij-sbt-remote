package com.dancingrobot84.sbt.remote.project.extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.protocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * @author: Nikolay Obedin
 * @since: 3/17/15.
 */
abstract class ClassifiersExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    logger.info("Extracting sources and javadocs...")
    for {
      _ <- watchTaskKey[sbt.UpdateReport]("updateClassifiers")(updateWatcher)
    } yield Unit
  }

  private def updateWatcher(key: ScopedKey, result: Try[sbt.UpdateReport])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { updateReport =>
      ifProjectAccepted(key.scope.project) { p =>
        updateReport.configurations.foreach(_.modules.foreach(m => addSources(p.name, m)))
      }
    }

  private def addSources(moduleId: Module.Id, moduleReport: sbt.ModuleReport)(
    implicit ctx: Extractor.Context): Unit = {
    val libId = Library.Id.fromSbtModuleId(moduleReport.module)
    val artifacts = {
      val grouped = moduleReport.artifacts.groupBy(_._1.`type`)
      val sources = grouped.getOrElse("src", Seq.empty)
      val docs = grouped.getOrElse("doc", Seq.empty)
      val toArtifact = (t: File => Artifact) => (f: (sbt.Artifact, File)) => t(f._2)
      (sources.map(toArtifact(Artifact.Source)) ++
        docs.map(toArtifact(Artifact.Doc))).toSet
    }
    if (artifacts.isEmpty) return

    withProject { project =>
      for {
        module <- project.modules.find(_.id == moduleId)
        lib <- project.libraries.filter(_.id ~= libId)
        artifact <- artifacts
      } {
        lib.addArtifact(artifact)
        logger.info(s"Library '${lib.id}': Add '${artifact.file}' as '${artifact.getClass.getSimpleName}'")
      }
    }
  }
}
