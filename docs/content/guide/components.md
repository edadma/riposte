---
title: "Components & the DSL"
weight: 1
---

A Riposte UI is an immutable tree of `VNode`s. You don't build that tree by hand — you
describe it with the **DSL** (element functions, attribute keys, event keys) and package
reusable pieces of it as **components**. This page covers both: first how to describe
markup, then how to factor it into components that take props and children.

## Elements

Every HTML element has a function of the same name. Each takes a varargs list of
*modifiers* — attributes, event handlers, and children, in any order:

```scala
import io.github.edadma.riposte.*

div(
  cls := "card",
  h2("Riposte"),
  p("A React-inspired library for Scala.js."),
)
```

The element functions cover the everyday HTML vocabulary — sectioning (`section`,
`article`, `nav`, `header`, `footer`, `main`, `aside`), grouping (`div`, `p`, `ul`, `ol`,
`li`, `pre`, `blockquote`, `figure`), text (`span`, `a`, `strong`, `em`, `code`, `small`,
`mark`), forms (`form`, `input`, `textarea`, `select`, `option`, `button`, `label`),
tables (`table`, `thead`, `tbody`, `tr`, `th`, `td`), and media (`img`, `video`, `audio`,
`source`). SVG elements (`svg`, `path`, `circle`, `g`, …) are there too and are namespaced
automatically.

### Children

A child can be another element, a `String` (becomes a text node), or an `Int` (rendered
as its decimal text). These conversions are automatic, so you can mix them freely:

```scala
p("You have ", strong(count), " unread messages.")
```

A `Seq[VNode]` is spliced in as a run of children — the idiom for rendering a list from
data:

```scala
ul(
  items.map(item => li(item.name))
)
```

An `Option[VNode]` is also a valid child: `Some(node)` renders the node, `None` renders an
empty placeholder (not nothing) so that toggling between the two keeps the surrounding
siblings — and their state — in stable positions.

## Attributes

Attributes are `AttrKey` values combined with a value through `:=`:

```scala
input(
  cls         := "field",
  id          := "email",
  value       := text,
  placeholder := "you@example.com",
  disabled    := submitting,        // Boolean
  tabIndex    := 0,                  // rendered as text
)
```

`:=` accepts a `String`, a `Boolean`, or a `Double`. `cls` (also available as
`className`), `id`, `href`, `src`, `value`, `name`, `type`, `placeholder`, `title`, and the
rest of the common attributes are provided as keys.

For attributes without a dedicated key, two families of helpers cover the long tail:

```scala
div(
  aria("label") := "Close",        // aria-label="Close"
  data("user-id") := userId,       // data-user-id="…"
)
```

Enumerated booleans — `draggable`, `spellcheck`, `contenteditable`, and the ARIA states —
render the literal strings `"true"` / `"false"` rather than toggling by presence, which is
what those attributes actually require.

### Typed properties

Some DOM things aren't attributes at all. A checkbox's `indeterminate` tri-state has no
attribute; a media element's `volume` and `currentTime` are numbers, not strings; and a
custom element (web component) expects rich **properties** — arrays, objects — not
stringified markup. For these, `prop[T](name)` sets a value straight on the live DOM node
via its property, untouched:

```scala
input(typ := "checkbox", indeterminate := true)  // a property with no attribute form
audio(volume := 0.5, currentTime := 12.0)         // numeric properties
myWidget(prop[js.Array[Item]]("items") := rows)    // rich data to a custom element
```

The value passes through with its real type, not as text; removing the prop on a later
render resets the property to `null`. `indeterminate` and the numeric
`volume` / `playbackRate` / `currentTime` / `valueAsNumber` / `valueAsDate` are provided as
ready-made keys; `prop[T]("name")` covers any other property.

Reach for `prop` only when an attribute genuinely can't express the value — ordinary
attributes (and the boolean/enumerated keys above) remain the right default, and are what
the host re-reads on patch. Under the hood this is vdom's `PropValue`, the one channel that
hands a host a typed value rather than a string — the same mechanism a non-DOM host (a
native renderer) uses to receive a colour or a size directly.

### Inline styles

`css` takes `name -> value` pairs and sets the `style` attribute:

```scala
div(
  css(
    "background"    -> "#0c8599",
    "height"        -> "0.75rem",
    "border-radius" -> "4px",
    "width"         -> s"$pct%",
  )
)
```

### Spreading a bundle of mods

