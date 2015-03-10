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

//resolvers += Resolver.typesafeIvyRepo("releases")

libraryDependencies ++= Seq(
  "com.typesafe.sbtrc" % "client-2-10" % "1.0-6899f43d9872193adcf40b6c0d2838d4a968d5f3",
  "org.scala-sbt" % "main" % "0.13.8-RC1",
  "org.scala-sbt" % "ivy" % "0.13.8-RC1"
)
