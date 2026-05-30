---
title: "Components & the DSL"
weight: 1
---

# Components & the DSL

A Riposte UI is an immutable tree of `VNode`s. You build that tree with the DSL: a set
of element functions, attribute keys, and event keys. A *component* is a value built with
`view { … }` (or `component[P] { … }` when it takes props — see [Hooks](/guide/hooks/));
call it (`MyComponent()`) wherever a child is expected.

## Elements

Element functions take a mix of attributes, event handlers, and children as arguments:

```scala
import io.github.edadma.riposte.*

div(
  cls := "card",
  h2("Riposte"),
  p("A React-inspired library for Scala.js."),
)
```

Strings are valid children — they become text nodes. Sequences of `VNode`s are spread
in as children too, so you can map over data:

```scala
ul(
  items.map(item => li(item.name)),
)
```

## Attributes

Attributes are set with `AttrKey` values and the `:=` operator:

```scala
input(
  cls         := "field",
  value       := text,
  placeholder := "Type here…",
)
```

`cls` (aliased as `className`), `id`, `href`, `value`, and the rest of the everyday HTML
attributes are provided. For the long tail there are `aria(...)` and `data(...)`
helpers, and enumerated booleans like `draggable` render as `"true"`/`"false"` rather
than by presence.

## Events

Event handlers use `EventKey` values, also with `:=`. Handlers are typed by the event
they receive — `onClick` hands you a `MouseEvent`, `onInput` an input event:

```scala
button(onClick := (e => println(s"clicked at ${e.clientX}")), "Click me")
```

## Conditional rendering

`when` and `unless` include a node only when a condition holds; an `Option[VNode]` child
works too. A hidden branch leaves a placeholder so sibling positions stay stable across
renders:

```scala
div(
  when(loggedIn)(p(s"Welcome, $name")),
  unless(loggedIn)(a(href := "/login", "Sign in")),
)
```

## Escape hatches

- **`unsafeHtml(s)`** sets inner HTML directly (React's `dangerouslySetInnerHTML`).
- **`portal(target, child)`** renders a child into a different DOM node — modals, tooltips.
- **`errorBoundary(fallback)(child)`** catches throws during mount, patch, and
  re-render, showing the fallback instead of crashing the tree.
- **SVG** elements (`svg`, `path`, …) are namespaced automatically.

Continue to [Hooks](/guide/hooks/) for state and effects.
