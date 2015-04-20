package com.dancingrobot84.sbt.remote.jps

import java.util

import org.jetbrains.jps.incremental
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.{ JpsElementChildRole, JpsProject }

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 4/17/15.
 */
class BuilderService extends incremental.BuilderService {
  override def createModuleLevelBuilders(): util.List[_ <: ModuleLevelBuilder] =
    Seq(new RemoteBuilder).asJava
}

