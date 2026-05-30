---
title: "Quick Start"
weight: 2
---

# Quick Start

This walks through a complete, tiny Riposte application: a counter you can click.

## Describe the UI

A component is a zero-arg function whose return type is the context function
`Hooks ?=> VNode`. The `Hooks ?=>` part is what lets you call hooks like `useState` at the
top level — the reconciler supplies the `Hooks` context when it runs the component. The
builder DSL gives you HTML tags, attributes, and event handlers:

```scala
import io.github.edadma.riposte.*

def Counter(): Hooks ?=> VNode =
  val (count, set, update) = useState(0)

  div(
    p(s"Count: $count"),
    button(onClick := (_ => update(_ + 1)), "Increment"),
    button(onClick := (_ => set(0)), "Reset"),
  )
```

`useState` returns three things: the current `state`, a `set` function that replaces it,
and an `update` function that derives the next value from the previous one. Reach for
`update` whenever the new value depends on the old.

## Mount it

Rendering attaches a component to a DOM node and keeps it in sync as state changes:

```scala
import org.scalajs.dom

@main def main(): Unit =
  val root = dom.document.getElementById("app")
  render(Counter(), root)
```

Note the `Counter()` — you *call* the component. That yields a `Hooks ?=> VNode`, which an
implicit conversion wraps as a component node, so it can be passed to `render` or nested as
a child of any element.

```html
<!-- index.html -->
<div id="app"></div>
<script src="main.js"></script>
```

Build the JavaScript with `fastLinkJS` (development) or `fullLinkJS` (production):

```
sbt app/fastLinkJS
```

That is the whole loop: clicking a button calls a setter, the setter schedules a
re-render, the reconciler diffs the new tree against the old, and only the changed text
node is touched.

## Where to go next

- [Components & the DSL](/guide/components/) — tags, attributes, children, events.
- [Hooks](/guide/hooks/) — state, effects, refs, and the rules that govern them.
- [State with atoms](/guide/state/) — share state across components without prop-drilling.
- [Routing](/guide/routing/) — map URLs to views in a single-page app.
