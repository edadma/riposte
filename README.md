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
  `useLayoutEffect`.
- **Scheduler** — state updates batch on the microtask queue and commit before
  paint; layout effects run before paint, passive effects after (on the
  macrotask executor).

## Hooks roadmap

`useRef`, `useMemo` / `useCallback`, `useReducer`, `useId`, and `useContext`
with providers.

## Development

```sh
sbt test           # run the jsdom-backed test suite
sbt fastLinkJS     # build the demo's JS
```

Then serve the repo root (e.g. `python3 -m http.server`) and open `index.html`
for the demo. Tests run under a real DOM via jsdom (`npm install` fetches it).

## License

ISC
