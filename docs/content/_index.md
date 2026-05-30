---
title: Riposte
splash: true
heroTitle: A React-style virtual DOM for
heroHighlight: Scala.js
summary: An immutable VNode tree describes your UI; a reconciler diffs each new tree against the live DOM and mutates only what changed. Function components, hooks, and a typed DSL — the React model, idiomatic Scala.
---

## Why Riposte

Riposte is a React-shaped virtual-DOM UI library for [Scala.js](https://www.scala-js.org/).
Components are functions, state lives in hooks, and the DOM is a projection of an
immutable tree. The name is a fencing term — the counter-thrust after a parry. Diffing is
the parry; patching the DOM is the riposte.

```scala
def Counter(using Hooks): VNode =
  val (count, _, update) = useState(0)
  button(onClick := (_ => update(_ + 1)), s"Count: $count")
```

## What's here

The sections split the docs into the usual layers: get up and running fast, understand
the model — components, hooks, shared state, and routing — then look up the exact API
surface when you need it.

- **[Getting Started](/getting-started/)** — install Riposte and mount your first component.
- **[Guide](/guide/)** — the DSL, hooks, atoms, and the router.
- **[Reference](/reference/)** — the published modules and their APIs.
