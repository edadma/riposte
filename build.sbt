import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import xerial.sbt.Sonatype.sonatypeCentralHost

ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

// --- Maven Central publishing ----------------------------------------------
// Metadata for the generated POM and the Sonatype Central wiring, mirroring the
// edadma cross-project template. Credentials live outside the repo (in
// ~/.sbt/.../sonatype.sbt), so nothing secret is checked in. Only the four real
// library modules publish; the demos and the root aggregator skip it.
ThisBuild / organizationName     := "edadma"
ThisBuild / organizationHomepage := Some(url("https://github.com/edadma"))
ThisBuild / licenses             := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))
ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / homepage             := Some(url("https://github.com/edadma/riposte"))
ThisBuild / description :=
  "A React-inspired frontend library for Scala.js: function components, hooks, and a " +
    "typed DSL over an immutable VNode tree diffed against the live DOM by a reconciler."
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/edadma/riposte"),
    "scm:git@github.com:edadma/riposte.git",
  ),
)
ThisBuild / developers := List(
  Developer(
    id = "edadma",
    name = "Edward A. Maxedon, Sr.",
    email = "edadma@gmail.com",
    url = url("https://github.com/edadma"),
  ),
)

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / sonatypeProfileName    := "io.github.edadma"
ThisBuild / publishTo              := sonatypePublishToBundle.value
ThisBuild / publishMavenStyle      := true
ThisBuild / Test / publishArtifact := false
ThisBuild / publishConfiguration :=
  publishConfiguration.value.withOverwrite(true).withChecksums(Vector.empty)

// `publishMavenStyle` is read by the publish task rather than another setting, so
// the unused-key linter flags the ThisBuild form; it is genuinely in effect.
Global / excludeLintKeys += publishMavenStyle

// Root aggregator. It has no sources of its own and is never published; it exists
// so that a task run at the repo root (e.g. `sbt test`) fans out to every module.
// It depends on nothing and nothing depends on it, so there's no cycle between
// aggregation and the modules' `.dependsOn(riposte)`.
lazy val root = project
  .in(file("."))
  .aggregate(riposte, atoms, router, salle, salleDemo, demo)
  .settings(
    name                := "riposte-root",
    publish / skip      := true,
    publishLocal / skip := true,
  )

// riposte — a React-style virtual-DOM UI library for Scala.js. The published
// library, in core/ so the repo root can stay a thin aggregator.
//
// An immutable VNode tree describes the UI; a reconciler diffs each new tree
// against the live DOM and mutates the DOM to match. Function components carry
// local state through positional hooks.
lazy val riposte = project
  .in(file("core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "riposte",
    scalacOptions ++= commonScalacOptions,

    // Tests need a real DOM. jsdom provides one under Node (installed via the
    // project's package.json at the repo root); the jsEnv is Test-scoped.
    Test / jsEnv := new JSDOMNodeJSEnv(),

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.1",
      // Spec-compliant macrotask scheduling, used to run passive effects after
      // the browser paints (render batching itself stays on the microtask queue).
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1",
      "org.scalatest" %%% "scalatest"  % "3.2.19" % Test,
    ),
  )

// The runnable demo. Depends on the library, supplies a `main` that mounts into
// the page, and emits a single classic <script>-loadable file so demo/index.html
// can load it with a plain <script src> tag. Never published.
//
//   sbt demo/fastLinkJS   # build demo/target/scala-3.8.3/riposte-demo-fastopt/main.js
lazy val demo = project
  .in(file("demo"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte)
  .settings(
    name := "riposte-demo",
    scalacOptions ++= commonScalacOptions,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("DemoApp"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
    publish / skip := true,
  )

// riposte-atoms — Jotai-inspired atomic state, a separate artifact built on the
// core's `useSyncExternalStore` seam. Atoms are identity-based units of shared
// state; derived atoms recompute from the atoms they read. scalajs-dom and the
// macrotask executor come transitively through `riposte`.
lazy val atoms = project
  .in(file("atoms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte)
  .settings(
    name := "riposte-atoms",
    scalacOptions ++= commonScalacOptions,
    Test / jsEnv := new JSDOMNodeJSEnv(),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
  )

// riposte-router — client-side routing for SPAs, a separate artifact built on the
// core's public API only (useSyncExternalStore for the location, context for route
// params, the DSL for Link). History and hash modes; route matching with params and
// specificity ranking. No core changes needed — the router touches no internals.
lazy val router = project
  .in(file("router"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte)
  .settings(
    name := "riposte-router",
    scalacOptions ++= commonScalacOptions,
    Test / jsEnv := new JSDOMNodeJSEnv(),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
  )

// salle — a component library for riposte (the fencing salle: the hall where the
// components live). A separate artifact built on the core's public DSL and hooks;
// no core changes required. Named components compose the core element builders into
// reusable, styled widgets.
lazy val salle = project
  .in(file("salle"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte)
  .settings(
    // Published as `riposte-salle`; the project id stays `salle` for `sbt salle/test`.
    name := "riposte-salle",
    scalacOptions ++= commonScalacOptions,
    Test / jsEnv := new JSDOMNodeJSEnv(),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
  )

// salle-demo — a runnable showcase for salle. Mirrors the riposte `demo` module
// (NoModule, main-module initializer) but depends on `salle`. Build with
// `sbt salleDemo/fastLinkJS`, then open salle-demo/index.html.
lazy val salleDemo = project
  .in(file("salle-demo"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(salle)
  .settings(
    name := "salle-demo",
    scalacOptions ++= commonScalacOptions,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass             := Some("SalleDemo"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
    publish / skip := true,
  )

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Werror",
  // The builder DSL turns Strings/VNodes/Seqs into element children via
  // `Conversion` givens; enable the feature build-wide so user code needn't
  // import it per file.
  "-language:implicitConversions",
)
