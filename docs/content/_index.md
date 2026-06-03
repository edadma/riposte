---
title: Riposte
splash: true
heroTitle: A React-inspired frontend library for
heroHighlight: Scala.js
summary: Build user interfaces with function components, hooks, and a typed DSL — the React model, idiomatic Scala. An immutable VNode tree describes your UI; a reconciler diffs each new tree against the live DOM and mutates only what changed.
---

## Why Riposte

Riposte is a frontend library for building user interfaces in
[Scala.js](https://www.scala-js.org/), inspired by React. Components are functions, state
lives in hooks, and the UI is an immutable tree projected onto the DOM. The name is a
fencing term — the counter-thrust after a parry. Diffing is the parry; patching the DOM is
the riposte.

```scala
val Counter = view {
  val (count, _, update) = useState(0)
  button(onClick := (_ => update(_ + 1)), s"Count: $count")
}
```

## What's here

The sections split the docs into the usual layers: get up and running fast, understand
the model — components, hooks, shared state, routing, and data fetching — then look up the
exact API surface when you need it.

- **[Getting Started](/getting-started/)** — install Riposte and mount your first component.
- **[Guide](/guide/)** — the DSL, hooks, shared state with atoms, the router, and queries.
- **[Component library (salle)](/salle/)** — styled, skinnable widgets: form controls,
  display, overlays, and layout, with light/dark theming.
- **[Reference](/reference/)** — the published modules and their APIs.
