package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.Project
import sbt.client._
import sbt.protocol.{MinimalBuildStructure, ProjectReference, ScopedKey}
import sbt.serialization.Unpickler

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Extractor {
  def attach(context: Extractor.Context): (Future[Unit], Subscription)
}

object Extractor {
  final case class Context(client: SbtClient, project: Project, logger: Logger)

  type WatchResult[T] = (ScopedKey, Try[T])

  abstract class Adapter extends Extractor {
    @volatile
    protected var projects: Vector[ProjectReference] = Vector.empty
    protected val subscriptions = mutable.Buffer.empty[Subscription]
    protected var ctx: Context = null


    protected def doAttach(): Future[Unit]

    protected def addSubscription(s: Subscription) =
      subscriptions += s

    protected def watchSettingKey[T](key: String)(valueListener: ValueListener[T])
        (implicit unpickler: Unpickler[T], ex: ExecutionContext): Future[Seq[WatchResult[T]]] = {
      Await.result(ctx.client.lookupScopedKey(key).map { allKeys =>
        Future.sequence(allKeys
          .filter(_.scope.project.exists(projects.contains))
          .map { key =>
            val p = Promise[WatchResult[T]]()
            addSubscription(ctx.client.watch(SettingKey[T](key)){ (key, result) =>
              if (p.isCompleted) valueListener(key, result) else p.success((key, result))
            })
            p.future
          }
        )
      }, Duration.Inf)
    }

    protected def watchTaskKey[T](key: String)(valueListener: ValueListener[T])
      (implicit unpickler: Unpickler[T], ex: ExecutionContext): Future[Seq[WatchResult[T]]] = {
      Await.result(ctx.client.lookupScopedKey(key).map { allKeys =>
        Future.sequence(allKeys
          .filter(_.scope.project.exists(projects.contains))
          .map { key =>
          val p = Promise[WatchResult[T]]()
          addSubscription(ctx.client.watch(SettingKey[T](key)){ (key, result) =>
            if (p.isCompleted) valueListener(key, result) else p.success((key, result))
          })
          p.future
        }
        )
      }, Duration.Inf)
    }

    def attach(context: Context): (Future[Unit], Subscription) = {
      ctx = context
      val initPromise = Promise[Unit]()

      addSubscription(ctx.client.watchBuild { case MinimalBuildStructure(builds, allProjects) =>
        val buildOpt = builds.find(_ == ctx.project.base).headOption

        buildOpt.map { build =>
          projects = allProjects.filter { p =>
            p.id.build == build && p.plugins.contains("sbt.plugins.JvmPlugin")
          }.map(_.id)

          if(projects.isEmpty)
            ctx.logger.error("No suitable modules found")
          else
            doAttach().onComplete(initPromise.complete)

        }.getOrElse {
          ctx.logger.error("No project found")
        }
      })

      (initPromise.future, new Subscription {
        override def cancel(): Unit =
          subscriptions.foreach(_.cancel())
      })
    }
  }

}
