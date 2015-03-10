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
      for {
        (projectRef, classpathDeps) <- buildDependencies.classpath
        dependency <- classpathDeps
        configuration = dependency.configuration
                                  .flatMap(Configuration.fromString)
                                  .getOrElse(Configuration.Compile)
      } {
        ctx.project.addDependency(projectRef.name, Dependency.Module(dependency.project.name, configuration))
        ctx.logger.warn(s"Module '${projectRef.name}'; depends on '${dependency.project.name}'")
      }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving 'buildDependencies' key", exc)
  }

}
