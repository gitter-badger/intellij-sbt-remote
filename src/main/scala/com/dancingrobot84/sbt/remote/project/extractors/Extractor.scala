package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.Project
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
  def attach(client: SbtClient, project: Project, logger: Logger): (Future[Unit], Subscription)
}

object Extractor {

  final case class Context(client: SbtClient, project: Project, logger: Logger, acceptedProjects: Vector[ProjectReference])

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
        if (p.build == ctx.project.base && ctx.acceptedProjects.contains(p))
          onAccept(p)
      }

    def attach(client: SbtClient, project: Project, logger: Logger): (Future[Unit], Subscription) = {
      val initPromise = Promise[Unit]()

      addSubscription(client.watchBuild { case MinimalBuildStructure(builds, allProjects) =>
        val buildOpt = builds.find(_ == project.base).headOption

        buildOpt.map { build =>
          val acceptedProjects = allProjects.filter { p =>
            p.id.build == build && p.plugins.contains("sbt.plugins.JvmPlugin")
          }.map(_.id)

          if(acceptedProjects.isEmpty)
            initPromise.failure(new Error("No suitable modules found"))
          else
            doAttach(Context(client, project, logger, acceptedProjects)).onComplete(initPromise.tryComplete)
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
