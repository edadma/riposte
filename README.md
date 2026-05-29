# vdom

A small, React-shaped **virtual-DOM UI library for Scala.js**.

An immutable `VNode` tree describes the UI; a reconciler diffs each new tree
against the live DOM and mutates the DOM to match. Function components hold
local state through positional hooks. Built on
[scalajs-dom](https://github.com/scala-js/scala-js-dom).

```scala
import io.github.edadma.vdom.*
import org.scalajs.dom

val Counter = view("Counter") {
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
- **Context** — `createContext` / `ctx.provide(value, child)` / `useContext`,
  resolved by walking up the live tree (nearest provider wins); consumers
  subscribe, so they update even from behind a memoized ancestor.
- **`memo`** — wrap a component to skip a parent-driven re-render when its props
  are unchanged (it still re-renders on its own state or a consumed context).
- **Scheduler** — state updates batch on the microtask queue and commit before
  paint; layout effects run before paint, passive effects after (on the
  macrotask executor).

## Not yet

Ref forwarding to DOM nodes, error boundaries, SVG namespacing, and a broader
typed event set.

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
