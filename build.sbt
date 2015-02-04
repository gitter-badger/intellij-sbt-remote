val commonSettings = Seq(
  name := "intellij-sbt-remote",
  organization := "com.dancingrobot84",
  version := "0.0.1",
  scalaVersion := "2.11.5",
  javacOptions ++= Seq("-source 1.6", "-target 1.6"),
  scalacOptions ++= Seq("-target:jvm-1.6", "-deprecation", "-feature"),
  ideaVersion := "14.0.3"
)

ideaPluginSettings ++ commonSettings
