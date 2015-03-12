package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.{ProjectRef, Project}
import sbt.client._
import sbt.protocol.{MinimalBuildStructure, ProjectReference, ScopedKey}
import sbt.serialization._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Extractor {
  def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): (Future[Unit], Subscription)
}

object Extractor {

  trait Context {
    val client: SbtClient
    val logger: Logger
    val acceptedProjects: Vector[ProjectReference]
    def withProject[T](trans: Project => T): T
  }

  private final class SynchronizedContext
      (val client: SbtClient,
       val logger: Logger,
       val acceptedProjects: Vector[ProjectReference],
       val projectRef: ProjectRef)
    extends Context {

    def withProject[T](trans: Project => T): T = projectRef.synchronized {
      val result = trans(projectRef.project)
      projectRef.project = projectRef.project.copy
      result
    }
  }

  type WatchResult[T] = (ScopedKey, Try[T])

  abstract class Adapter extends Extractor {
    protected val subscriptions = mutable.Buffer.empty[Subscription]

    protected def doAttach(implicit ctx: Context): Future[Unit]

    protected def addSubscription(s: Subscription) =
      subscriptions += s

    protected def watchSettingKey[T]
        (key: String)(valueListener: ValueListener[T])
        (implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Context) :
        Future[Seq[WatchResult[T]]] =
      ctx.client.lookupScopedKey(key).flatMap { allKeys =>
        Future.sequence(
          allKeys
          .filter(_.scope.project.exists(ctx.acceptedProjects.contains))
          .map { key =>
            val p = Promise[WatchResult[T]]()
            addSubscription(ctx.client.watch(SettingKey[T](key)){ (key, result) =>
              valueListener(key, result)
              p.trySuccess((key, result))
            })
            p.future
          }
        )
      }

    protected def watchTaskKey[T]
        (key: String)(valueListener: ValueListener[T])
        (implicit unpickler: Unpickler[T], ex: ExecutionContext, ctx: Context) :
        Future[Seq[WatchResult[T]]] =
      ctx.client.lookupScopedKey(key).flatMap { allKeys =>
        Future.sequence(
          allKeys
          .filter(_.scope.project.exists(ctx.acceptedProjects.contains))
          .map { key =>
            val p = Promise[WatchResult[T]]()
            addSubscription(ctx.client.watch(SettingKey[T](key)){ (key, result) =>
              valueListener(key, result)
              p.trySuccess((key, result))
            })
            p.future
          }
        )
      }

    protected def ifProjectAccepted
        (project: Option[ProjectReference])(onAccept: ProjectReference => Unit)
        (implicit ctx: Context): Unit =
      project.foreach { p =>
        val base = withProject(_.base)
        if (p.build == base && ctx.acceptedProjects.contains(p))
          onAccept(p)
      }

    protected def withProject[T](trans: Project => T)(implicit ctx: Context): T =
      ctx.withProject(trans)

    protected def logger(implicit ctx: Context): Logger =
      ctx.logger

    def attach(client: SbtClient, projectRef: ProjectRef, logger: Logger): (Future[Unit], Subscription) = {
      val initPromise = Promise[Unit]()

      addSubscription(client.watchBuild { case MinimalBuildStructure(builds, allProjects) =>
        val buildOpt = builds.find(_ == projectRef.project.base).headOption

        buildOpt.map { build =>
          val acceptedProjects = allProjects.filter { p =>
            p.id.build == build && p.plugins.contains("sbt.plugins.JvmPlugin")
          }.map(_.id)

          if(acceptedProjects.isEmpty)
            initPromise.failure(new Error("No suitable modules found"))
          else
            doAttach(new SynchronizedContext(client, logger, acceptedProjects, projectRef)).onComplete(initPromise.tryComplete)
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

}
