package com.dancingrobot84.sbt.remote
package project
package extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.protocol._
import sbt.serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
abstract class InternalDependenciesExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    logger.info(Bundle("sbt.remote.import.extractInternalDependencies"))
    for {
      _ <- watchTaskKey[Seq[Attributed[File]]]("unmanagedJars")(classpathWatcher(Configuration.Compile))
      _ <- watchTaskKey[Seq[Attributed[File]]]("test:unmanagedJars")(classpathWatcher(Configuration.Test))
    } yield processInterProjectDependencies
  }

  private def classpathWatcher(conf: Configuration)(key: ScopedKey, result: Try[Seq[Attributed[File]]])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { jars =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          for {
            module <- project.modules.find(_.id == p.name)
            jar <- jars
            lib = project.addLibrary(Library.Id.forUnmanagedJars(module.id, conf))
            artifact = Artifact.Binary(jar.data)
          } {
            lib.addArtifact(artifact)
            module.addDependency(Dependency.Library(lib.id, conf))
            logger.info(Bundle("sbt.remote.import.library.addArtifactAs", lib.id, artifact.file, artifact.getClass.getSimpleName))
          }
        }
      }
    }

  private def processInterProjectDependencies(implicit ctx: Extractor.Context): Unit =
    ctx.acceptedProjects.foreach { projectRef =>
      projectRef.dependencies.foreach { dependencies =>
        for {
          dependency <- dependencies.classpath
          configuration = dependency.configuration
            .flatMap(Configuration.fromString)
            .getOrElse(Configuration.Compile)
        } {
          withProject(_.modules.find(_.id == projectRef.id.name).foreach { module =>
            module.addDependency(Dependency.Module(dependency.project.name, configuration))
            logger.info(Bundle("sbt.remote.import.module.dependOn", module.id, dependency.project.name))
          })
        }
      }
    }
}
