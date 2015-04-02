package com.dancingrobot84.sbt.remote
package project
package extractors

import java.io.File
import com.dancingrobot84.sbt.remote.project.structure.Path
import sbt.protocol.ScopedKey
import sbt.serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
abstract class BasicsExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] =
    for {
      _ <- watchSettingKey[File]("baseDirectory")(baseDirWatcher)
      _ <- watchSettingKey[String]("name")(nameWatcher)
      _ <- watchSettingKey[Seq[File]]("unmanagedSourceDirectories")(pathsWatcher(Path.Source))
      _ <- watchSettingKey[Seq[File]]("managedSourceDirectories")(pathsWatcher(Path.GenSource))
      _ <- watchSettingKey[Seq[File]]("test:unmanagedSourceDirectories")(pathsWatcher(Path.TestSource))
      _ <- watchSettingKey[Seq[File]]("test:managedSourceDirectories")(pathsWatcher(Path.GenTestSource))
      _ <- watchSettingKey[Seq[File]]("unmanagedResourceDirectories")(pathsWatcher(Path.Resource))
      _ <- watchSettingKey[Seq[File]]("managedResourceDirectories")(pathsWatcher(Path.GenResource))
      _ <- watchSettingKey[Seq[File]]("test:unmanagedResourceDirectories")(pathsWatcher(Path.TestResource))
      _ <- watchSettingKey[Seq[File]]("test:managedResourceDirectories")(pathsWatcher(Path.GenTestResource))
      _ <- watchSettingKey[File]("classDirectory")(pathWatcher(Path.Output))
      _ <- watchSettingKey[File]("test:classDirectory")(pathWatcher(Path.TestOutput))
      _ <- watchSettingKey[String]("scalaVersion")(versionWatcher)
      _ <- watchSettingKey[Seq[String]]("scalacOptions")(scalacOptionsWatcher)
    } yield Unit

  private def baseDirWatcher(key: ScopedKey, result: Try[File])(implicit ctx: Extractor.Context): Unit = result match {
    case Success(baseDir) => ifProjectAccepted(key.scope.project) { p =>
      withProject { project =>
        project.addModule(p.name, baseDir)
        if (baseDir == new File(project.base))
          project.name = p.name
      }
      logger.warn(s"Module '${p.name}' sets '$baseDir' as baseDirectory")
    }
    case Failure(exc) =>
      logger.error("Failed retrieving 'baseDirectory' key", exc)
  }

  private def nameWatcher(key: ScopedKey, result: Try[String])(implicit ctx: Extractor.Context): Unit = result match {
    case Success(name) => ifProjectAccepted(key.scope.project) { p =>
      withProject { project =>
        project.modules.find(_.id == p.name).foreach { module =>
          module.name = name
          if (project.base == module.base.toURI)
            project.name = name
        }
      }
      logger.warn(s"Module '${p.name}' changes its name to '$name'")
    }
    case Failure(exc) =>
      logger.error("Failed retrieving 'name' key", exc)
  }

  private def pathsWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[Seq[File]])(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(paths) => ifProjectAccepted(key.scope.project) { p =>
      paths.foreach { path =>
        withProject { project =>
          project.modules.find(_.id == p.name).foreach(_.addPath(pathTrans(path)))
        }
        logger.warn(s"Module '${p.name}' adds '$path' as '${pathTrans(path).getClass.getSimpleName}'")
      }
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving '$key' key", exc)
  }

  private def pathWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[File])(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(path) => ifProjectAccepted(key.scope.project) { p =>
      logger.warn(s"Module '${p.name}' adds '$path' as '${pathTrans(path).getClass.getSimpleName}'")
      withProject { project =>
        project.modules.find(_.id == p.name).foreach(_.addPath(pathTrans(path)))
      }
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving '$key' key", exc)
  }

  private def versionWatcher(key: ScopedKey, result: Try[String])(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(scalaVersion) => ifProjectAccepted(key.scope.project) { p =>
      withProject { project =>
        project.modules.find(_.id == p.name).foreach(m => m.scalaVersion = Some(scalaVersion))
      }
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving '$key' key", exc)
  }

  private def scalacOptionsWatcher(key: ScopedKey, result: Try[Seq[String]])(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(options) => ifProjectAccepted(key.scope.project) { p =>
      withProject { project =>
        project.modules.find(_.id == p.name).foreach(m => m.scalacOptions = options)
      }
    }
    case Failure(exc) =>
      logger.error(s"Failed retrieving '$key' key", exc)
  }

}
