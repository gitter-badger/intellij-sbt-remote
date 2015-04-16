package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.{ Project, ProjectRef }
import sbt.client._
import sbt.protocol.{ MinimalBuildStructure, MinimalProjectStructure, ProjectReference, ScopedKey }
import sbt.serialization._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Extractor {
  def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit]
  def detach(): Unit

  def attachOnce(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit] = {
    val f = attach(client, projectRef, logger)
    f.onComplete(_ => detach)
    f
  }
}

trait Context {
  def getContext(client: SbtClient, logger: Logger, acceptedProjects: Vector[MinimalProjectStructure], projectRef: ProjectRef): Extractor.Context
}

abstract class ExtractorAdapter extends Extractor with Context {
  protected def doAttach(implicit ctx: Extractor.Context): Future[Unit]

  private val subscriptions = mutable.Buffer.empty[Subscription]

  protected def addSubscription(s: Subscription) =
    subscriptions += s

  type WatchResult[T] = (ScopedKey, Try[T])

  protected def watchSettingKey[T](key: String)(valueListener: ValueListener[T])(
    implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Extractor.Context): Future[Seq[WatchResult[T]]] =
    doWatch(key) { scopedKey =>
      val p = Promise[WatchResult[T]]()
      addSubscription(ctx.client.watch(TaskKey[T](scopedKey)) { (key, result) =>
        valueListener(key, result)
        p.trySuccess((key, result))
      })
      p.future
    }

  protected def watchTaskKey[T](key: String)(valueListener: ValueListener[T])(
    implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Extractor.Context): Future[Seq[WatchResult[T]]] =
    doWatch(key) { scopedKey =>
      val p = Promise[WatchResult[T]]()
      addSubscription(ctx.client.watch(TaskKey[T](scopedKey)) { (key, result) =>
        valueListener(key, result)
        p.trySuccess((key, result))
      })
      p.future
    }

  private def doWatch[T](key: String)(keyProcessor: ScopedKey => Future[WatchResult[T]])(
    implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Extractor.Context): Future[Seq[WatchResult[T]]] = {
    val allProjectKeys = ctx.acceptedProjects.map(pr => s"${pr.id.name}/$key")
    for {
      allKeys <- Future.traverse(allProjectKeys)(ctx.client.lookupScopedKey).map(_.flatten.distinct.toSeq)
      results <- Future.sequence(allKeys
        .filter(_.scope.project.exists(p => ctx.acceptedProjects.exists(_.id == p)))
        .map(keyProcessor))
    } yield results
  }

  protected def ifProjectAccepted(project: Option[ProjectReference])(onAccept: ProjectReference => Unit)(
    implicit ctx: Extractor.Context): Unit =
    project.foreach { p =>
      val base = withProject(_.base)
      if (p.build == base && ctx.acceptedProjects.exists(_.id == p))
        onAccept(p)
    }

  protected def withProject[T](trans: Project => T)(implicit ctx: Extractor.Context): T =
    ctx.withProject(trans)

  protected def logger(implicit ctx: Extractor.Context): Logger =
    ctx.logger

  protected def logOnWatchFailure[T](key: ScopedKey, result: Try[T])(onSuccess: T => Unit)(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(value) => onSuccess(value)
    case Failure(exc)   => ctx.logger.error(Bundle("sbt.remote.import.failedRetrievingKey", key))
  }

  override def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit] = {
    val initPromise = Promise[Unit]()

    addSubscription(client.watchBuild {
      case MinimalBuildStructure(builds, allProjects) =>
        val buildOpt = builds.find(_ == projectRef.project.base).headOption

        buildOpt.map { build =>
          val acceptedProjects = allProjects.filter { p =>
            p.id.build == build && p.plugins.contains("sbt.plugins.JvmPlugin")
          }

          if (acceptedProjects.isEmpty)
            initPromise.failure(new Error(Bundle("sbt.remote.import.noSuitableModulesFound")))
          else
            doAttach(getContext(client, logger, acceptedProjects, projectRef)).onComplete(initPromise.tryComplete)
        }.getOrElse {
          initPromise.failure(new Error(Bundle("sbt.remote.import.noProjectFound")))
        }
    })

    initPromise.future
  }

  override def detach(): Unit =
    subscriptions.foreach(_.cancel())
}

trait SynchronizedContext extends Context {
  override def getContext(client0: SbtClient,
                          logger0: Logger,
                          acceptedProjects0: Vector[MinimalProjectStructure],
                          projectRef0: ProjectRef) = new Extractor.Context {
    override val client = client0
    override val logger = logger0
    override val acceptedProjects = acceptedProjects0
    override def withProject[T](trans: Project => T): T = projectRef0.synchronized {
      val result = trans(projectRef0.project)
      projectRef0.project = projectRef0.project.copy
      result
    }
  }
}

object Extractor {
  trait Context {
    val client: SbtClient
    val logger: Logger
    val acceptedProjects: Vector[MinimalProjectStructure]
    def withProject[T](trans: Project => T): T
  }
}
