---
title: "Routing"
weight: 4
---

**riposte-router** maps URLs to views for single-page apps. It's built entirely on the
core's public API — `useSyncExternalStore` for the location, context for route params and
the outlet, the DSL for links — so it touches no internals and adds nothing you couldn't
build yourself; it just saves you from doing so.

Add the dependency (it pulls in the core transitively):

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-router" % "0.3.2"

import io.github.edadma.riposte.*
import io.github.edadma.riposte.router.*
```

## A router and some routes

`Router` establishes the routing context and tracks the current location. Inside it,
`Routes` picks the best-matching route for the current URL and renders it. `route` pairs a
path pattern with a view:

```scala
val App = view {
  Router() {
    Routes(
      route("/")(Home()),
      route("/about")(About()),
      route("/users/:id")(UserPage()),
      route("*")(NotFound()),
    )
  }
}
```

`Routes` picks the **most specific** match regardless of declaration order, so a static
`/users/new` wins over the dynamic `/users/:id` even if `:id` is declared first. A `"*"`
pattern is a catch-all — the idiomatic not-found route.

### History vs. hash mode

`Router` defaults to **History** mode — clean URLs (`/users/7`) via the History API, which
needs the server to fall back to `index.html` for unknown paths. For static hosting with
no such fallback, use **hash** mode, which keeps everything after a `#` (`/#/users/7`):

```scala
Router(RouterMode.Hash) {
  Routes(…)
}
```

## Params

A `:name` segment captures a path parameter. Read the matched params with `useParams`,
which returns a `Params` (a `Map[String, String]`):

```scala
val UserPage = view {
  val id = useParams().getOrElse("id", "")
  p(s"User $id")
}
```

`useParams` reads the params of the nearest enclosing route, and params **accumulate down
a nested branch** — a child route sees its own captures plus all of its ancestors'.

Alternatively, take the params directly in the route declaration:

```scala
route("/users/:id")(params => UserDetail(params("id")))
```

## Links and navigation

`Link` renders an `<a>` that navigates in-app — no full-page reload:

```scala
nav(
  Link("/", "Home"),
  Link("/about", "About"),
)
```

`NavLink` is a `Link` that adds an active CSS class when its target matches the current
location. It's curried — options first, then the children — and `end = true` requires an
exact path match (otherwise a prefix match counts, so `/users` is active on
`/users/7` too):

```scala
nav(
  NavLink("/", activeClass = "current", end = true)("Home"),
  NavLink("/users", activeClass = "current")("Users"),
)
```

To navigate imperatively — after a form submit, say — call `navigate`. Pass
`replace = true` to replace the current history entry instead of pushing a new one:

```scala
button(onClick := (_ => navigate("/users/42")), "Open user 42")
navigate("/login", replace = true)
```

## Nested routes

A route can take **child routes**. The parent matches a *prefix* of the path and renders
its matched child wherever its view places an `Outlet`; the child's pattern is relative to
the parent. `index(...)` declares the child shown when the parent's own path is matched
exactly:

```scala
Routes(
  route("/dashboard")(Dashboard())(
    index(Overview()),
    route("settings")(Settings()),
    route("users/:id")(UserDetail()),
  ),
)

val Dashboard = view {
  div(
    h1("Dashboard"),
    nav(
      NavLink("/dashboard/settings", end = true)("Settings"),
    ),
    Outlet,   // Overview, Settings, or UserDetail renders here
  )
}
```

This is how you build shared layouts: the parent route is the chrome (header, sidebar,
nav), and `Outlet` is the hole the active child fills.

## Query strings

`useSearchParams` reads and updates the query portion of the URL, in both History and hash
modes. It returns the current params and a setter; the setter takes the new params and a
`replace` flag:

```scala
val Search = view {
  val (params, setParams) = useSearchParams()
  val q = params.getOrElse("q", "")

  input(
    value := q,
    onInput := (e => setParams(Map("q" -> targetValue(e)), true)),
  )
}
```

### A single typed query value

`useQueryState` focuses `useSearchParams` down to **one typed key**, returning the same
`(value, set, update)` triple as the core's `useState`. The value lives in the URL — so it
survives reload and is shareable and bookmarkable — but reads and writes like ordinary
component state:

```scala
val Counter = view {
  val (count, setCount, updateCount) = useQueryState("count", 0)

  div(
    button(onClick := (_ => updateCount(_ - 1)), "−"),
    span(s" $count "),
    button(onClick := (_ => updateCount(_ + 1)), "+"),
  )
}
```

A `QueryCodec[T]` crosses the string boundary of the URL; codecs for `String`, `Int`,
`Long`, `Double`, and `Boolean` are provided `given`s, so the type is inferred from the
default. A custom type supplies its own `QueryCodec` (or you pass one explicitly). An
absent or unparseable value reads back as the default, so `?count=abc` is the default
rather than an error.

Setting the value to the default **drops the key** entirely, keeping URLs clean. Writes
**replace** the current history entry by default — so typing into a filter doesn't fill the
Back button — but pass `push = true` for a new entry:

```scala
setCount(5)               // ?count=5, replacing the current entry
setCount(5, push = true)  // …as a new history entry instead
updateCount(_ + 1)        // reads the live value, then writes
```

The component re-renders only when *this* key changes: a write to an unrelated query key
produces an equal snapshot and bails out. Independent keys set in the same tick each see
the other's write, so neither clobbers the other.

## Per-route error boundaries

`route(...).catchErrors(fallback)` wraps a route's view in an
[error boundary](/guide/components/#escape-hatches): if rendering that route — or any
descendant up to a nested route's own boundary — throws, `fallback(error)` shows in its
place instead of the failure tearing down the app. The route keeps matching, so fixing the
cause and re-rendering recovers:

```scala
route("/report/:id")(Report())
  .catchErrors(err => div(cls := "error", s"Couldn't load report: ${err.getMessage}"))
```

## Lazy routes

`lazyView` defers loading a view until it's first rendered, backing onto JavaScript's
dynamic `import()` so the bundler can split that view into its own chunk. It takes a
function returning a `js.Promise[VNode]` and an optional fallback shown while loading:

```scala
route("/admin")(
  lazyView(() => loadAdminPanel(), fallback = p("Loading…"))
)
```

Pair it with code-splitting in your build to keep the initial bundle small and load heavy
routes on demand.

## Scroll restoration

`ScrollRestoration` is a component that resets (and, for back/forward navigation, restores)
the scroll position as the location changes — the behavior browsers do for free on full
page loads but not for in-app navigation. Render it once, inside the `Router`:

```scala
val App = view {
  Router() {
    ScrollRestoration()
    Routes(…)
  }
}
```
