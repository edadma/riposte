---
title: "Routing"
weight: 4
---

# Routing

**riposte-router** maps URLs to views for single-page apps. It is built on the core's
public API — `useSyncExternalStore` for the location, context for route params, the DSL
for links — so it touches no internals.

Add the dependency:

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-router" % "0.0.1"
```

## A router and some routes

`Router` establishes the routing context and tracks the current location. `Routes` picks
the best match among the routes you give it and renders that route's element. `route`
pairs a path pattern with an element:

```scala
import io.github.edadma.riposte.*
import io.github.edadma.riposte.router.*

def App(using Hooks): VNode =
  Router() {
    Routes(
      route("/")(Home),
      route("/about")(About),
      route("/users/:id")(UserPage),
    )
  }
```

`Router(mode = History)` uses the HTML History API and clean URLs; `Router(mode = Hash)`
uses `#/…` fragments for static hosting without server rewrites.

## Params

A `:name` segment is a parameter. Read the matched params with `useParams`:

```scala
def UserPage(using Hooks, RouterContext): VNode =
  val id = useParams.getOrElse("id", "")
  p(s"User $id")
```

## Links and navigation

`Link` navigates without a full page reload. `NavLink` adds an active class when its
target matches the current location (`end = true` matches the path exactly):

```scala
nav(
  Link("/")("Home"),
  NavLink("/about", activeClass = "current")("About"),
)
```

To navigate imperatively — say, after a form submit — call `navigate`:

```scala
button(onClick := (_ => navigate("/users/42")), "Open user 42")
```

## Nested routes

A route can take child routes; the parent renders an `Outlet` where the matched child
goes. `index(...)` is the child shown at the parent's own path. Params accumulate down
the branch:

```scala
Routes(
  route("/dashboard")(Dashboard)(
    index(Overview),
    route("settings")(Settings),
    route("users/:id")(UserDetail),
  ),
)

def Dashboard(using Hooks, RouterContext): VNode =
  div(
    h1("Dashboard"),
    Outlet,  // renders Overview, Settings, or UserDetail
  )
```

## Query strings

`useSearchParams` reads (and updates) the query portion of the URL, in both History and
Hash modes.
