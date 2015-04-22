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
    libraryDependencies ++= Seq(
      "com.typesafe.sbtrc" % "client-2-11" % "1.0-543625a608b99b6a4d3b3124f4f662b02411b3a3",
      "org.scala-sbt" %% "serialization" % "0.1.1"
    )
  )

lazy val ideaPlugin: Project = project.in(file("."))
  .aggregate(jpsPlugin)
  .settings(commonSettings:_*)
  .settings(
    name := "idea-plugin",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala=false),
    resolvers += Resolver.typesafeIvyRepo("releases"),
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

packagePlugin in ideaPlugin <<= (assembly in jpsPlugin, assembly in ideaPlugin, target in ideaPlugin) map { (jpsJar, ideaJar, target) =>
  val pluginName = "intellij-sbt-remote"
  val sources = Seq(
    ideaJar -> s"$pluginName/lib/${ideaJar.getName}",
    jpsJar  -> s"$pluginName/${jpsJar.getName}"
  )
  val out = target / s"$pluginName-plugin.zip"
  IO.zip(sources, out)
  out
}