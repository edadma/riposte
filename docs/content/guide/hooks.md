---
title: "Hooks"
weight: 2
---

Hooks give a component local state, side effects, memoized values, and access to the DOM.
They're available through a `Hooks` context that Riposte supplies while rendering — which
is exactly what the component constructors (`view`, `component`, `container`) set up. Call
hooks at the top level of a component body and you never pass the context explicitly:

```scala
import io.github.edadma.riposte.*

val Greeting = view {
  val (name, setName, _) = useState("world")
  div(
    input(value := name, onInput := (e => setName(targetValue(e)))),
    p(s"Hello, $name!"),
  )
}
```

The same is true inside `component[P] { props => … }` and `container { children => … }` —
all three run their body with the `Hooks` context in scope.

## The rules of hooks

Hooks are **positional**: Riposte identifies each one by its call order within a component,
not by name. Two rules follow, and they're the same as React's:

1. **Call hooks unconditionally, in the same order every render.** Never put a hook inside
   an `if`, a loop, or a nested function — that would shift the positions on some renders
   and scramble the state. Put the condition *inside* the hook instead.
2. **Call hooks only from a component body** (or from another hook), not from ordinary
   functions.

```scala
// Wrong — the hook is conditional:
if loggedIn then val (x, setX, _) = useState(0)

// Right — the hook always runs; the condition lives in how you use it:
val (x, setX, _) = useState(0)
if loggedIn then …use x…
```

## useState

`useState(initial)` returns a triple: the current value, a `set` that replaces it, and an
`update` that derives the next value from the previous one.

```scala
val (count, set, update) = useState(0)

button(onClick := (_ => update(_ + 1)), s"Count: $count")   // increment
button(onClick := (_ => set(0)), "Reset")                   // replace
```

Reach for `update` whenever the next value depends on the current one — it composes
correctly when several updates happen in the same event, where repeated `set(count + 1)`
calls would all see the same stale `count`. Setters schedule a re-render on the microtask
queue, so multiple updates in one event handler are batched into a single render.

The state can be any type — a `Vector`, a case class, whatever:

```scala
val (items, setItems, updateItems) = useState(Vector.empty[String])
updateItems(_ :+ "new")
```

The `initial` argument is **by-name**: it's evaluated only on the first render, when the
state cell is created, and never again. So an allocating initializer you don't want re-run
each render just works through the same signature — `useState(new Buffer)` builds the
buffer once, not on every render. No separate "lazy initializer" form is needed.

## useReducer

For state with several distinct transitions, `useReducer` centralizes them in one
function. It returns the current state and a `dispatch`:

```scala
enum Action:
  case Increment, Decrement, Reset

val (count, dispatch) = useReducer[Int, Action](
  (state, action) => action match
    case Action.Increment => state + 1
    case Action.Decrement => state - 1
    case Action.Reset     => 0,
  0,
)

button(onClick := (_ => dispatch(Action.Increment)), s"Count: $count")
```

## useEffect

`useEffect(body, deps)` runs a side effect *after* the DOM has been updated and the
browser has painted (it runs on the macrotask queue, so it never blocks a frame). Use it
for anything outside the render-to-DOM flow: network requests, subscriptions, timers,
manual DOM measurement, logging.

The body returns a **cleanup** — either a function that undoes the effect, or `noCleanup`
when there's nothing to tear down. Riposte runs the cleanup before the effect re-runs and
once more on unmount:

```scala
useEffect(
  () => {
    val id = dom.window.setInterval(() => tick(), 1000)
    () => dom.window.clearInterval(id)   // cleanup
  },
  Array(),                                // deps
)
```

The **dependency array** controls when the effect re-runs:

- `Array(a, b)` — re-run only when `a` or `b` changed since the last render.
- `Array()` (empty) — run once, after the first mount, and clean up on unmount.
- `null` — run after *every* render.

```scala
useEffect(
  () => { dom.document.title = s"Count: $count"; noCleanup },
  Array(count),
)
```

`useLayoutEffect` has the same signature but runs **synchronously after DOM mutations and
before paint** — use it (sparingly) when you must read layout and re-mutate the DOM
without the user seeing an intermediate frame.

## useRef

A ref is a mutable box whose `.current` survives across renders without causing a
re-render when you change it. Two uses:

**A handle to a DOM node.** Bind it with the `ref` attribute, then reach for the node
imperatively — the thing state can't do:

```scala
val inputRef = useRef[dom.html.Input | Null](null)

div(
  input(ref := inputRef, placeholder := "press focus →"),
  button(
    onClick := { _ =>
      val node = inputRef.current
      if node != null then node.focus()
    },
    "focus",
  ),
)
```

`ref` also accepts a callback — `ref := (node => …)` — invoked with the node on mount and
`null` on unmount, for when you need to react to attach/detach.

