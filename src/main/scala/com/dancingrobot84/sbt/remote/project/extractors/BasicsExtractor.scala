package com.dancingrobot84.sbt.remote
package project
package extractors

import java.io.File
import com.dancingrobot84.sbt.remote.project.structure.Path
import com.intellij.openapi.util.io.FileUtil
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

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    logger.info("Extracting basic settings...")
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
      _ <- watchSettingKey[Seq[String]]("scalacOptions")(scalacOptionsWatcher)
    } yield Unit
  }

  private def baseDirWatcher(key: ScopedKey, result: Try[File])(implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { baseDir =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          project.addModule(p.name, baseDir)
          if (baseDir == new File(project.base))
            project.name = p.name
          logger.info(s"Module '${p.name}': Set 'ContentRoot' to '$baseDir'")
        }
      }
    }

  private def nameWatcher(key: ScopedKey, result: Try[String])(implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { name =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          project.modules.find(_.id == p.name).foreach { module =>
            module.name = name
            if (project.base == module.base.toURI)
              project.name = name
            logger.info(s"Module '${module.id}': Set 'Name' to '$name'")
          }
        }
      }
    }

  private def pathsWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[Seq[File]])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { paths =>
      ifProjectAccepted(key.scope.project) { p =>
        paths.foreach { path =>
          withProject { project =>
            project.modules.find(_.id == p.name).foreach { module =>
              if (FileUtil.isAncestor(module.base, path, false)) {
                module.addPath(pathTrans(path))
                logger.info(s"Module '${module.id}': Add '$path' to '${pathTrans(path).getClass.getSimpleName}'")
              } else {
                logger.warn(s"'$path' is not added because it is outside of module's '${module.id}' content root")
              }
            }
          }
        }
      }
    }

  private def pathWatcher(pathTrans: File => Path)(key: ScopedKey, result: Try[File])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { path =>
      ifProjectAccepted(key.scope.project) { p =>
        logger.info(s"Module '${p.name}': Add '$path' to '${pathTrans(path).getClass.getSimpleName}'")
        withProject { project =>
          project.modules.find(_.id == p.name).foreach(_.addPath(pathTrans(path)))
        }
      }
    }

  private def scalacOptionsWatcher(key: ScopedKey, result: Try[Seq[String]])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { options =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          project.modules.find(_.id == p.name).foreach(m => m.scalacOptions = options)
        }
      }
    }
}
