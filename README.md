# riposte

A small, React-shaped **virtual-DOM UI library for Scala.js**.

An immutable `VNode` tree describes the UI; a reconciler diffs each new tree
against the live DOM and mutates the DOM to match. Function components hold
local state through positional hooks. Built on
[scalajs-dom](https://github.com/scala-js/scala-js-dom).

> *riposte* — in fencing, the swift counter-thrust that follows a parry. Here:
> an event or state change comes in, the reconciler diffs (parry), and patches
> the DOM in answer (riposte). The word traces back through Italian
> sword-fighting to Latin *respondēre* — "to respond."

```scala
import io.github.edadma.riposte.*
import org.scalajs.dom

val Counter = view {
  val (count, _, update) = useState(0)
  div(
    p(s"Count: $count"),
    button(onClick := (_ => update(_ - 1)), "−"),
    button(onClick := (_ => update(_ + 1)), "+"),
  )
}

@main def run(): Unit =
  render(Counter(), dom.document.getElementById("app"))
```

## Why context functions

A component body is a `Hooks ?=> VNode` context function, so the hook context
is threaded implicitly — you call `useState(0)` directly, with no `hooks`
parameter to name or pass around.

## Props

A component with one prop uses `component[P]`. For several props, pass them
positionally — no wrapper type:

```scala
val Stat = component[String, Int] { (label, value) =>
  div(span(label), strong(value))
}
Stat("Clicks", clicks)
```

When you want field names without declaring a case class, use a named tuple as
the single prop:

```scala
val Card = component[(title: String, count: Int)] { p =>
  div(span(p.title), strong(p.count))
}
Card((title = "Hi", count = 3))
```

Both keep component identity stable (so hook state survives prop changes) and
work with `memo`, which compares props structurally.

## Refs

`ref := box` binds an element to a `useRef` box so you can reach its live DOM
node imperatively — focus, measure, or hand it to a browser API:

```scala
val Field = view {
  val inputRef = useRef[dom.html.Input | Null](null)
  div(
    input(ref := inputRef),
    button(onClick := { _ =>
      val node = inputRef.current
      if node != null then node.focus()
    }, "focus"),
  )
}
```

The node is written to `.current` on mount and cleared to null on unmount. Pass
a callback instead — `ref := (node => …)`, called with the node on mount and
null on unmount — when you'd rather run code than hold a handle.

## Conditional children

`when(cond)(node)` renders the node only when the condition holds (`unless` is
its negation), and an `Option[VNode]` renders `Some` or nothing — the Scala
stand-ins for React's `{cond && <X/>}` and `{maybe}`:

```scala
div(
  when(items.isEmpty)(p("nothing here yet")),
  errorBanner,           // errorBanner: Option[VNode]
  ul(items.map(row)),
)
```

A hidden branch leaves an empty placeholder in its slot rather than dropping the
child, so flipping the condition never disturbs the DOM or state of the siblings
around it. The node passed to `when`/`unless` is by-name, so it is built only
when actually shown.

## Children (slots)

`container` builds a component you call with child nodes — React's
`props.children`. The body receives them as a `Vector[VNode]` and places them
wherever it likes:

```scala
val Card = container { children =>
  div(cls := "card", children)
}
Card(h2("Title"), p("Body"))
```

For props alongside the children, use `container[P]` and call it curried:

```scala
val Panel = container[(title: String)] { (p, children) =>
  section(h2(p.title), div(cls := "body", children))
}
Panel((title = "Settings"))(toggle, slider)
```

Children are the component's props, so identity, surviving hook state, and
`memo` all behave as for any other component. A container can hold its own
state — e.g. a collapsible that shows the children it's given only when open.

## External state

App-level state lives *outside* the component tree, in a store of your choosing.
`useSyncExternalStore(subscribe, getSnapshot)` is the seam that connects any such
store to the re-render model:

```scala
val count = useSyncExternalStore(store.subscribe, () => store.get.count)
```

`subscribe` registers a callback the store fires on every change (and returns an
unsubscribe); `getSnapshot` reads the current value. The component re-renders
only when the snapshot actually changes (`!=`), so a `getSnapshot` that selects a
slice **bails out** when that slice is unchanged — the basis for efficient
selector-based stores. This is the integration point for a hand-rolled store, a
reducer-over-context, or a reactive library like Airstream (a thin bridge hook
wraps a `Signal`/`Var`), none of which the core needs to know about.

The **`riposte-atoms`** module (in `atoms/`, a separate artifact) is one such
engine, built entirely on this seam: Jotai-inspired atomic state.

```scala
val count   = atom(0)                 // a writable primitive atom
val doubled = atom(g => g(count) * 2) // derives from it, tracking the dependency

val Counter = view {
  val (n, setN) = useAtom(count)      // read + write, like useState but shared
  button(onClick := (_ => setN(n + 1)), s"count: $n")
}
val Doubled = view {
  val d = useAtomValue(doubled)       // re-renders only when `doubled` changes
  span(s"doubled: $d")
}
```

Atoms are identity-based units of shared state; a derived atom recomputes when an
atom it reads changes, and a component re-renders only for the atoms it actually
reads — fine-grained by construction, no selectors needed.

Beyond primitive and read-only derived atoms, the module also has writable-derived
atoms (`atom(read, write)`) and write-only `action` atoms, a `StoreProvider` for
scoping state to a subtree, `selectAtom` / `atomFamily` / `onMount` /
`atomWithStorage` utilities, and `atomLoadable` for `Future`-backed values
(`Loadable` = Loading / Data / Errored, no Suspense).

## What's here

- **VNode tree** — `VText`, `VElement`, `VFragment`, `VComponent`, `VEmpty`.
- **Builder DSL** — `div`/`span`/`button`/… with `cls := …`, `onClick := …`,
  `key := …`, `css(…)`; Strings, VNodes, and `Seq[VNode]` become children
  automatically.
- **Reconciler** — mount / patch / unmount, attribute-vs-property handling,
  event-listener swapping, and a keyed child diff that moves only the DOM
  blocks that are actually out of place (so focus and cursor survive).
- **Hooks** — `useState` (returns `(state, set, update)`), `useEffect`,
  `useLayoutEffect`, `useRef`, `useMemo`, `useCallback`, `useReducer`, `useId`,
  `useContext`, `useSyncExternalStore`, and `useTransition` (eased animation —
  returns the current value on its way to a target, driving its own frames).
- **Refs to DOM nodes** — `ref := someRefBox` writes the live element into a
  `useRef` box (`.current`), cleared to null on unmount; `ref := (node => …)`
  takes a callback instead. For focus, measurement, and other imperative work.
- **Conditional children** — `when(cond)(node)` / `unless`, and an
  `Option[VNode]` child; a hidden branch holds its slot so siblings stay put.
- **Children / slots** — `container { children => … }` (and `container[P]` for
  props plus children) builds a component you call with child nodes, the
  analogue of React's `props.children`.
- **Typed events** — `onClick` hands you a `dom.MouseEvent`, `onKeyDown` a
  `dom.KeyboardEvent`, `onFocus`/`onBlur` a `dom.FocusEvent` — no cast at the
  call site; `on(name)` covers anything else as a `dom.Event`.
- **Context** — `createContext` / `ctx.provide(value, child)` / `useContext`,
  resolved by walking up the live tree (nearest provider wins); consumers
  subscribe, so they update even from behind a memoized ancestor.
- **`memo`** — wrap a component to skip a parent-driven re-render when its props
  are unchanged (it still re-renders on its own state or a consumed context).
- **Scheduler** — state updates batch on the microtask queue and commit before
  paint; layout effects run before paint, passive effects after (on the
  macrotask executor).
- **SVG** — `svg`/`g`/`path`/`circle`/… render in the SVG namespace
  automatically (every descendant of an `<svg>`, including children added in a
  later patch); `svgText` for the SVG `<text>` element.
- **Portals** — `portal(target, child)` renders a subtree into a different DOM
  container while keeping it in the component tree (events and re-renders still
  flow); for modals and overlays that must escape an ancestor's clipping.
- **Error boundaries** — `errorBoundary(fallback)(child)` contains a render
  throw in its subtree and shows a fallback instead of tearing down the tree,
  on the mount, parent-patch, and state-update re-render paths; recovers when a
  later render succeeds.

## Not yet

`foreignObject` HTML re-entry inside SVG, and form helpers.

## Layout

- `core/` — the `riposte` library (the published artifact)
- `atoms/` — `riposte-atoms`, a Jotai-inspired atomic-state module (a separate
  artifact) built on the core's `useSyncExternalStore`
- `demo/` — a runnable showcase in its own subproject that depends on the
  library, so no demo code ends up in the published artifact
- the repo root is a thin aggregator project (not published); a task run there
  fans out to every module

## Development

```sh
sbt test              # run every module's jsdom-backed suite (core + atoms)
sbt riposte/test      # just the library (project id is `riposte`, in core/)
sbt atoms/test        # just the atoms module
sbt demo/fastLinkJS   # build the demo's JS
```

Then serve `demo/` (e.g. `cd demo && python3 -m http.server`) and open
`index.html`. Tests run under a real DOM via jsdom (`npm install` fetches it).

## License

ISC
