package com.dancingrobot84.sbt.remote.jps

import java.io.File

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ ExitCode, OutputConsumer }
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.messages.{ BuildMessage, CompilerMessage, ProgressMessage }
import org.jetbrains.jps.incremental.scala.ChunkExclusionService
import org.jetbrains.jps.incremental.{ BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder }
import sbt.client._
import sbt.protocol._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }

/**
 * @author Nikolay Obedin
 * @since 4/20/15.
 */
class RemoteBuilder extends ModuleLevelBuilder(BuilderCategory.SOURCE_GENERATOR) {

  override def buildStarted(context: CompileContext): Unit = {
    super.buildStarted(context)
    if (SbtRemoteProjectSettings.get(context.getProjectDescriptor.getProject).isDefined)
      JavaBuilder.IS_ENABLED.set(context, false)
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: OutputConsumer): ExitCode = {
    implicit val implicitContext = context
    SbtRemoteProjectSettings.get(context.getProjectDescriptor.getProject).foreach { settings =>
      val donePromise = Promise[ExitCode]()

      val modulesToCompile = chunk.getModules.asScala.flatMap { module =>
        val qualifiedName = settings.moduleNameToQualifiedNameMap.get(module.getName)
        if (qualifiedName.isEmpty)
          compilerMessage(BuildMessage.Kind.WARNING, Bundle("sbt.remote.jps.moduleNotFound", module.getName), None, -1, -1)
        qualifiedName
      }.toSet

      // TODO: reuse SBT connection from IDEA process
      progressMessage(Bundle("sbt.remote.jps.connectingToServer"))
      SbtConnector("idea", "Intellij IDEA", new File(settings.projectPath))
        .open(onConnect(modulesToCompile, donePromise), onFailure(donePromise))

      return Await.result(donePromise.future, Duration.Inf)
    }

    ExitCode.NOTHING_DONE
  }

  override def getPresentableName: String = Bundle("sbt.remote.jps.builderName")

  private def onConnect(modules: Set[String], donePromise: Promise[ExitCode])(client: SbtClient)(
    implicit context: CompileContext): Unit = {
    checkCanceled(client, donePromise, -1)
    var jobId: Long = -1

    client.handleEvents { event =>
      checkCanceled(client, donePromise, jobId)
      event match {
        case ExecutionSuccess(id) if id == jobId =>
          donePromise.trySuccess(ExitCode.OK)
        case ExecutionFailure(id) if id == jobId =>
          donePromise.trySuccess(ExitCode.ABORT)
        case _ => handleLogEvents(jobId, event)
      }
    }

    val command = modules.map(_ + "/compile").mkString("; ", " ; ", "")
    client.requestExecution(command, None).foreach(jobId = _)
  }

  private def handleLogEvents(jobId: Long, event: Event)(implicit context: CompileContext): Unit = event match {
    case CompilationFailure(_, failure) => failure match {
      case CompilationFailure(_, Position(srcPath, _, lineOpt, lineContent, _, pointerOpt, space), severity, message) =>
        val messageWithLineAndPointer =
          message + "\n" + lineContent + space.map("\n" + _ + "^").getOrElse("")
        val kind = severity match {
          case xsbti.Severity.Warn  => BuildMessage.Kind.WARNING
          case xsbti.Severity.Error => BuildMessage.Kind.ERROR
          case _                    => BuildMessage.Kind.INFO
        }
        val line = lineOpt.fold(-1L)(_.toLong)
        val column = pointerOpt.fold(-1L)(_.toLong + 1L)
        compilerMessage(kind, messageWithLineAndPointer, srcPath, line, column)
      case _ => // ignore
    }
    case TaskStarted(id, _, Some(key)) if id == jobId =>
      progressMessage(Bundle("sbt.remote.jps.taskIsRunning", key.key.name))
    case TaskFinished(id, _, Some(key), success, _) if id == jobId =>
      val message = {
        val taskName = key.key.name
        if (success)
          Bundle("sbt.remote.jps.taskSucceeded", taskName)
        else
          Bundle("sbt.remote.jps.taskFailed", taskName)
      }
      progressMessage(message)
    case logEvent: LogEvent if !logEvent.entry.message.startsWith("Read from stdout:") =>
      logEvent.entry match {
        case LogMessage(level, message) if level != LogMessage.DEBUG =>
          progressMessage(s"[$level] $message")
        case _ => // ignore
      }
    case _ => // ignore
  }

  private def onFailure(donePromise: Promise[ExitCode])(reconnecting: Boolean, cause: String)(
    implicit context: CompileContext): Unit = {
    context.processMessage(new BuildMessage(cause, BuildMessage.Kind.WARNING) {})
    if (!reconnecting)
      donePromise.trySuccess(ExitCode.ABORT)
  }

  private def checkCanceled(client: SbtClient, donePromise: Promise[ExitCode], jobId: Long)(
    implicit context: CompileContext): Unit =
    if (context.getCancelStatus.isCanceled) {
      if (jobId != -1) {
        val isCancelled = !Await.result(client.cancelExecution(jobId), Duration.Inf)
        if (!isCancelled)
          return
      }
      donePromise.trySuccess(ExitCode.ABORT)
    }

  private def progressMessage(message: String)(implicit context: CompileContext): Unit =
    context.processMessage(new ProgressMessage(message))

  private def compilerMessage(kind: BuildMessage.Kind,
                              message: String,
                              sourcePath: Option[String],
                              line: Long,
                              column: Long)(implicit context: CompileContext): Unit =
    context.processMessage(new CompilerMessage(
      Bundle("sbt.remote.jps.compilerName"), kind, message, sourcePath.orNull, -1, -1, -1, line, column))
}

object RemoteBuilder {
  class DisableScalaCompilerService extends ChunkExclusionService {
    override def isExcluded(chunk: ModuleChunk): Boolean =
      chunk.getModules.asScala.forall { module =>
        SbtRemoteProjectSettings.get(module.getProject).isDefined
      }
  }
}
