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
    javacOptions ++= Seq("-source 1.6", "-target 1.6"),
    scalacOptions ++= scalacOpts,
    ideaBuild := "141.177.4",
    assemblyExcludedJars in assembly <<= ideaFullJars,
    libraryDependencies ++= Seq(
      "com.typesafe.sbtrc" % "client-2-11" % "1.0-37163c266936173d582a90113a59c729872665e0"
    ),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(IndentLocalDefs, true)
      .setPreference(IndentPackageBlocks, false)
      .setPreference(PreserveDanglingCloseParenthesis, true)
  )
