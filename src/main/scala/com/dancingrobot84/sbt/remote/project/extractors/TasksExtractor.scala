package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure.Task
import sbt.protocol.{ Completion, ExecutionAnalysisKey }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Nikolay Obedin
 * @since 3/27/15.
 */
abstract class TasksExtractor extends ExtractorAdapter {
  override protected def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    Future.sequence(ctx.acceptedProjects.map(pr => addTasks(pr.id.name))).map(_ => Unit)
  }

  private def getCompletions(projectName: String)(implicit ctx: Extractor.Context): Future[Vector[String]] =
    for {
      completions <- ctx.client.possibleAutocompletions(s"$projectName/", 0)
    } yield {
      completions.collect { case Completion(_, name, false) => name }.distinct
    }

  private def filterKeys(completions: Vector[String])(implicit ctx: Extractor.Context): Future[Vector[Task]] =
    Future.sequence(completions.map(ctx.client.lookupScopedKey)).map(_.flatten.map(k => Task(k.key.name)))

  private def addTasks(projectName: String)(implicit ctx: Extractor.Context): Future[Unit] =
    getCompletions(projectName).flatMap(filterKeys).map { tasks =>
      tasks.map(task => withProject { project =>
        project.modules.find(_.id == projectName).foreach(_.addTask(task))
      })
      Unit
    }
}
