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
import scala.util.Try

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
abstract class BasicsExtractor extends ExtractorAdapter {

  override def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    logger.info(Bundle("sbt.remote.import.extractBasicSetting"))
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
          project.addModule(p, baseDir)
          if (baseDir == new File(project.base))
            project.name = p.name
          logger.info(Bundle("sbt.remote.import.module.setContentRoot", p.name, baseDir))
        }
      }
    }

  private def nameWatcher(key: ScopedKey, result: Try[String])(implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { name =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          project.modules.find(_.id == p).foreach { module =>
            module.name = name
            if (project.base == module.base.toURI)
              project.name = name
            logger.info(Bundle("sbt.remote.import.module.setName", module.id.name, name))
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
            project.modules.find(_.id == p).foreach { module =>
              if (FileUtil.isAncestor(module.base, path, false)) {
                module.addPath(pathTrans(path))
                logger.info(Bundle("sbt.remote.import.module.addPathTo", module.id.name, path, pathTrans(path).getClass.getSimpleName))
              } else {
                logger.warn(Bundle("sbt.remote.import.module.pathOutsideOfContentRoot", path, module.id.name))
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
        logger.info(Bundle("sbt.remote.import.module.addPathTo", p.name, path, pathTrans(path).getClass.getSimpleName))
        withProject { project =>
          project.modules.find(_.id == p).foreach(_.addPath(pathTrans(path)))
        }
      }
    }

  private def scalacOptionsWatcher(key: ScopedKey, result: Try[Seq[String]])(
    implicit ctx: Extractor.Context): Unit =
    logOnWatchFailure(key, result) { options =>
      ifProjectAccepted(key.scope.project) { p =>
        withProject { project =>
          project.modules.find(_.id == p).foreach(m => m.scalacOptions = options)
        }
      }
    }
}
