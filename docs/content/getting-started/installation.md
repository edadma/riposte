---
title: "Installation"
weight: 1
---

Riposte targets Scala.js. You need an sbt project with the Scala.js plugin enabled.

## Requirements

- Scala 3
- sbt with the `sbt-scalajs` plugin
- Node.js (for running and testing against a DOM via jsdom)

## Add the plugin

In `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.16.0")
```

## Add the dependency

Riposte is published for Scala.js under the `io.github.edadma` organization. Enable the
Scala.js plugin on your module and add the core library:

```scala
lazy val app = project
  .in(file("app"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    libraryDependencies += "io.github.edadma" %%% "riposte" % "0.1.0",
  )
```

The optional sibling artifacts are added the same way when you need them:

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-atoms"  % "0.1.0"
libraryDependencies += "io.github.edadma" %%% "riposte-router" % "0.1.0"
libraryDependencies += "io.github.edadma" %%% "riposte-salle"  % "0.1.0"
```

All three depend on `riposte` transitively, so you do not need to list the core separately.
([salle](/salle/) is the styled component library; it also ships a `salle.css` you
include in your page.)

## A DOM to render into

Riposte renders into a real DOM node. In the browser that is an element on the page; in
tests it is a [jsdom](https://github.com/jsdom/jsdom) document. Configure the jsdom
test environment in your build so tests have a DOM:

```scala
import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

Test / jsEnv := new JSDOMNodeJSEnv()
```

Continue to the [quick start](/getting-started/quick-start/) to mount a component.
