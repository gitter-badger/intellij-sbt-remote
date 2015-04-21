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
 * Extractor has the notion of algorithm that reads distinct part of a project
 * (say 'folder structure' or 'external dependencies') from server.
 * In the future when sbt server will be smart enough to reload automatically on build
 * changes and send us updated information we could get rid of ExternalSystem because
 * of its synchronous behaviour. That's why all these `attach/detach` methods for: to
 * dynamically enable/disable extractors on some project.
 *
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Extractor {
  /**
   * Attach to specified sbt `client` which corresponds to IDEA's `projectRef`.
   * It is user's responsibility to provide matching `client` and `projectRef`.
   * "No project found" error will be fired if `client` does not match `projectRef`.
   */
  def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit]

  /**
   * Detach
   * TODO: detach from specified project/client only
   */
  def detach(): Unit

  def attachOnce(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit] = {
    val f = attach(client, projectRef, logger)
    f.onComplete(_ => detach)
    f
  }
}

/**
 * Context trait adds a wrapper for settings common for all extractors in `Extractor.Context`.
 * See `Extractor.Context` documentation for more information about its goals.
 */
trait Context {
  def getContext(client: SbtClient, logger: Logger, acceptedProjects: Vector[MinimalProjectStructure], projectRef: ProjectRef): Extractor.Context
}

/**
 * Correctly handles attachments/detachments and introduces a couple of
 * utility functions.
 * It is STRONGLY RECOMMENDED to inherit Adapter and NOT Extractor itself.
 */
abstract class ExtractorAdapter extends Extractor with Context {
  protected def doAttach(implicit ctx: Extractor.Context): Future[Unit]

  private val subscriptions = mutable.Buffer.empty[Subscription]

  protected def addSubscription(s: Subscription) =
    subscriptions += s

  type WatchResult[T] = (ScopedKey, Try[T])

  /**
   * Watch build setting `key` for all accepted projects where it was defined.
   */
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

  /**
   * Watch build task `key` for all accepted projects where it was defined.
   */
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
    val allProjectKeys = ctx.acceptedProjects.map(pr => s"{${pr.id.build}}${pr.id.name}/$key")
    for {
      allKeys <- Future.traverse(allProjectKeys)(ctx.client.lookupScopedKey).map(_.flatten.distinct.toSeq)
      results <- Future.sequence(allKeys
        .filter(_.scope.project.exists(p => ctx.acceptedProjects.exists(_.id == p)))
        .map(keyProcessor))
    } yield results
  }

  /**
   * Check whether `project` is accepted
   * (its build belongs to a project this extractor is attached to and all necessary sbt plugins are enabled)
   */
  protected def ifProjectAccepted(project: Option[ProjectReference])(onAccept: ProjectReference => Unit)(
    implicit ctx: Extractor.Context): Unit =
    project.foreach { p =>
      if (ctx.acceptedProjects.exists(_.id == p))
        onAccept(p)
    }

  /**
   * Transform attached project with `trans`
   */
  protected def withProject[T](trans: Project => T)(implicit ctx: Extractor.Context): T =
    ctx.withProject(trans)

  protected def logger(implicit ctx: Extractor.Context): Logger =
    ctx.logger

  /**
   * Wrap `key` `result` calling `onSuccess` if it is Success and logging
   * `key` to `logger` if it is Failure
   */
  protected def logOnWatchFailure[T](key: ScopedKey, result: Try[T])(onSuccess: T => Unit)(
    implicit ctx: Extractor.Context): Unit = result match {
    case Success(value) => onSuccess(value)
    case Failure(exc)   => ctx.logger.error(Bundle("sbt.remote.import.failedRetrievingKey", key))
  }

  // TODO: check for being already attached
  override def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): Future[Unit] = {
    val initPromise = Promise[Unit]()

    addSubscription(client.watchBuild {
      case MinimalBuildStructure(builds, allProjects) =>
        val acceptedProjects = allProjects.filter(_.plugins.contains("sbt.plugins.JvmPlugin"))
        if (acceptedProjects.isEmpty)
          initPromise.failure(new Error(Bundle("sbt.remote.import.noSuitableModulesFound")))
        else
          doAttach(getContext(client, logger, acceptedProjects, projectRef)).onComplete(initPromise.tryComplete)
    })

    initPromise.future
  }

  override def detach(): Unit =
    subscriptions.foreach(_.cancel())
}

/**
 * Rewrite `projectRef` contents on each transformation which is
 * executed on `projectRef` synchronization lock
 */
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
  /**
   * Extractor.Context is internal state common for all possible extractors.
   *
   * Because of asynchronous behaviour of sbt server extractors could try to
   * concurrently modify attached project. It could lead to inconsistencies.
   * Moreover, just putting `synchronized` on each Project's method won't save
   * situation because different implementations of project.structure.traits could
   * use different underlying representations.
   *
   * For example, for `StatefulProject` we can use `synchronized`.
   * But if we ever get rid of External System than we will have to deal with IDEA itself
   * and thus running project modifications in writeActions.
   *
   * To eliminate need to synchronize on each of project.structure.traits methods `withProject`
   * function was introduced. It is the one and only point of synchronization of project
   * modifications.
   */
  trait Context {
    val client: SbtClient
    val logger: Logger
    val acceptedProjects: Vector[MinimalProjectStructure]
    def withProject[T](trans: Project => T): T
  }
}
