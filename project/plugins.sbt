addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

resolvers += Resolver.url("org.jetbrains-bintray",
  url("http://dl.bintray.com/jetbrains/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "1.0.1")
