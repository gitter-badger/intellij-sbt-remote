package com.dancingrobot84.sbt.remote
package console

import com.dancingrobot84.sbt.remote.applicationComponents.SbtServerConnectionManager
import com.dancingrobot84.sbt.remote.external.SystemSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.{ completion => idea }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }

/**
 * @author Nikolay Obedin
 * @since 4/22/15.
 */
class CompletionContributor extends idea.CompletionContributor {
  override def fillCompletionVariants(parameters: idea.CompletionParameters, result: idea.CompletionResultSet): Unit = {
    val project = parameters.getEditor.getProject

    for {
      _ <- Option(SystemSettings(project))
      connector <- SbtServerConnectionManager().getSbtConnectorFor(project.getBasePath)
    } {
      val completionsPromise = Promise[Vector[String]]()
      val toComplete = parameters.getOriginalPosition.getText

      val subscription = connector.open { client =>
        completionsPromise.completeWith(client.possibleAutocompletions(toComplete, 0).map(_.map(_.append)))
      }
      completionsPromise.future.onComplete(_ => subscription.cancel())

      Await.result(completionsPromise.future, Duration.Inf).foreach { completion =>
        result.addElement(new LookupElement {
          override def getLookupString: String = toComplete + completion
        })
      }
    }
  }
}
