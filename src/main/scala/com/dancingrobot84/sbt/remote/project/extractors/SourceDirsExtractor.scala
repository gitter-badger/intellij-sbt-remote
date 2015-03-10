package com.dancingrobot84.sbt.remote
package project
package extractors

import java.io.File
import com.dancingrobot84.sbt.remote.project.structure.Path
import sbt.protocol.ScopedKey
import sbt.serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
class SourceDirsExtractor extends Extractor.Adapter {

  def doAttach(implicit ctx: Extractor.Context): Future[Unit] =
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
      last <- watchSettingKey[File]("test:classDirectory")(pathWatcher(Path.TestOutput))
    } yield Unit

  private def baseDirWatcher
      (key: ScopedKey, result: Try[File])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(baseDir) => ifProjectAccepted(key.scope.project) { p =>
      ctx.project.addModule(p.name, baseDir)
      if (baseDir == new File(ctx.project.base))
        ctx.project.name = p.name
      ctx.logger.warn(s"Module '${p.name}' sets '$baseDir' as baseDirectory")
    }
    case Failure(exc) =>
      ctx.logger.error("Failed retrieving 'baseDirectory' key", exc)
  }

  private def nameWatcher
      (key: ScopedKey, result: Try[String])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(name) => ifProjectAccepted(key.scope.project) { p =>
      ctx.project.findModule(p.name).foreach { module =>
        module.name = name
        if (ctx.project.base == module.base.toURI)
          ctx.project.name = name
        ctx.logger.warn(s"Module '${p.name}' changes its name to '$name'")
      }
    }
    case Failure(exc) =>
      ctx.logger.error("Failed retrieving 'name' key", exc)
  }

  private def pathsWatcher
      (pathTrans: File => Path)(key: ScopedKey, result: Try[Seq[File]])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(paths) => ifProjectAccepted(key.scope.project) { p =>
      paths.foreach { path =>
        ctx.logger.warn(s"Module '${p.name}' adds '$path' as '${pathTrans(path).getClass.getSimpleName}'")
        ctx.project.findModule(p.name).foreach(_.addPath(pathTrans(path)))
      }
    }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving '$key' key", exc)
  }

  private def pathWatcher
      (pathTrans: File => Path)(key: ScopedKey, result: Try[File])
      (implicit ctx: Extractor.Context): Unit = result match {
    case Success(path) => ifProjectAccepted(key.scope.project) { p =>
      ctx.logger.warn(s"Module '${p.name}' adds '$path' as '${pathTrans(path).getClass.getSimpleName}'")
      ctx.project.findModule(p.name).foreach(_.addPath(pathTrans(path)))
    }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving '$key' key", exc)
  }

}
