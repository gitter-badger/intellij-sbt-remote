import scalariform.formatter.preferences._

val commonSettings = Seq(
  name := "intellij-sbt-remote",
  organization := "com.dancingrobot84",
  version := "0.0.1",
  scalaVersion := "2.11.5",
  javacOptions ++= Seq("-source 1.6", "-target 1.6"),
  scalacOptions ++= Seq("-target:jvm-1.6", "-deprecation", "-feature"),
  ideaBuild := "139.1117.1",
  assemblyExcludedJars in assembly <<= ideaFullJars
)

ideaPluginSettings ++ commonSettings ++ scalariformSettings

libraryDependencies ++= Seq(
  "com.typesafe.sbtrc" % "client-2-11" % "1.0-37163c266936173d582a90113a59c729872665e0"
)

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(IndentLocalDefs, true)
  .setPreference(IndentPackageBlocks, false)
  .setPreference(PreserveDanglingCloseParenthesis, true)
