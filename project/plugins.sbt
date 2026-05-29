addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")

// Provides the jsdom-backed jsEnv used for DOM unit tests.
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
