package com.dancingrobot84.sbt.remote.project.extractors

import java.io.File

import com.dancingrobot84.sbt.remote.project.structure.{Artifact, Configuration, Dependency, LibraryId}
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
        deps <- parse(str)
        proj <- key.scope.project
        mod  <- ctx.project.findModule(proj.name)
      } {
        val libId = conf match {
          case Configuration.Compile => LibraryId("", s"${proj.name}-compile", "")
          case Configuration.Test => LibraryId("", s"${proj.name}-test", "")
          case _ => LibraryId("", s"${proj.name}", "")
        }
        val lib = ctx.project.addLibrary(libId)
        deps.foreach(d => lib.addArtifact(Artifact.Binary(d)))
        mod.addDependency(Dependency.Library(lib.id, conf))
        ctx.logger.warn(s"Module: ${proj.name}; Conf: $conf; Artifacts: $deps")
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
