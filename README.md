# vdom

A small, React-shaped **virtual-DOM UI library for Scala.js**.

An immutable `VNode` tree describes the UI; a reconciler diffs each new tree
against the live DOM and mutates the DOM to match. Function components hold
local state through positional hooks. Built on
[scalajs-dom](https://github.com/scala-js/scala-js-dom).

```scala
import io.github.edadma.vdom.*
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
  and `useContext`.
- **Refs to DOM nodes** — `ref := someRefBox` writes the live element into a
  `useRef` box (`.current`), cleared to null on unmount; `ref := (node => …)`
  takes a callback instead. For focus, measurement, and other imperative work.
- **Conditional children** — `when(cond)(node)` / `unless`, and an
  `Option[VNode]` child; a hidden branch holds its slot so siblings stay put.
- **Context** — `createContext` / `ctx.provide(value, child)` / `useContext`,
  resolved by walking up the live tree (nearest provider wins); consumers
  subscribe, so they update even from behind a memoized ancestor.
- **`memo`** — wrap a component to skip a parent-driven re-render when its props
  are unchanged (it still re-renders on its own state or a consumed context).
- **Scheduler** — state updates batch on the microtask queue and commit before
  paint; layout effects run before paint, passive effects after (on the
  macrotask executor).

## Not yet

Error boundaries, SVG namespacing, portals, and a broader typed event set.

## Layout

- `src/` — the `vdom` library (the published artifact)
- `demo/` — a runnable showcase in its own subproject that depends on the
  library, so no demo code ends up in the published artifact

## Development

```sh
sbt test              # run the library's jsdom-backed test suite
sbt demo/fastLinkJS   # build the demo's JS
```

Then serve `demo/` (e.g. `cd demo && python3 -m http.server`) and open
`index.html`. Tests run under a real DOM via jsdom (`npm install` fetches it).

## License

ISC
