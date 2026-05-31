---
title: "Modules & API"
weight: 1
---

Riposte ships as four independently published artifacts under the `io.github.edadma`
organization, all at the same version. The three sibling artifacts depend on the core
transitively, so adding any of them is enough — you don't list `riposte` separately.

| Artifact          | Import                                  | What it gives you                      |
|-------------------|-----------------------------------------|----------------------------------------|
| `riposte`         | `io.github.edadma.riposte.*`            | Components, hooks, the DSL, rendering   |
| `riposte-atoms`   | `io.github.edadma.riposte.atoms.*`      | Shared atomic state                     |
| `riposte-router`  | `io.github.edadma.riposte.router.*`     | Client-side routing                     |
| `riposte-salle`   | `io.github.edadma.riposte.salle.*`      | Styled, skinnable component library     |

`riposte-salle` also depends on `riposte` transitively. The current published version is `0.0.2`.

## riposte (core)

The React-inspired core: an immutable `VNode` tree, a reconciler that diffs it against the
live DOM, function components, and hooks.

### Components

| API                                   | Purpose                                          |
|---------------------------------------|--------------------------------------------------|
| `view { … }`                          | A no-props component. Call it `C()`.             |
| `component[P] { p => … }`             | A component taking one prop (or a named tuple).  |
| `component[A, B] { (a, b) => … }`     | Positional props (up to four).                   |
| `container { children => … }`         | A component that wraps children (a slot).        |
| `container[P] { (p, children) => … }` | Props + children; called curried `C(p)(kids…)`.  |
| `memo(c)`                             | Bail out of re-render when props are unchanged.  |

See [Components & the DSL](/guide/components/).

### Hooks

| Hook                                   | Returns / does                                       |
|----------------------------------------|------------------------------------------------------|
| `useState(initial)`                    | `(value, set, update)`                               |
| `useReducer(reducer, initial)`         | `(state, dispatch)`                                  |
| `useEffect(body, deps)`                | Run a side effect after paint; `body` returns cleanup |
| `useLayoutEffect(body, deps)`          | Same, but synchronously before paint                 |
| `useRef(initial)`                      | A mutable `.current` box; bind to a node with `ref` |
| `useMemo(compute, deps)`               | Cache an expensive value                             |
| `useCallback(fn, deps)`                | Cache a function's identity                          |
| `useId()`                              | A stable unique id string                            |
| `useContext(ctx)`                      | The nearest provided context value                   |
| `useTransition(target, durationMs)`    | An eased `Double` animated each frame                |
| `useSyncExternalStore(sub, snapshot)`  | Subscribe to an external store                       |

`useState`'s `initial` is by-name (evaluated once), giving lazy initialization through the
same signature. Dependency arrays: `Array(a, b)` re-runs on change, `Array()` runs once,
`null` runs every render. Effect cleanups return a function or `noCleanup`.

**DOM hooks** (built on the primitives, for common DOM patterns):

| Hook                                                  | Does                                          |
|-------------------------------------------------------|-----------------------------------------------|
| `useEventListener(target, event, handler)`            | Subscribe to a DOM event for the lifetime     |
| `useClickOutside(ref, active, handler)`               | Fire when a press lands outside `ref`         |
| `useMediaQuery(query)`                                | Live `Boolean` for a CSS media query          |
| `useIntersectionObserver(ref, …)`                     | `Boolean` viewport visibility (lazy load)     |

See [Hooks](/guide/hooks/).

### The DSL

- **Elements** — a function per HTML tag (`div`, `p`, `ul`, `input`, `button`, `table`, …)
  plus namespaced SVG (`svg`, `path`, …).
- **Children** — `String`, `Int`, `VNode`, `Seq[VNode]`, and `Option[VNode]` are all valid
  children via automatic conversions.
- **Attributes** — `AttrKey := value` (`cls`/`className`, `id`, `href`, `value`, …);
  `aria(name)` / `data(name)` for the long tail; `css(name -> value, …)` for inline styles;
  enumerated booleans render `"true"`/`"false"`.
- **Events** — typed `EventKey := handler` (`onClick`, `onInput`, `onKeyDown`, …);
  `on(name)` for the rest; `targetValue(e)` reads an input's value.
- **Keys** — `key := id` on an element (or a second arg to a component) for stable list
  identity.
- **Conditionals** — `when(cond)(node)`, `unless(cond)(node)`.
- **Escape hatches** — `unsafeHtml(s)`, `portal(target, child)`,
  `errorBoundary(fallback)(child)`.

### Context

`createContext(default)` makes a `Context[T]`; `ctx.provide(value, child)` scopes a value
to a subtree; `useContext(ctx)` reads the nearest one.

### Rendering

`render(node, container)` mounts a tree into a DOM element and returns a `Root`. Calling
`render` again reconciles; `root.unmount()` tears down. `createRoot(container)` gives you
the `Root` without an initial render.

## riposte-atoms

Atomic, identity-based shared state built on the core's `useSyncExternalStore` seam.

- **Create** — `atom(value)` (primitive), `atom(get => …)` (derived, read-only),
  `atom(read, write)` (writable-derived), `action(write)` (write-only command).
- **Utilities** — `selectAtom`, `atomFamily`, `atomWithStorage`, `atomLoadable`.
- **Hooks** — `useAtom` (read+write), `useAtomValue` (read), `useSetAtom` (write).
- **Store** — values live in a `Store`; `Store.default` is the global one, `new Store`
  makes a scoped one, `StoreProvider(store) { … }` scopes it to a subtree.

See [State with Atoms](/guide/state/).

## riposte-router

Client-side routing built on the core's public API.

- **Setup** — `Router(mode) { … }`, `mode` = `RouterMode.History` (default) or
  `RouterMode.Hash`.
- **Routes** — `Routes(...)`, `route(pattern)(view)`, nested children via
  `route(...)(view)(children…)`, `index(view)`, and `Outlet` for the matched child.
- **Navigation** — `Link(to, children…)`, `NavLink(to, activeClass, end)(children…)`,
  `navigate(to, replace)`.
- **Params & query** — `useParams()` (a `Map`), `useSearchParams()` → `(params, set)`.
- **Advanced** — `route(...).catchErrors(fallback)`, `lazyView(load, fallback)`,
  `ScrollRestoration()`.

See [Routing](/guide/routing/).

## riposte-salle

A styled component library on top of the core.

- **Skin system** — components express intent (`Color`, `Size`, per-component variant
  enums like `ButtonVariant`); a `Skin` maps that to CSS classes. Ships `SalleSkin`
  (default, styled by `salle.css`) and `DaisySkin` (DaisyUI vocabulary). Set one with
  `SkinProvider(skin) { … }`; read it with `useSkin()`.
- **Theming** — one open `data-theme` set (ships `light`/`dark`, `system` follows the OS).
  `useTheme()` → `(theme, resolved, setTheme, toggle)`; plus `ThemeToggle` (sun/moon icon
  button) and `ThemeSelect(themes)`.
- **Components** — `Button`, `Input`, `Checkbox`, `Toggle`, `Select` (`Opt`), `ImageCard`.
- **Hook** — `useControllable(value, default, onChange)` for controlled/uncontrolled state.

See [Component library (salle)](/guide/salle/).
