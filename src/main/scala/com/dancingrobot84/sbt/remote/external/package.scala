package com.dancingrobot84.sbt.remote

import _root_.java.lang.{Boolean => JavaBoolean}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.{Pair => IdeaPair}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.{Function => IdeaFunction}
import scala.language.implicitConversions

/**
 * @author Nikolay Obedin
 * @since 2/5/15.
 */
package object external {
  val Id = new ProjectSystemId(Bundle("sbt.remote.name"), Bundle("sbt.remote.name"))

  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] =
    new IdeaFunction[A, B] {
      def fun(a: A) = f(a)
    }

  implicit def toIdeaPredicate[A](f: A => Boolean): IdeaFunction[A, JavaBoolean] =
    new IdeaFunction[A, JavaBoolean] {
      def fun(a: A) = JavaBoolean.valueOf(f(a))
    }

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] =
    new IdeaFunction[IdeaPair[A, B], C] {
      def fun(pair: IdeaPair[A, B]) = f(pair.getFirst, pair.getSecond)
    }

  object ImportUtil {
    def canImportFrom(from: VirtualFile): Boolean = {
      if (from.isDirectory)
        from.getName == "project" ||
          from.containsDirectory("project") ||
          from.containsFile("build.sbt")
      else
        from.getName == "build.sbt"
    }

    def projectRootOf(entry: VirtualFile): VirtualFile =
      if (entry.isDirectory) {
        if (entry.getName == "project") entry.getParent else entry
      } else {
        entry.getParent
      }
  }

  implicit class RichVirtualFile(val entry: VirtualFile) extends AnyVal {
    def containsDirectory(name: String): Boolean =
      find(name).exists(_.isDirectory)

    def containsFile(name: String): Boolean =
      find(name).exists(!_.isDirectory)

    def find(name: String): Option[VirtualFile] =
      Option(entry.findChild(name))
  }
}
