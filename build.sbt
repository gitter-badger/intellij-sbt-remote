val commonSettings = Seq(
  name := "intellij-sbt-remote",
  organization := "com.dancingrobot84",
  version := "0.0.1",
  scalaVersion := "2.10.4",
  javacOptions ++= Seq("-source 1.6", "-target 1.6"),
  scalacOptions ++= Seq("-target:jvm-1.6", "-deprecation", "-feature"),
  ideaVersion := "14.0.3",
  assemblyExcludedJars in assembly <<= ideaFullJars
)

ideaPluginSettings ++ commonSettings

val versions = new {
  val sbt = "0.13.8-RC1"
  val sbtRemoteClient = "1.0-0c4f8149883708a2647e535b34c8d36806927791"
}

libraryDependencies ++= Seq(
  "com.typesafe.sbtrc" % "client-2-10" % versions.sbtRemoteClient,
  "org.scala-sbt" % "main" % versions.sbt,
  "org.scala-sbt" % "ivy" % versions.sbt
)
