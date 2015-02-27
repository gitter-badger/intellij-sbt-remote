package com.dancingrobot84.sbt.remote.project.extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.client.{RawValueListener, TaskKey}
import sbt.protocol.{BuildValue, ScopedKey, TaskResult, TaskSuccess}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.parsing.combinator._

/**
 * @author: Nikolay Obedin
 * @since: 2/20/15.
 */
class DependenciesExtractor extends Extractor.Adapter {

  def doAttach(): Future[Unit] = {
    // TODO: this is a workaround, rewrite when serialization is implemented
    for {
      _ <- rawWatch("externalDependencyClasspath")(libraryWatcher(Configuration.Compile))
      _ <- rawWatch("test:externalDependencyClasspath")(libraryWatcher(Configuration.Test))
      _ <- rawWatch("internalDependencyClasspath")(projectWatcher(Configuration.Compile))
    } yield Unit
  }

  private def rawWatch(key: String)(listener: RawValueListener)(implicit ex: ExecutionContext): Future[Seq[Unit]] =
    ctx.client.lookupScopedKey(key).flatMap { allKeys =>
      Future.sequence(
        allKeys
          .filter(_.scope.project.exists(projects.contains))
          .map { key =>
          val p = Promise[Unit]()
          addSubscription(ctx.client.rawWatch(TaskKey(key)){ (key, result) =>
            listener(key, result)
            p.trySuccess((key, result))
          })
          p.future
        }
      )
    }

  private def libraryWatcher(conf: Configuration)(key: ScopedKey, result: TaskResult): Unit = result match {
    case TaskSuccess(BuildValue(_, str)) =>
      for {
        artifacts <- parse(str)
        project <- key.scope.project
      } {
        val libId = conf match {
          case Configuration.Compile => LibraryId("", s"${project.name}-compile", "")
          case Configuration.Test => LibraryId("", s"${project.name}-test", "")
          case _ => LibraryId("", s"${project.name}", "")
        }
        val lib = ctx.project.addLibrary(libId)
        artifacts.foreach(d => lib.addArtifact(Artifact.Binary(d)))
        ctx.project.addDependency(project.name, Dependency.Library(lib.id, conf))
        ctx.logger.warn(s"Module: ${project.name}; Conf: $conf; Artifacts: $artifacts")
      }
    case _ =>
  }

  private def projectWatcher(conf: Configuration)(key: ScopedKey, result: TaskResult): Unit = result match {
    case TaskSuccess(BuildValue(_, str)) =>
      for {
        paths0  <- parse(str)
        paths = paths0.map(p => if (p.getName == "classes") p else new File(p.getParent, "classes"))
        project <- key.scope.project
      } {
        paths.foreach(path => ctx.project.addDependency(project.name, Dependency.Module(path, conf)))
        ctx.logger.warn(s"Module: ${project.name}; Conf: $conf; Paths: $paths")
      }
    case _ =>
  }

  private def parse(str: String): Option[Seq[File]] = {
    object Parser extends RegexParsers {
      def seqOfFiles: Parser[Seq[File]] = "List(" ~> repsep(file, ", ") <~ ")"
      def file: Parser[File] = "Attributed(" ~> ("([^)])+".r ^^ {f => new File(f)}) <~ ")"
    }
    Parser.parseAll(Parser.seqOfFiles, str).map(Some(_)).getOrElse(None)
  }
}
