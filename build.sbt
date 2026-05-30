import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

// Root aggregator. It has no sources of its own and is never published; it exists
// so that a task run at the repo root (e.g. `sbt test`) fans out to every module.
// It depends on nothing and nothing depends on it, so there's no cycle between
// aggregation and the modules' `.dependsOn(riposte)`.
lazy val root = project
  .in(file("."))
  .aggregate(riposte, atoms, demo)
  .settings(
    name           := "riposte-root",
    publish / skip := true,
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
