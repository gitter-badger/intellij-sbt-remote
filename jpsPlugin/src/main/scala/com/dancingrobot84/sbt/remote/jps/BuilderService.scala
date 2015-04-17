package com.dancingrobot84.sbt.remote.jps

import java.util

import com.intellij.openapi.util.Key
import org.jdom.Element
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ExitCode, OutputConsumer}
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.scala.ChunkExclusionService
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.{JpsElementChildRole, JpsProject}
import org.jetbrains.jps.model.serialization.{JpsProjectExtensionSerializer, JpsModelSerializerExtension}
import org.jetbrains.jps.{ModuleChunk, incremental}

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/17/15.
 */
class BuilderService extends incremental.BuilderService {
  override def createModuleLevelBuilders(): util.List[_ <: ModuleLevelBuilder] = {
    Seq(new Builder).asJava
  }
}

class Builder extends ModuleLevelBuilder(BuilderCategory.SOURCE_GENERATOR) {

  override def buildStarted(context: CompileContext): Unit = {
    super.buildStarted(context)
    if (SbtRemoteProjectFlag.isSet(context.getProjectDescriptor.getProject))
      JavaBuilder.IS_ENABLED.set(context, false)
  }

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: OutputConsumer): ExitCode = {
    if (SbtRemoteProjectFlag.isSet(context.getProjectDescriptor.getProject))
      context.processMessage(new CompilerMessage("SBT Remote", BuildMessage.Kind.INFO, "YAY! I'M RUNNING!"))


    ExitCode.NOTHING_DONE
  }

  override def getPresentableName: String = Bundle("sbt.remote.jps.builderName")
}

class SerializerExtension extends JpsModelSerializerExtension {
  override def getProjectExtensionSerializers: util.List[_ <: JpsProjectExtensionSerializer] = {
    val sbtRemoteSerializer = new JpsProjectExtensionSerializer("sbt-remote.xml", "SbtRemoteSystemSettings") {
      override def loadExtension(e: JpsProject, componentTag: Element): Unit =
        e.getContainer.setChild(SbtRemoteProjectFlag.Role, new SbtRemoteProjectFlag)
      override def saveExtension(e: JpsProject, componentTag: Element): Unit = {}
    }
    Seq(sbtRemoteSerializer).asJava
  }
}

class SbtRemoteProjectFlag extends JpsElementBase[SbtRemoteProjectFlag] {
  override def applyChanges(modified: SbtRemoteProjectFlag): Unit = {}
  override def createCopy(): SbtRemoteProjectFlag = new SbtRemoteProjectFlag
}

object SbtRemoteProjectFlag {
  val Role = new JpsElementChildRole[SbtRemoteProjectFlag]

  def isSet(project: JpsProject): Boolean =
    project.getContainer.getChild(Role) != null
}

class DisableScalaCompilerService extends ChunkExclusionService {
  override def isExcluded(chunk: ModuleChunk): Boolean =
    chunk.getModules.asScala.forall { module =>
      SbtRemoteProjectFlag.isSet(module.getProject)
    }
}