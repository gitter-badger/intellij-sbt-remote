package com.dancingrobot84.sbt.remote
package project
package extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.extractors.Extractor.WatchResult
import com.dancingrobot84.sbt.remote.project.structure.Path
import sbt.client.ValueListener
import sbt.protocol.ScopedKey
import sbt.serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
class SourceDirsExtractor extends Extractor.Adapter {

  def doAttach(): Future[Unit] = {
    val initPromise = Promise[Unit]()

    val watchers = Map(
      "unmanagedSourceDirectories"          -> pathsWatcher(Path.Source) _,
      "managedSourceDirectories"            -> pathsWatcher(Path.GenSource) _,
      "test:unmanagedSourceDirectories"     -> pathsWatcher(Path.TestSource) _,
      "test:managedSourceDirectories"       -> pathsWatcher(Path.GenTestSource) _,
      "unmanagedResourceDirectories"        -> pathsWatcher(Path.Resource) _,
      "managedResourceDirectories"          -> pathsWatcher(Path.GenResource) _,
      "test:unmanagedResourceDirectories"   -> pathsWatcher(Path.TestResource) _,
      "test:managedResourceDirectories"     -> pathsWatcher(Path.GenTestResource) _
    )

    def callWatcher[T](watcher: ValueListener[T])(results: Seq[WatchResult[T]]) =
      results.foreach(r => watcher(r._1, r._2))

    watchSettingKey[File]("baseDirectory")(baseDirWatcher)
      .map(callWatcher(baseDirWatcher))
      .flatMap { _ =>
        Future.sequence {
          val sourceFutures = for {
            (key, watcher) <- watchers
            f = watchSettingKey[Seq[File]](key)(watcher)
          } yield f.map(callWatcher(watcher))

          sourceFutures.toSeq ++ Seq(
            watchSettingKey[File]("classDirectory")(pathWatcher(Path.Output))
              .map(callWatcher(pathWatcher(Path.Output))),
            watchSettingKey[File]("test:classDirectory")(pathWatcher(Path.TestOutput))
              .map(callWatcher(pathWatcher(Path.TestOutput)))
          )
        }
      }
      .onComplete(_ => initPromise.success(()))

    initPromise.future
  }

  private def baseDirWatcher(key: ScopedKey, result: Try[File]): Unit = result match {
    case Success(baseDir) => key.scope.project.foreach { p =>
      if (p.build == ctx.project.base && projects.contains(p)) {
        ctx.project.addModule(p.name, baseDir)
        if (baseDir == new File(ctx.project.base))
          ctx.project.name = p.name
      }
    }
    case Failure(exc) =>
      ctx.logger.error("Failed retrieving 'baseDirectory' key", exc)
  }

  private def pathsWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[Seq[File]]): Unit = result match {
    case Success(sources) => key.scope.project.foreach { p =>
      if (p.build == ctx.project.base && projects.contains(p)) {
        sources.foreach { s =>
          ctx.logger.info(s"Got path '$s' for module ${p.name}")
          ctx.project.findModule(p.name).foreach(_.addPath(pathTrans(s)))
        }
      }
    }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving '$key' key", exc)
  }

  private def pathWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[File]): Unit = result match {
    case Success(path) => key.scope.project.foreach { p =>
      if (p.build == ctx.project.base && projects.contains(p)) {
        ctx.logger.info(s"Got path '$path' for module ${p.name}")
        ctx.project.findModule(p.name).foreach(_.addPath(pathTrans(path)))
      }
    }
    case Failure(exc) =>
      ctx.logger.error(s"Failed retrieving '$key' key", exc)
  }

}
