package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom

// The routing surface: declare routes, render whichever matches the current
// location, read the matched params, and link between routes. Built entirely on
// the core's public API — `useLocation` (itself `useSyncExternalStore`), context
// for the params, and the builder DSL for `Link`.

// A single route: a path pattern and how to render it from the captured params.
final case class Route private[router] (pattern: String, build: Params => VNode)

// Declare a route whose view uses the captured params: `route("/users/:id")(p =>
// User(p("id")))`.
def route(pattern: String)(build: Params => VNode): Route = Route(pattern, build)

// Declare a route with a fixed view that needs no params: `route("/")(Home())`.
def route(pattern: String)(node: => VNode): Route = Route(pattern, _ => node)

// The params of the nearest enclosing matched route, for components below the
// route's own view that didn't receive them directly. Empty outside any route.
private val ParamsContext: Context[Params] = createContext(Map.empty)

def useParams()(using Hooks): Params = useContext(ParamsContext)

// Render whichever of `routes` best matches the current location — the most
// specific match wins, so declaration order doesn't matter — and nothing when none
// match (add a `route("*")(...)` catch-all for a not-found view). The matched
// params are both passed to the route's `build` and provided via context to its
// descendants.
private val RoutesComponent = component[Vector[Route]] { routes =>
  val path = useLocation()
  bestMatch(routes, path) match
    case Some((r, params)) => ParamsContext.provide(params, r.build(params))
    case None              => empty
}

def Routes(routes: Route*): VNode = RoutesComponent(routes.toVector)

private def bestMatch(routes: Vector[Route], path: String): Option[(Route, Params)] =
  routes.iterator
    .flatMap(r => matchPath(r.pattern, path).map(p => (r, p)))
    .maxByOption((rp: (Route, Params)) => specificity(rp._1.pattern))

// A client-side link: a real anchor (accessible, and a modifier-click still opens a
// new tab), but a plain left-click navigates in-app instead of reloading the page.
def Link(to: String, children: VNode*): VNode =
  a(
    href := Location.hrefFor(to),
    onClick := { (e: dom.MouseEvent) =>
      if e.button == 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey then
        e.preventDefault()
        navigate(to)
    },
    children,
  )
