resolvers += Resolver.jcenterRepo
resolvers += Resolver.mavenCentral

addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "6.0.0")
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.3")
