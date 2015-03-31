package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.externalSystem.service.execution.{ AbstractExternalSystemRuntimeConfigurationProducer, AbstractExternalSystemTaskConfigurationType }
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil

/**
 * @author Nikolay Obedin
 * @since 3/27/15.
 */
class TaskConfigurationType extends AbstractExternalSystemTaskConfigurationType(Id)

object TaskConfigurationType {
  def apply(): AbstractExternalSystemTaskConfigurationType =
    ExternalSystemUtil.findConfigurationType(Id)
}

class TaskConfigurationProducer extends AbstractExternalSystemRuntimeConfigurationProducer(TaskConfigurationType())