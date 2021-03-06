import sbt._
import scalariform.formatter.preferences._

lazy val scalacOpts = Seq(
  "-target:jvm-1.6",
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint"
)

lazy val sbtRemoteVersion = "1.0-13ae835d2a838b026dc0b2faa7a6100195ed5169"

lazy val commonSettings: Seq[Setting[_]] =
  ideaPluginSettings ++
  scalariformSettings ++
  Seq(
    organization := "com.dancingrobot84",
    version := "0.0.1",
    scalaVersion := "2.11.6",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions ++= scalacOpts,
    ideaBuild := "141.177.4",
    ideaPlugins += "Scala",
    assemblyExcludedJars in assembly <<= ideaFullJars,
    assemblyJarName in assembly := name.value + ".jar",
    ScalariformKeys.preferences := {
      ScalariformKeys.preferences.value
        .setPreference(AlignParameters, true)
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(IndentLocalDefs, true)
        .setPreference(IndentPackageBlocks, false)
        .setPreference(PreserveDanglingCloseParenthesis, true)
    },
    resolvers += Resolver.typesafeIvyRepo("releases"),
    libraryDependencies ++= Seq(
      "com.typesafe.sbtrc" % "client-2-11" % sbtRemoteVersion,
      "org.scala-sbt" %% "serialization" % "0.1.1"
    )
  )

lazy val ideaPlugin: Project = project.in(file("."))
  .aggregate(jpsPlugin)
  .settings(commonSettings:_*)
  .settings(
    name := "idea-plugin",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala=false),
    libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.1",
    aggregate in updateIdea := false
  )

lazy val ideaRunner: Project = project.in(file("ideaRunner"))
  .dependsOn(ideaPlugin % Provided)
  .dependsOn(jpsPlugin % Provided)
  .settings(
    scalaVersion := "2.11.6",
    autoScalaLibrary := false,
    unmanagedJars in Compile <<= ideaMainJars.in(ideaPlugin),
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Provided <<= ideaPluginJars.in(ideaPlugin)
  )

lazy val jpsPlugin: Project = project.in(file("jpsPlugin"))
  .settings(commonSettings:_*)
  .settings(
    name := "jps-plugin",
    ideaBaseDirectory := baseDirectory.value.getParentFile / "idea",
    ideaPluginJars ++= {
      val jpsPluginDir = ideaBaseDirectory.value / ideaBuild.value / "plugins" / "Scala" / "lib" / "jps"
      (jpsPluginDir * (globFilter("*.jar") -- "*asm*.jar")).classpath
    }
  )

lazy val bundledServer: Project = project.in(file("bundledServer"))
  .settings(
    libraryDependencies += "com.typesafe.sbtrc" % "server-0-13" % sbtRemoteVersion
  )

updateIdea in ideaPlugin <<= (updateIdea in ideaPlugin, ideaBaseDirectory in ideaPlugin, ideaBuild in ideaPlugin, streams) map { (_, base, build, streams) =>
  val scalaPluginUrl = url("https://plugins.jetbrains.com/files/1347/19130/scala-intellij-bin-1.4.15.zip")
  val scalaPluginZipFile = base / "archives" / "scala-plugin.zip"
  val pluginsDir = base/ build / "plugins"
  if (!scalaPluginZipFile.isFile) {
    streams.log.info(s"Downloading Scala plugin from $scalaPluginUrl to $scalaPluginZipFile")
    IO.download(scalaPluginUrl, scalaPluginZipFile)
  } else {
    streams.log(s"$scalaPluginZipFile exists, download aborted")
  }
  streams.log.info(s"Unpacking $scalaPluginZipFile to $pluginsDir")
  IO.unzip(scalaPluginZipFile, pluginsDir)
}

lazy val packagePlugin = TaskKey[File]("package-plugin", "Create plugin's zip file ready to load into IDEA")

packagePlugin in ideaPlugin <<= (assembly in jpsPlugin,
                                 assembly in ideaPlugin,
                                 target in ideaPlugin,
                                 ivyPaths,
                                 update in bundledServer in Compile) map
  { (jpsJar, ideaJar, target, paths, serverArtifacts) =>
    val pluginName = "intellij-sbt-remote"
    val ivyLocal = paths.ivyHome.getOrElse(file(System.getProperty("user.home")) / ".ivy2") / "local"
    val sources = Seq(
      ideaJar -> s"$pluginName/lib/${ideaJar.getName}",
      jpsJar  -> s"$pluginName/${jpsJar.getName}"
    ) ++
      serverArtifacts.configuration(Compile.name)
        .fold(Seq.empty[ModuleReport])(_.modules)
        .flatMap { moduleReport =>
          val id = moduleReport.module
          moduleReport.artifacts.flatMap { case (artifact, jar) =>
            val baseDir = s"$pluginName/server/${id.organization}/${id.name}/${id.revision}"
            if (jar.relativeTo(ivyLocal).isDefined) {
              val ivyXml = jar.getParentFile.getParentFile / "ivys" / "ivy.xml"
              Seq(jar -> s"$baseDir/${artifact.name}.${artifact.extension}",
                  ivyXml -> s"$baseDir/ivy.xml")
            } else {
              Seq.empty
            }
          }
        }
    val out = target / s"$pluginName-plugin.zip"
    IO.zip(sources, out)
    out
  }
