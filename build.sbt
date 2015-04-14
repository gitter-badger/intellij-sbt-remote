import sbt._
import scalariform.formatter.preferences._

val scalacOpts = Seq(
  "-target:jvm-1.6",
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint"
)

lazy val root = project.in(file("."))
  .settings(ideaPluginSettings:_*)
  .settings(scalariformSettings:_*)
  .settings(
    name := "intellij-sbt-remote",
    organization := "com.dancingrobot84",
    version := "0.0.1",
    scalaVersion := "2.11.5",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions ++= scalacOpts,
    ideaBuild := "141.177.4",
    ideaPlugins += "Scala",
    assemblyExcludedJars in assembly <<= ideaFullJars,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala=false),
    libraryDependencies ++= Seq(
      "com.typesafe.sbtrc" % "client-2-11" % "1.0-37163c266936173d582a90113a59c729872665e0",
      "org.scalaz" %% "scalaz-core" % "7.1.1"
    ),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(IndentLocalDefs, true)
      .setPreference(IndentPackageBlocks, false)
      .setPreference(PreserveDanglingCloseParenthesis, true)
  )

lazy val ideaRunner = project.in(file("ideaRunner"))
  .dependsOn(root % Provided)
  .settings(
    scalaVersion := "2.11.5",
    autoScalaLibrary := false,
    unmanagedJars in Compile <<= ideaMainJars.in(root),
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Provided <<= ideaPluginJars.in(root)
  )

updateIdea <<= (updateIdea, ideaBaseDirectory, ideaBuild, streams) map { (_, base, build, streams) =>
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