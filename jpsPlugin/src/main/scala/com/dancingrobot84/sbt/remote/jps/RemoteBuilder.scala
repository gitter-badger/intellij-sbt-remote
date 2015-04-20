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
    SbtRemoteProjectSettings.get(context.getProjectDescriptor.getProject).foreach { settings =>
      val donePromise = Promise[ExitCode]()
      // TODO: reuse SBT connection from IDEA process
      context.processMessage(new ProgressMessage(Bundle("sbt.remote.jps.connectingToServer")))
      SbtConnector("idea", "Intellij IDEA", new File(settings.projectPath))
        .open(onConnect(context, donePromise), onFailure(context, donePromise))
      return Await.result(donePromise.future, Duration.Inf)
    }

    ExitCode.NOTHING_DONE
  }

  override def getPresentableName: String = Bundle("sbt.remote.jps.builderName")

  def onConnect(context: CompileContext, donePromise: Promise[ExitCode])(client: SbtClient): Unit = {
    var jobId: Long = -1

    client.handleEvents {
      case CompilationFailure(id, failure) => failure match {
        case CompilationFailure(_, Position(srcPath, _, lineOpt, lineContent, _, pointerOpt, space), severity, message) =>
          val messageWithLineAndPointer =
            message + "\n" + lineContent + space.map("\n" + _ + "^").getOrElse("")
          val kind = severity match {
            case xsbti.Severity.Warn  => BuildMessage.Kind.WARNING
            case xsbti.Severity.Error => BuildMessage.Kind.ERROR
            case _                    => BuildMessage.Kind.INFO
          }
          val line = lineOpt.fold(-1L)(_.toLong)
          val pointer = pointerOpt.fold(-1L)(_.toLong + 1L)
          context.processMessage(new CompilerMessage(Bundle("sbt.remote.jps.compilerName"), kind,
            messageWithLineAndPointer, srcPath.orNull, -1, -1, -1, line, pointer))
        case _ => // do nothing
      }
      case ExecutionSuccess(id) if id == jobId =>
        donePromise.success(ExitCode.OK)
      case ExecutionFailure(id) if id == jobId =>
        donePromise.success(ExitCode.ABORT)
      case x =>
        context.processMessage(new ProgressMessage(x.toString))
    }

    client.requestExecution("compile", None).foreach(jobId = _)
  }

  def onFailure(context: CompileContext, donePromise: Promise[ExitCode])(reconnecting: Boolean, cause: String): Unit = {
    context.processMessage(new BuildMessage(cause, BuildMessage.Kind.WARNING) {})
    if (!reconnecting)
      donePromise.success(ExitCode.ABORT)
  }
}

object RemoteBuilder {
  class DisableScalaCompilerService extends ChunkExclusionService {
    override def isExcluded(chunk: ModuleChunk): Boolean =
      chunk.getModules.asScala.forall { module =>
        SbtRemoteProjectSettings.get(module.getProject).isDefined
      }
  }
}
