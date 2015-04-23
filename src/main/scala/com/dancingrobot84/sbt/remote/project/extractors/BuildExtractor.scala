package com.dancingrobot84.sbt.remote
package project
package extractors

import com.dancingrobot84.sbt.remote.project.structure._
import sbt.protocol._

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author: Nikolay Obedin
 * @since: 23/04/2015.
 */
abstract class BuildExtractor extends ExtractorAdapter {
  override protected def doAttach(implicit ctx: Extractor.Context): Future[Unit] = {
    logger.info(Bundle("sbt.remote.import.extractBuildInfo"))

    val donePromise = Promise[Unit]
    addSubscription(ctx.client.watchBuild {
      case MinimalBuildStructure(builds, buildData, _) => withProject { project =>
        for {
          BuildData(build, classpath, _) <- buildData
          module <- project.modules.find(_.id.build == build)
          libId = Library.Id.forBuildJars(build)
          library = project.addLibrary(libId)
        } {
          classpath.filter(_.isFile).map(Artifact.Binary).foreach(library.addArtifact)
          module.addDependency(Dependency.Library(libId, Configuration.Provided))
        }
        donePromise.trySuccess(Unit)
      }
    })

    donePromise.future
  }
}
