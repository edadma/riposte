---
title: "Modules & API"
weight: 1
---

Riposte ships as six independently published artifacts under the `io.github.edadma`
organization, all at the same version. The sibling artifacts depend on the core
transitively, so adding any of them is enough — you don't list `riposte` separately.

| Artifact          | Import                                  | What it gives you                      |
|-------------------|-----------------------------------------|----------------------------------------|
| `riposte`         | `io.github.edadma.riposte.*`            | Components, hooks, the DSL, rendering   |
| `riposte-atoms`   | `io.github.edadma.riposte.atoms.*`      | Shared atomic state                     |
| `riposte-router`  | `io.github.edadma.riposte.router.*`     | Client-side routing                     |
| `riposte-query`   | `io.github.edadma.riposte.query.*`      | Async server-state cache               |
| `riposte-forms`   | `io.github.edadma.riposte.forms.*`      | react-hook-form-style form layer        |
| `riposte-salle`   | `io.github.edadma.riposte.salle.*`      | Styled, skinnable component library     |

`riposte-query` also depends on `riposte-atoms`; the rest depend only on `riposte`. The
current published version is `0.3.4`.

Under `riposte` sits **`vdom`** — a host-agnostic engine (the `VNode` model, the
reconciler, the hooks runtime, and the scheduler) that names no DOM type. `riposte` is its
DOM host: it supplies the `HostConfig` that performs the real DOM mutations, the HTML/SVG
DSL, and the DOM hooks, then re-exports vdom's API under `io.github.edadma.riposte`. `vdom`
is published (riposte depends on it transitively) and cross-builds to the JVM, where a
headless test host drives the reconciler and hooks — but you import `riposte`, never `vdom`
directly.

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
| `usePresence(present, exitMs)`         | Keep an element mounted through its exit animation    |
| `useSyncExternalStore(sub, snapshot)`  | Subscribe to an external store                       |
| `useImperativeHandle(ref, factory, deps)` | Expose an imperative handle to a parent's `ref`   |
| `useDeferredValue(value)`              | A copy of `value` that lags one commit               |
| `useDebouncedValue(value, delayMs)`    | `value` after it stops changing for `delayMs`        |
| `useThrottledValue(value, intervalMs)` | `value` at most once per `intervalMs` (leading+trailing) |

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
| `useResizeObserver(ref, onResize)`                    | Run a callback when an element's size changes  |
| `useFocusTrap(active)`                                 | Confine focus to a container while `active`    |

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
  `on(name)` for the rest; `targetValue(e)` reads an input's value. Chain `.capture`,
  `.once`, and/or `.passive` for listener options (`onScroll.passive := h`). A listener is
  re-registered only when its handler or its options change, so `.once` fires at most once
  per registration.
- **Refs** — `ref := box` binds an element to a `useRef` box (or a callback); pass a
  ref down as a prop to forward it, and `useImperativeHandle` to expose a custom handle.
- **Keys** — `key := id` on an element (or a second arg to a component) for stable list
  identity.
- **Conditionals** — `when(cond)(node)`, `unless(cond)(node)`.
- **Escape hatches** — `unsafeHtml(s)`, `portal(target, child)`,
  `errorBoundary(fallback)(child)`; a `Seq[Mod]` spreads into a tag's mod list (`spread(mods)`
  is the explicit form) — the `{...props}` analogue.

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
- **Eviction** — `Store.forget(atom)` drops an atom from the graph (runs its cleanup,
  detaches it, discards its cached state); `atomFamily` returns an `AtomFamily` with
  `contains`/`keys`/`remove`/`clear` to release keyed atoms.

See [State with Atoms](/guide/state/).

## riposte-router

Client-side routing built on the core's public API.

- **Setup** — `Router(mode) { … }`, `mode` = `RouterMode.History` (default) or
  `RouterMode.Hash`.
- **Routes** — `Routes(...)`, `route(pattern)(view)`, nested children via
  `route(...)(view)(children…)`, `index(view)`, and `Outlet` for the matched child.
- **Navigation** — `Link(to, children…)`, `NavLink(to, activeClass, end)(children…)`,
  `navigate(to, replace)`.
- **Params & query** — `useParams()` (a `Map`), `useSearchParams()` → `(params, set)`,
  `useQueryState(key, default)` → a `useState`-shaped triple for one typed URL query key.
- **Advanced** — `route(...).catchErrors(fallback)`, `lazyView(load, fallback)`,
  `ScrollRestoration()`.

See [Routing](/guide/routing/).

## riposte-query

A TanStack-Query-style async data layer — a keyed cache of server state — built on
riposte-atoms (each query is one atom).

- **Read** — `useQuery(key, fetcher, options, placeholderData)` → a `QueryResult` named tuple
  (`data`, `error`, `isLoading`, `isFetching`, `isError`, `isPlaceholderData`, `refetch`,
  `cancel`); `useSelectQuery(key, fetcher, select, …)` projects the cached data into a derived
  shape.
- **Cancellation** — `result.cancel()` (or `client.cancelQuery(key)`) aborts an in-flight
  fetch; a fetcher reads `QueryFetch.signal` to wire the abort into its request.
- **Keys** — `queryKey(parts*)` → a structured `Vector[Any]`; equal parts share an entry,
  prefixes drive invalidation.
- **Options** — `QueryOptions(staleTime, gcTime, retry, retryDelay, refetchOnWindowFocus,
  refetchOnReconnect, enabled, refetchInterval, keepPreviousData)` tune freshness, garbage
  collection, retry/backoff, focus/reconnect refetch, conditional fetching, polling, and
  cross-key data retention.
