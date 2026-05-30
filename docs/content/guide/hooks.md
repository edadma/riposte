---
title: "Hooks"
weight: 2
---

# Hooks

Hooks give a component local state, side effects, and access to the DOM. They are
available through a `Hooks` context that Riposte supplies while rendering. Build a
component with `view { … }` and call hooks at the top level of the block — `view` is what
makes the context available, so you never pass it explicitly:

```scala
import io.github.edadma.riposte.*

val Greeting = view {
  val (name, setName, _) = useState("world")
  div(
    input(value := name, onInput := (e => setName(e.target.value))),
    p(s"Hello, $name!"),
  )
}
```

A component that takes props uses `component[P] { props => … }` instead, and one that
wraps children uses `container { children => … }`. All three run their body with the same
`Hooks` context, so hooks work the same way in each.

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
the macrotask queue, after the browser paints, so it never blocks a frame. The effect
returns either a cleanup function or `null` when there's nothing to tear down. The
dependency list — an `Array` — controls when it re-runs; same deps, no re-run:

```scala
useEffect(() => {
  dom.document.title = s"Count: $count"
  null
}, Array(count))
```

Pass `empty` (an empty dependency array) to run the effect only once, after the first
mount; pass `null` to run it after every render.

## Refs

A ref gives you the underlying DOM node. Bind a box (or a callback) with `ref :=` and
read it from an effect:

```scala
val box = Ref[dom.html.Input]()
input(ref := box)
useEffect(() => { box.current.foreach(_.focus()); null }, empty)
```

## Beyond local state

For state shared across components, `useSyncExternalStore` is the seam the
[atoms module](/guide/state/) builds on. For time-sliced UI updates there's
`useTransition`. See those pages for the higher-level APIs that wrap them.
