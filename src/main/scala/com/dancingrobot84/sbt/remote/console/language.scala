package com.dancingrobot84.sbt.remote
package console

import javax.swing.Icon

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory, LanguageFileType}

/**
 * @author Nikolay Obedin
 * @since 4/22/15.
 */
object SbtRemoteLanguage extends Language("SbtRemote")

object SbtRemoteLanguageFileType extends LanguageFileType(SbtRemoteLanguage) {
  override def getDefaultExtension: String = "sbt-remote"

  override def getName: String = Bundle("sbt.remote.name")

  override def getIcon: Icon = Bundle("sbt.remote.icon")

  override def getDescription: String = "Auxilary filetype to provide competions for SBT Remote Console"
}

class SbtRemoteFileTypeFactory extends FileTypeFactory {
  override def createFileTypes(consumer: FileTypeConsumer): Unit =
    consumer.consume(SbtRemoteLanguageFileType, SbtRemoteLanguageFileType.getDefaultExtension)
}