package com.dancingrobot84.sbt.remote
package console

import javax.swing.Icon

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang._
import com.intellij.openapi.fileTypes._
import com.intellij.psi.tree.{ IElementType, IFileElementType }
import com.intellij.psi.{ FileViewProvider, PsiFile }

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

class PsiSbtRemoteFile(provider: FileViewProvider) extends PsiFileBase(provider, SbtRemoteLanguage) {
  override def getFileType: FileType = SbtRemoteLanguageFileType
}

class PsiSbtRemoteParserDefinition extends PlainTextParserDefinition {
  override def createFile(viewProvider: FileViewProvider): PsiFile =
    new PsiSbtRemoteFile(viewProvider)

  override def getFileNodeType: IFileElementType = new IFileElementType(SbtRemoteLanguage) {
    val PlainText = new IElementType("SbtRemotePlainText", SbtRemoteLanguage)
    override def parseContents(chameleon: ASTNode): ASTNode =
      ASTFactory.leaf(PlainText, chameleon.getChars)
  }
}