- **Mutations** — `useMutation(mutationFn, onMutate, onSuccess, onError, onSettled)` → a
  `MutationResult` (`mutate`/`mutateAsync`/`reset` + status flags) for the write side.
- **Infinite** — `useInfiniteQuery(key, fetchPage, initialPageParam, getNextPageParam,
  getPreviousPageParam, options)` → an `InfiniteQueryResult` (`pages`, `hasNextPage`,
  `fetchNextPage`, plus `hasPreviousPage`/`fetchPreviousPage` for bidirectional lists).
- **Client** — `QueryClient`, `QueryClientProvider(client)(child)`, `useQueryClient`; cache
  control via `invalidate`, `invalidatePrefix`, `refetch`, `setQueryData` (value or updater),
  `getQueryData`, `prefetchQuery`.

See [Data Fetching](/guide/queries/).

## riposte-forms

A react-hook-form-style form layer built on the core's public API. Fields are uncontrolled
(the DOM owns the value, read through a ref), so typing doesn't re-render.

- **Setup** — `useForm(defaultValues, mode, reValidateMode, resolver, asyncResolver)` → a
  `Form` handle; `mode` / `reValidateMode` are `ValidationMode.{OnSubmit,OnBlur,OnChange,
  OnTouched,All}`.
- **Fields** — `form.register(field, rules)` spreads mods onto an uncontrolled element;
  `form.handleSubmit(onValid, onInvalid)` validates then submits; `handleSubmitAsync` awaits
  an async valid handler (`isSubmitting` spans the `Future`).
- **Rules** — `Rules(required, minLength, maxLength, min, max, pattern, validate, messages)`;
  a failure is a `FieldError(kind, message)`; `Messages` overrides the defaults. A whole-form
  `Resolver` / `AsyncResolver` replaces the per-field rules with a schema check.
- **State** — `form.formState` (`errors`, `isDirty`, `isValid`, `isValidating`,
  `isSubmitting`, `isSubmitted`, `submitCount`, `touchedFields`, `dirtyFields`).
- **Imperative** — `setValue`/`getValue`/`getValues`, `trigger` / `triggerAsync`,
  `setError`/`clearErrors`, `reset`.
- **Watch** — `form.watch[T](field)`, `useWatch`, `useWatchAll`, `useWatchFields`.
- **Controlled** — `Controller(name, control, rules) { args => … }` adapts a controlled
  component (value + onChange) into the form.
- **Field arrays** — `useFieldArray(control, name, initial)` → a `FieldArray` (`fields` with
  stable-`id` rows; `append`/`prepend`/`insert`/`remove`/`move`/`swap`/`replace`/`values`).

See [Forms](/guide/forms/).

## riposte-salle

A styled component library on top of the core.

- **Skin system** — components express intent (`Color`, `Size`, per-component variant
  enums like `ButtonVariant`); a `Skin` maps that to CSS classes. Ships `SalleSkin`
  (default; its `salle.css` is baked into the artifact and self-installs at runtime) and
  `DaisySkin` (DaisyUI vocabulary). Set one with
  `SkinProvider(skin) { … }`; read it with `useSkin()`.
- **Theming** — one open `data-theme` set (ships `light`/`dark`, `system` follows the OS).
  `useTheme()` → `(theme, resolved, setTheme, toggle)`; plus `ThemeToggle` (sun/moon icon
  button) and `ThemeSelect(themes)`.
- **Form controls** — `Button`, `Input`, `Checkbox`, `Toggle`, `Select` (`Opt`), and
  `Segmented` (`SegmentedOpt`) — a single-choice `radiogroup` strip.
- **Display** — `ImageCard`; the previewable `Image` with its `Lightbox` viewer and
  `ImagePreviewGroup` (shared gallery lightbox); `Badge`/`Tag`/`CheckableTag` label pills;
  `Skeleton` / `SkeletonText` / `SkeletonImage` loading placeholders; `Descriptions`
  (`DescItem`) — a label/value metadata table (pure `descriptionsRows`).
- **Feedback** — `Spinner` (indeterminate, with overlay mode), `Progress` (linear),
  `RadialProgress` (ring); `Empty` (`EmptyImage`) — a `role=status` empty-state placeholder.
- **Banners** — `Carousel` (`CarouselEffect`) — a rotating slide region with autoplay,
  arrows, dots, keyboard, and swipe; `Hero` — a full-bleed headline band.
- **Navigation** — `Pagination` (controlled; pure `paginationRange`/`pageCount`);
  `Dropdown` menu button (`MenuItem`/`MenuDivider`); `Tabs` (data-driven panels, `Tab`,
  `TabsVariant`/`TabsPosition`); `Breadcrumb` (`Crumb`) — a `nav` hierarchy trail.
- **Overlays** — `Modal` (portal dialog) and `Drawer` (edge-docked panel) — both focus-
  trapped via the core `useFocusTrap`; `Toast`/`Toaster` (imperative `toast` API); and
  `Tooltip` (floating hint, hover/focus/click) — all animated via the core `usePresence` hook.
- **Layout** — the page shell `Layout` (with `Header`/`Content`/`Footer`/`Sider` regions),
  `Navbar` (three-zone, responsive collapse) and content `Footer` (`Footer.Title`); plus
  `Row`/`Col` (24-column grid, responsive spans) and `Masonry` / `MasonryResponsive` (packed
  columns; pure `layoutMasonry`).
- **Hook** — `useControllable(value, default, onChange)` for controlled/uncontrolled state.

See [Component library (salle)](/salle/).
