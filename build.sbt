import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

// vdom — a React-style virtual-DOM UI library for Scala.js.
//
// An immutable VNode tree describes the UI; a reconciler diffs each new tree
// against the live DOM and mutates the DOM to match. Function components carry
// local state through positional hooks.
//
// This is the library only — no demo code, no main initializer — so the
// published artifact stays clean. The runnable demo lives in its own `demo`
// subproject below.
lazy val vdom = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "vdom",
    scalacOptions ++= commonScalacOptions,

    // Tests need a real DOM. jsdom provides one under Node (installed via the
    // project's package.json); the jsEnv is Test-scoped.
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
//   sbt demo/fastLinkJS   # build demo/target/scala-3.8.3/demo-fastopt/main.js
lazy val demo = project
  .in(file("demo"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(vdom)
  .settings(
    name := "vdom-demo",
    scalacOptions ++= commonScalacOptions,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("DemoApp"),
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