**Mutable instance state that isn't UI.** A ref is the place for a value you want to
remember but that shouldn't trigger rendering — a previous value, a timer id, a flag.

## useMemo and useCallback

Both cache something across renders, keyed on a dependency array, to avoid redundant work.

`useMemo(compute, deps)` caches the *result* of an expensive computation, recomputing only
when a dependency changes:

```scala
val sorted = useMemo(() => items.sortBy(_.name), Array(items))
```

`useCallback(fn, deps)` caches a *function value* so its identity is stable across renders
— important when you pass a handler to a `memo`-ized child (an inline lambda would be a new
object each render and defeat the memoization) or use it as an effect dependency:

```scala
val onSelect = useCallback((id: String) => setSelected(id), Array())
Row(rowProps, onSelect)   // Row can now actually bail out
```

## useId

`useId()` returns a stable, unique string id — the same value across re-renders of that
instance. Use it to wire an input to its label without hand-managing globally unique ids:

```scala
val id = useId()
label(htmlFor := id, "Email")
input(id := id, `type` := "email")
```

## useContext

Context passes a value down to descendants without threading it through every component's
props. Create a context with a default, provide a value to a subtree, and read the nearest
provided value with `useContext`:

```scala
val ThemeContext = createContext("light")

val Themed = view {
  val theme = useContext(ThemeContext)
  div(cls := s"theme-$theme", "…")
}

// Provide a value to a subtree:
ThemeContext.provide("dark", Themed())
```

Reading a context subscribes the component to it: when a provider higher up supplies a new
value, every descendant that reads the context re-renders. The [atoms](/guide/state/) and
[router](/guide/routing/) modules are both built on context.

## useTransition

`useTransition(target, durationMs)` eases a `Double` toward `target` over `durationMs`,
re-rendering the component each animation frame until it settles. It's a self-driving
animation primitive for simple numeric transitions — progress bars, fades, slides:

```scala
val (open, _, toggle) = useState(false)
val width = useTransition(if open then 100.0 else 0.0, 400)

div(
  css("width" -> s"$width%", "height" -> "0.5rem", "background" -> "#0c8599"),
  // a button elsewhere calls toggle(o => !o)
)
```

## useSyncExternalStore

The low-level seam for subscribing a component to an external, mutable data source. You
give it a `subscribe` function (called with a callback to run on change; returns an
unsubscribe) and a `getSnapshot` that reads the current value:

```scala
val value = useSyncExternalStore(
  subscribe   = cb => { source.addListener(cb); () => source.removeListener(cb) },
  getSnapshot = () => source.current,
)
```

You rarely call this directly — it's the foundation the [atoms](/guide/state/) module and
the router's location tracking are built on. Reach for those higher-level APIs first;
`useSyncExternalStore` is here for integrating a store Riposte doesn't already wrap.

## DOM hooks

A handful of higher-level hooks wrap common DOM patterns so you don't re-implement the
`addEventListener`/`removeEventListener` bookkeeping each time. They're built on the
primitives above and live in the core alongside them.

**`useEventListener(target, event, handler)`** attaches `handler` for `event` on `target`
for the component's lifetime, removing it on unmount. The latest `handler` is always used
(it's kept in a ref), so a handler closing over changing state stays current without
re-subscribing every render:

```scala
useEventListener(dom.window, "resize", _ => recompute())
```

**`useClickOutside(ref, active, handler)`** calls `handler` when a pointer press lands
outside the element held by `ref`, but only while `active` is true — the robust way to
dismiss an open popup or menu (a real document `pointerdown` listener checking containment,
not a focus-blur race):

```scala
val box = useRef[dom.Element | Null](null)
useClickOutside(box, menuOpen, () => setMenuOpen(false))
div(ref := box, /* … the menu … */)
```

**`useMediaQuery(query)`** returns a `Boolean` that tracks a CSS media query live —
re-rendering when it starts or stops matching:

```scala
val isWide = useMediaQuery("(min-width: 768px)")
val prefersDark = useMediaQuery("(prefers-color-scheme: dark)")
```

**`useIntersectionObserver(ref, rootMargin, threshold, once)`** reports whether the
element held by `ref` is in (or near) the viewport — for lazy loading and infinite scroll.
It returns a `Boolean` that flips to `true` when the element intersects the viewport
(expanded by `rootMargin`, default `"200px"`, so work can start just before it scrolls in).
With `once = true` (the default) it latches and disconnects — right for lazy-loading an
image; with `once = false` it tracks visibility both ways. Where `IntersectionObserver`
isn't available it returns `true`, so dependent content still loads:

```scala
val tile = useRef[dom.Element | Null](null)
val visible = useIntersectionObserver(tile)
div(ref := tile, when(visible)(img(src := heavyImageUrl)))
```

(salle's [`ImageCard`](/guide/salle/) is built on this hook.)
