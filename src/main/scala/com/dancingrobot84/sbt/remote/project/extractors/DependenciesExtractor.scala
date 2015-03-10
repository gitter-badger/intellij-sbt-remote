package com.dancingrobot84.sbt.remote.project.extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.client.{RawValueListener, TaskKey}
import sbt.protocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Try, Success, Failure}

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class DependenciesExtractor extends Extractor.Adapter {

  def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
      _ <- watchSettingKey[BuildDependencies]("buildDependencies")(buildDependenciesWatcher)
      _ <- watchTaskKey[sbt.UpdateReport]("update")(updateWatcher)
    } yield Unit
  }

  private def updateWatcher
      (key: ScopedKey, result: Try[sbt.UpdateReport])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(updateReport) =>
      println("TODO: update")
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving 'update' key", exc)
  }

  private def buildDependenciesWatcher
      (key: ScopedKey, result: Try[BuildDependencies])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(buildDependencies) =>
      buildDependencies.classpath.foreach { case (projectRef, classpathDeps) =>
        classpathDeps.foreach { dep =>
          val conf = Configuration.Compile // TODO: check configuration
          ctx.project.addDependency(projectRef.name, Dependency.Module(dep.project.name, conf))
          ctx.logger.warn(s"Module '${projectRef.name}'; depends on '${dep.project.name}'")
        }
      }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving 'buildDependencies' key", exc)
  }

}
