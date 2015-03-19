resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.12.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")
