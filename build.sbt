import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import xerial.sbt.Sonatype.sonatypeCentralHost
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.3.3"

// --- Maven Central publishing ----------------------------------------------
// Metadata for the generated POM and the Sonatype Central wiring, mirroring the
// edadma cross-project template. Credentials live outside the repo (in
// ~/.sbt/.../sonatype.sbt), so nothing secret is checked in. Seven real library
// artifacts publish — the six riposte* modules plus the JS build of the vdom core
// they sit on; the demos, the JVM vdom test build, and the root aggregator skip it.
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
  .aggregate(vdom.js, vdom.jvm, vdom.native, riposte, atoms, router, query, forms, salle, salleDemo, salleE2E, demo)
  .settings(
    name                := "riposte-root",
    publish / skip      := true,
    publishLocal / skip := true,
  )

// vdom — the host-agnostic core extracted from riposte: the VNode model, the
// reconciler, the hooks runtime, the scheduler, and the `HostConfig` abstraction
// they mutate the world through. It names no platform type, so it cross-builds to
// JS (which riposte's DOM host drives) and JVM (where a headless TestHost drives
// the reconciler/hooks tests). The JVM target is the forcing function: a stray
// `org.scalajs.dom` reference would fail to compile there.
//
// Developed in-tree for now (riposte `.dependsOn(vdom.js)`); it will move to its own
// repo once mature, but the `io.github.edadma::vdom` coordinate is stable, so the JS
// and Native builds publish today — riposte's POM depends on the JS artifact, and the
// `suit` toolkit depends on the Native one. The JVM build exists solely for the
// headless reconciler/hooks tests and stays unpublished.
lazy val vdom = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("vdom"))
  .settings(
    name        := "vdom",
    description := "The host-agnostic core of riposte: the VNode model, reconciler, hooks runtime, and scheduler over a HostConfig abstraction. riposte is its DOM host.",
    scalacOptions ++= commonScalacOptions,
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
    // The suites install process-global seams before each test — the HostConfig, the
    // Scheduler's microtask/macrotask queues, and the transition/timer clocks — and reset
    // them in `withFixture`. That isolation holds only if suites run one at a time; run in
    // parallel they clobber one another's seams on the shared Scheduler singleton. ScalaTest
    // is already sequential within a suite; this serializes across them too.
    Test / parallelExecution := false,
  )
  .jvmSettings(
    publish / skip      := true,
    publishLocal / skip := true,
  )
  // The Native target is the SDL3 host's foundation: the same pure sources that drive a
  // browser and a headless JVM test host must also cross to a pixel-canvas host with no
  // DOM assumptions. The `suit` toolkit (its own repo) depends on this artifact, so it
  // publishes to Maven Central alongside the JS build.

// riposte — a React-style virtual-DOM UI library for Scala.js. The published
// library, in core/ so the repo root can stay a thin aggregator. It is the DOM
// host for `vdom`: it supplies the `HostConfig`, the HTML/SVG builder DSL, the
// DOM-specific hooks, and re-exports vdom's public API under its own package.
//
// An immutable VNode tree describes the UI; a reconciler diffs each new tree
// against the live DOM and mutates the DOM to match. Function components carry
// local state through positional hooks.
lazy val riposte = project
  .in(file("core"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(vdom.js)
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

// riposte-query — a TanStack-Query-style async data layer, a separate artifact
// built on riposte-atoms. A query cache is a reactive keyed store of async cells:
// `atomFamily` dedupes per key, `onMount` drives fetch-on-first-observe and a
// gcTime countdown on last-observer-leave, and `Store.forget` evicts. Depends on
// core (the hooks/DSL) and atoms (the cache substrate); scalajs-dom comes through
// both transitively.
lazy val query = project
  .in(file("query"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte, atoms)
  .settings(
    name := "riposte-query",
    scalacOptions ++= commonScalacOptions,
    Test / jsEnv := new JSDOMNodeJSEnv(),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
  )

// riposte-forms — a react-hook-form-style form layer, a separate artifact built on
// the core's public API only (useSyncExternalStore for form state, refs for the
// uncontrolled inputs, the DSL for what `register` spreads onto an element). Fields
// are uncontrolled — the DOM owns the text and the store reads it through a ref — so
// typing never re-renders; only form-state consumers do. Consumed by salle, but
// usable standalone for anyone who doesn't want salle.
lazy val forms = project
  .in(file("forms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(riposte)
  .settings(
    name := "riposte-forms",
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
    // Bake salle.css + its parts into the artifact as a string constant; the library
    // injects it at runtime (SalleStyles), so consumers need no separate CSS asset.
    Compile / sourceGenerators += Def.task {
      SalleCss.generate(baseDirectory.value / "css", (Compile / sourceManaged).value)
    }.taskValue,
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

// salle-e2e — the Playwright harness app. A NoModule bundle (like salle-demo) that
// mounts ONE component fixture chosen by the page's `?case=` query param, optionally
// re-skinned via `?skin=daisy`. The Playwright specs in salle-e2e/tests/ drive these
// fixtures in a real browser — the hover/focus/keyboard/scroll/lazy-load behaviour
// jsdom can't exercise. Build with `sbt salleE2E/fastLinkJS`. Never published.
lazy val salleE2E = project
  .in(file("salle-e2e"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(salle)
  .settings(
    name := "salle-e2e",
    scalacOptions ++= commonScalacOptions,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass             := Some("SalleE2E"),
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
