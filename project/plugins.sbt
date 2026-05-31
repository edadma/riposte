addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")

// Maven Central publishing: artifact signing + the Sonatype Central tasks.
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.3.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")

// Provides the jsdom-backed jsEnv used for DOM unit tests.
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
