package com.dancingrobot84.sbt.remote

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.Messages

/**
 * @author Nikolay Obedin
 * @since 2/4/15.
 */
class TestAction extends AnAction("Test Action") {
  def actionPerformed(evt: AnActionEvent): Unit = {
    Messages.showMessageDialog("Hello world!", "Test Action", null)
  }
}
