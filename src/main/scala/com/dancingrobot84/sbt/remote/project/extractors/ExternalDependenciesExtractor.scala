package com.dancingrobot84.sbt.remote.project.extractors

import sbt.protocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * @author: Nikolay Obedin
 * @since: 3/11/15.
 */
class ExternalDependenciesExtractor extends Extractor.Adapter {

  def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    for {
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
}
