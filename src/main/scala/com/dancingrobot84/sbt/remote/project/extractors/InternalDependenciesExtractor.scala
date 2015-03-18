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
abstract class InternalDependenciesExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
      _ <- watchSettingKey[BuildDependencies]("buildDependencies")(buildDependenciesWatcher)
      _ <- watchTaskKey[Seq[Attributed[File]]]("unmanagedJars")(classpathWatcher(Configuration.Compile))
      _ <- watchTaskKey[Seq[Attributed[File]]]("test:unmanagedJars")(classpathWatcher(Configuration.Test))
    } yield Unit
  }

  private def classpathWatcher(conf: Configuration)
      (key: ScopedKey, result: Try[Seq[Attributed[File]]])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(jars) => ifProjectAccepted(key.scope.project) { p =>
      withProject { project =>
        for {
          module <- project.modules.find(_.id == p.name)
          jar <- jars
          lib = project.addLibrary(Library.Id.forUnmanagedJars(module.id, conf))
        } {
          logger.warn(s"Library '${lib.id}' adds '${jar.data}' to itself")
          lib.addArtifact(Artifact.Binary(jar.data))
          module.addDependency(Dependency.Library(lib.id, conf))
        }
      }
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving '$key' key", exc)
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
        withProject(_.modules.find(_.id == projectRef.name).foreach { module =>
            module.addDependency(Dependency.Module(dependency.project.name, configuration))
        })
        logger.warn(s"Module '${projectRef.name}' depends on '${dependency.project.name}'")
      }
    case Failure(exc) =>
      logger.error(s"Failed retrieving 'buildDependencies' key", exc)
  }

}
