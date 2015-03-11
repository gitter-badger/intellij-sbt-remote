package com.dancingrobot84.sbt.remote.project.extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.protocol._
import sbt.serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class InternalDependenciesExtractor extends Extractor.Adapter {

  def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
      _ <- watchSettingKey[BuildDependencies]("buildDependencies")(buildDependenciesWatcher)
      _ <- watchTaskKey[Seq[Attributed[File]]]("internalDependencyClasspath")(classpathWatcher)
    } yield Unit
  }

  private def classpathWatcher
      (key: ScopedKey, result: Try[Seq[Attributed[File]]])
      (implicit ctx: Extractor.Context): Unit = {
    println("TODO: internalDependencyClasspath")
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
