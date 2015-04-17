package com.dancingrobot84.sbt.remote.jps

import java.util

import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ExitCode, OutputConsumer}
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.jps.{ModuleChunk, incremental}

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/17/15.
 */
class BuilderService extends incremental.BuilderService {
  override def createModuleLevelBuilders(): util.List[_ <: ModuleLevelBuilder] = {
    val builder = new ModuleLevelBuilder(BuilderCategory.SOURCE_GENERATOR) {
      override def build(context: CompileContext,
                         chunk: ModuleChunk,
                         dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                         outputConsumer: OutputConsumer): ExitCode = {
        context.processMessage(new CompilerMessage("SBT Remote", BuildMessage.Kind.INFO, "YAY! I'M RUNNING!"))
        ExitCode.NOTHING_DONE
      }

      override def getPresentableName: String = Bundle("sbt.remote.jps.builderName")
    }

    Seq(builder).asJava
  }
}
