package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.{ ProjectRef, Project }
import sbt.client._
import sbt.protocol.{ MinimalBuildStructure, ProjectReference, ScopedKey }
import sbt.serialization._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Extractor {
  def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): (Future[Unit], Subscription)
}

trait Context {
  def getContext(client: SbtClient, logger: Logger, acceptedProjects: Vector[ProjectReference], projectRef: ProjectRef): Extractor.Context
}

abstract class ExtractorAdapter extends Extractor with Context {
  protected def doAttach(implicit ctx: Extractor.Context): Future[Unit]

  private val subscriptions = mutable.Buffer.empty[Subscription]

  protected def addSubscription(s: Subscription) =
    subscriptions += s

  type WatchResult[T] = (ScopedKey, Try[T])

  protected def watchSettingKey[T](key: String)(valueListener: ValueListener[T])(
    implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Extractor.Context): Future[Seq[WatchResult[T]]] =
    ctx.client.lookupScopedKey(key).flatMap { allKeys =>
      Future.sequence(
        allKeys
          .filter(_.scope.project.exists(ctx.acceptedProjects.contains))
          .map { key =>
            val p = Promise[WatchResult[T]]()
            addSubscription(ctx.client.watch(SettingKey[T](key)) { (key, result) =>
              valueListener(key, result)
              p.trySuccess((key, result))
            })
            p.future
          }
      )
    }

  protected def watchTaskKey[T](key: String)(valueListener: ValueListener[T])(
    implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Extractor.Context): Future[Seq[WatchResult[T]]] =
    ctx.client.lookupScopedKey(key).flatMap { allKeys =>
      Future.sequence(
        allKeys
          .filter(_.scope.project.exists(ctx.acceptedProjects.contains))
          .map { key =>
            val p = Promise[WatchResult[T]]()
            addSubscription(ctx.client.watch(SettingKey[T](key)) { (key, result) =>
              valueListener(key, result)
              p.trySuccess((key, result))
            })
            p.future
          }
      )
    }

  protected def ifProjectAccepted(project: Option[ProjectReference])(onAccept: ProjectReference => Unit)(
    implicit ctx: Extractor.Context): Unit =
    project.foreach { p =>
      val base = withProject(_.base)
      if (p.build == base && ctx.acceptedProjects.contains(p))
        onAccept(p)
    }

  protected def withProject[T](trans: Project => T)(implicit ctx: Extractor.Context): T =
    ctx.withProject(trans)

  protected def logger(implicit ctx: Extractor.Context): Logger =
    ctx.logger

  override def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): (Future[Unit], Subscription) = {
    val initPromise = Promise[Unit]()

    addSubscription(client.watchBuild {
      case MinimalBuildStructure(builds, allProjects) =>
        val buildOpt = builds.find(_ == projectRef.project.base).headOption

        buildOpt.map { build =>
          val acceptedProjects = allProjects.filter { p =>
            p.id.build == build && p.plugins.contains("sbt.plugins.JvmPlugin")
          }.map(_.id)

          if (acceptedProjects.isEmpty)
            initPromise.failure(new Error("No suitable modules found"))
          else
            doAttach(getContext(client, logger, acceptedProjects, projectRef)).onComplete(initPromise.tryComplete)
        }.getOrElse {
          initPromise.failure(new Error("No project found"))
        }
    })

    (initPromise.future, new Subscription {
      override def cancel(): Unit =
        subscriptions.foreach(_.cancel())
    })
  }
}

trait SynchronizedContext extends Context {
  override def getContext(client0: SbtClient,
                          logger0: Logger,
                          acceptedProjects0: Vector[ProjectReference],
                          projectRef0: ProjectRef) = new Extractor.Context {
    val client = client0
    val logger = logger0
    val acceptedProjects = acceptedProjects0
    def withProject[T](trans: Project => T): T = projectRef0.synchronized {
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
    val acceptedProjects: Vector[ProjectReference]
    def withProject[T](trans: Project => T): T
  }
}
