package com.dancingrobot84.sbt.remote.external

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
class ProjectImportProvider(builder: ProjectImportBuilder)
    extends AbstractExternalProjectImportProvider(builder, Id) {

  override def canImport(entry: VirtualFile, project: Project): Boolean =
    ImportUtil.canImportFrom(entry)

  override def getPathToBeImported(entry: VirtualFile): String =
    ImportUtil.projectRootOf(entry).getPath
}
