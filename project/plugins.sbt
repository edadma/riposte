addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")

// Cross-building the host-agnostic `vdom` core across JS (for riposte) and JVM
// (for the headless reconciler/hooks tests). The JVM target is the forcing
// function: any org.scalajs.dom in vdom fails the JVM compile.
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")

// Maven Central publishing: artifact signing + the Sonatype Central tasks.
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.3.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")

// Provides the jsdom-backed jsEnv used for DOM unit tests.
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
