package com.dancingrobot84.sbt.remote.misc

import com.dancingrobot84.sbt.remote.external
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry

/**
 * @author: Nikolay Obedin
 * @since: 2/11/15.
 */
class StartupRoutines extends ApplicationComponent.Adapter {
  override def initComponent(): Unit =
    Registry.get(external.Id.getId + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX).setValue(true)
}