A `Seq[Mod]` drops straight into a tag's mod list — the riposte analogue of JSX's
`{...props}`. A helper that returns a bundle of mods (a shared set of attributes, or a form
library's `register`) composes alongside the others:

```scala
val field = Seq(cls := "field", aria("required") := true)
input(field, typ := "email", placeholder := "you@example.com")
```

Scala forbids splicing a `Seq` with `*` next to other varargs, so this works through an
implicit conversion; `spread(field)` is the explicit form when a named call reads more
clearly. Bundles may nest. (salle's [forms layer](/guide/forms/) leans on this for
`register`.)

## Events

Event handlers are `EventKey` values, also combined with `:=`. Each key is typed by the
event it delivers, so the handler parameter needs no annotation — `onClick` hands you a
`dom.MouseEvent`, `onKeyDown` a `dom.KeyboardEvent`, `onInput` a `dom.Event`:

```scala
button(onClick := (e => println(s"clicked at ${e.clientX}, ${e.clientY}")), "Click me")

input(onKeyDown := (e => if e.key == "Enter" then submit()))
```

The full set covers mouse, pointer, keyboard, focus, drag, touch, and clipboard events.
For an event without a dedicated key, `on(name)` gives you a handler typed as the base
`dom.Event`. Reading the current value of an `<input>` is common enough to have a helper:

```scala
input(value := draft, onInput := (e => setDraft(targetValue(e))))
```

## Components

A component is a reusable, named piece of UI. It's an ordinary `val`, built with one of
three constructors depending on what it accepts. You *call* it — `Counter()`,
`Stat("Clicks", n)` — wherever a child is expected, and the result is a `VNode` like any
other.

Components matter for more than reuse: the reconciler tracks each mounted component by
identity, so hook state (see [Hooks](/guide/hooks/)) lives on the instance and survives
re-renders, and only the components whose inputs changed re-render.

### `view` — no props

The simplest component takes nothing. Build it with `view { … }`; the block is the render
body, and hooks work inside it:

```scala
val Counter = view {
  val (count, _, update) = useState(0)
  button(onClick := (_ => update(_ + 1)), s"Count: $count")
}
```

Call it with empty parens — `Counter()` — to get a node:

```scala
div(h1("Demo"), Counter())
```

### `component` — props

When a component needs inputs, build it with `component`. The one-prop form takes a single
value:

```scala
val Greeting = component[String] { name =>
  p(s"Hello, $name!")
}

Greeting("world")
```

For several inputs, the positional forms (`component[A, B]`, up to four) let you pass
arguments directly instead of bundling them:

```scala
val Stat = component[String, Int] { (label, value) =>
  div(
    cls := "stat",
    span(cls := "label", label),
    span(": "),
    strong(value),
  )
}

Stat("Clicks", clicks)
Stat("Likes", likes)
```

If you'd rather have named fields without declaring a case class, pass a single *named
tuple* to `component[P]`:

```scala
val Card = component[(title: String, count: Int)] { p =>
  div(span(p.title), strong(p.count))
}

Card((title = "Unread", count = 12))
```

A parent re-renders its children by passing them new props; each child re-renders only
when the props it received actually changed.

### `container` — children (slots)

A component that wraps arbitrary children — the analogue of React's `props.children` — is
built with `container`. The children arrive as a `Children` (a `Vector[VNode]`); splice
them wherever you like with the usual seq-as-children conversion:

```scala
val Card = container { children =>
  div(cls := "card", children)
}

Card(
  h2("Title"),
  p("Body text."),
)
```

A container can take props *and* children. Declare it with `container[P]` and call it
curried — props first, then the children:

```scala
val Panel = container[(title: String)] { (p, children) =>
  section(
    cls := "panel",
    h2(p.title),
    div(cls := "panel-body", children),
  )
}

Panel((title = "Settings"))(
  toggle,
  slider,
)
```

### Keyed lists

When you render a list that can reorder, insert, or delete, give each item a stable `key`
so the reconciler moves DOM nodes instead of rebuilding them — preserving their state and
focus. Inside an element, set it with the `key` attribute:

```scala
ul(
  items.map(item =>
    li(
      key := item.id,
      span(item.name),
      button(onClick := (_ => remove(item.id)), "✕"),
    )
  )
)
```

A component instance can be keyed too, by passing a key as the second argument:
`Row(rowProps, item.id)`.

### `memo` — skip unchanged re-renders

Wrapping a component in `memo` makes it bail out of a parent-driven re-render when its new
props are equal (`==`) to the previous ones — Riposte's `React.memo`. It still re-renders
on its own state changes:

```scala
val Row = memo(component[RowProps] { props => … })
```

Define the memoized component once as a stable `val`; calling `memo` inline in a render
produces a new identity each time and defeats the purpose. Props that carry freshly
allocated closures compare unequal every render, so stabilize handlers with `useCallback`
(see [Hooks](/guide/hooks/)) when memoizing.

## Escape hatches

A few constructs step outside the normal element-tree flow:

- **`unsafeHtml(s)`** sets an element's inner HTML directly — Riposte's
  `dangerouslySetInnerHTML`. Only pass markup you trust.
- **`portal(target, child)`** renders `child` into a *different* DOM node (one outside the
  component's own subtree) while keeping it logically part of the tree — for modals,
  tooltips, and overlays that must escape `overflow: hidden` or stacking contexts.
- **`errorBoundary(fallback)(child)`** catches throws during `child`'s mount, patch, and
  re-render, rendering `fallback(error)` in its place instead of tearing down the whole
  tree.

```scala
errorBoundary(err => div(cls := "error", s"Something broke: ${err.getMessage}")) {
  RiskyWidget()
}
```

Continue to [Hooks](/guide/hooks/) for state, effects, and the rest of the hook family.
