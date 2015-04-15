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
      completions.collect { case Completion(name, _, false) => name }
    }

  private def filterKeys(projectName: String)(completions: Vector[String])(implicit ctx: Extractor.Context): Future[Vector[Task]] = {
    val analysis = Future.sequence(completions.map(c => ctx.client.analyzeExecution(s"$projectName/$c")))
    analysis.map { a =>
      a.collect { case ExecutionAnalysisKey(keys) => keys.map(_.key.name) }.flatMap(_.map(Task))
    }
  }

  private def addTasks(projectName: String)(implicit ctx: Extractor.Context): Future[Unit] =
    getCompletions(projectName).flatMap(filterKeys(projectName)).map { tasks =>
      tasks.map(task => withProject { project =>
        project.modules.find(_.id == projectName).foreach(_.addTask(task))
      })
      Unit
    }
}
