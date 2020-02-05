resolvers += Resolver.jcenterRepo
resolvers += Resolver.mavenCentral

addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "6.0.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")
addSbtPlugin("com.lightbend.rp" % "sbt-deckhand" % "0.1.0")

//libraryDependencies += "org.postgresql" % "postgresql" % "42.2.9"
