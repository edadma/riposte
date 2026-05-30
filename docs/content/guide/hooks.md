---
title: "Hooks"
weight: 2
---

# Hooks

Hooks give a function component local state, side effects, and access to the DOM. They
are available through a `Hooks` context, which Riposte supplies while rendering. Declare
a component as `(using Hooks)` (or `Hooks ?=> VNode`) and call hooks at the top level:

```scala
import io.github.edadma.riposte.*

def Greeting(using Hooks): VNode =
  val (name, setName, _) = useState("world")
  div(
    input(value := name, onInput := (e => setName(e.target.value))),
    p(s"Hello, $name!"),
  )
```

## The rules

Hooks are positional: Riposte tracks them by call order within a component, so call them
unconditionally and in the same order on every render. Don't call a hook inside an `if`,
loop, or nested function — lift the condition inside the hook instead.

## useState

`useState(initial)` returns a triple: the current value, a `set` that replaces it, and an
`update` that derives the next value from the previous one. Use `update` when the new
value depends on the old, so concurrent updates compose:

```scala
val (count, set, update) = useState(0)

button(onClick := (_ => update(_ + 1)), s"Count: $count")
```

A setter schedules a re-render on the microtask queue; multiple sets in one event are
batched into a single render.

## useEffect

`useEffect(effect, deps)` runs a side effect after the DOM has been updated. It runs on
the macrotask queue, after the browser paints, so it never blocks a frame. The
dependency list controls when it re-runs — same deps, no re-run:

```scala
useEffect(() => {
  document.title = s"Count: $count"
}, Seq(count))
```

## Refs

A ref gives you the underlying DOM node. Bind a box (or a callback) with `ref :=` and
read it from an effect:

```scala
val box = Ref[dom.html.Input]()
input(ref := box)
useEffect(() => box.current.foreach(_.focus()), Nil)
```

## Beyond local state

For state shared across components, `useSyncExternalStore` is the seam the
[atoms module](/guide/state/) builds on. For time-sliced UI updates there's
`useTransition`. See those pages for the higher-level APIs that wrap them.
