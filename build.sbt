import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

// vdom — a React-style virtual-DOM UI library for Scala.js.
//
// An immutable VNode tree describes the UI; a reconciler diffs each new tree
// against the live DOM and mutates the DOM to match. Function components with
// positional hooks (useState first; more to follow) provide local state.
lazy val vdom = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "vdom",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Werror",
      // The builder DSL turns Strings/VNodes/Seqs into element children via
      // `Conversion` givens; enable the feature build-wide so user code needn't
      // import it per file.
      "-language:implicitConversions",
    ),

    // The demo's `main` runs automatically when the linked module loads in a
    // browser. Tests use ScalaTest's own entry point, not this.
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("io.github.edadma.vdom.demo.DemoApp"),

    // Emit a single classic <script>-loadable file (no ES modules), so the
    // demo's index.html can load it with a plain <script src> tag.
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),

    // Tests need a real DOM. jsdom provides one under Node (installed via the
    // project's package.json); the jsEnv is Test-scoped so the demo build stays
    // plain Node.
    Test / jsEnv := new JSDOMNodeJSEnv(),

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.1",
      // Spec-compliant macrotask scheduling, used to run passive effects after
      // the browser paints (render batching itself stays on the microtask queue).
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1",
      "org.scalatest" %%% "scalatest"  % "3.2.19" % Test,
    ),
  )
