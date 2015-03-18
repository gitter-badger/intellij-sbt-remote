package com.dancingrobot84.sbt.remote
package external
package controls

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemToolWindowCondition
import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory

/**
 * @author Nikolay Obedin
 * @since 3/18/15.
 */
class ToolWindowFactory extends AbstractExternalSystemToolWindowFactory(Id)

class ToolWindowCondition extends AbstractExternalSystemToolWindowCondition(Id)