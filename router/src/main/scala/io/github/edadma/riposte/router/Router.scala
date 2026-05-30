package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom

// The routing surface: declare routes (flat or nested), render the branch that
// matches the current location, read the matched params, link between routes, and
// highlight the active link. Built entirely on the core's public API — `useLocation`
// (itself `useSyncExternalStore`), context for the params and the outlet, and the
// builder DSL for the links.

// A single route: a path pattern, how to render it from the captured params, and
// any nested child routes. A route with children matches a *prefix* of the path
// and renders its children where its view calls `Outlet()`; a leaf route must
// consume the whole remaining path. Child patterns are relative to the parent.
final case class Route private[router] (
    pattern:  String,
    build:    Params => VNode,
    children: Vector[Route] = Vector.empty,
):
  // Attach nested child routes: `route("/users")(UsersLayout())( route(":id")(…),
  // index(…) )`. The layout renders the matched child at its `Outlet()`.
  def apply(children: Route*): Route = copy(children = children.toVector)

// Declare a route whose view uses the captured params: `route("/users/:id")(p =>
// User(p("id")))`.
def route(pattern: String)(build: Params => VNode): Route = Route(pattern, build)

// Declare a route with a fixed view that needs no params: `route("/")(Home())`.
def route(pattern: String)(node: => VNode): Route = Route(pattern, _ => node)

// An index route: the default child of a layout, matching when the parent's path
// is hit exactly with nothing left over. `index(Dashboard())` or `index(p => …)`.
def index(build: Params => VNode): Route = Route("", build)
def index(node: => VNode): Route         = Route("", _ => node)

// The params of the nearest enclosing matched route — accumulated down the branch,
// so a child sees its ancestors' params too. Empty outside any route.
private val ParamsContext: Context[Params] = createContext(Map.empty)

def useParams()(using Hooks): Params = useContext(ParamsContext)

// The branch still to render below this point, innermost-first as a stack: the
// head is what the next `Outlet()` renders, the tail is what *its* outlet renders.
private val OutletContext: Context[Matched] = createContext(Vector.empty)

// Render the matched child of the enclosing layout route, or nothing if this route
// is the leaf. Place it in a layout's view where the nested route should appear.
val Outlet = view {
  renderChain(useContext(OutletContext))
}

// Render whichever branch of `routes` best matches the current location — the most
// specific match wins, so declaration order doesn't matter — and nothing when none
// match (add a `route("*")(…)` catch-all for a not-found view). The matched route's
// view renders at the top; nested routes render at each `Outlet()`.
private val RoutesComponent = component[Vector[Route]] { routes =>
  val path = useLocation()
  matchRoutes(routes, path) match
    case Some(chain) => renderChain(chain)
    case None        => empty
}

def Routes(routes: Route*): VNode = RoutesComponent(routes.toVector)

// The matched branch, outermost route first, each paired with its accumulated
// params (the route's own captures plus every ancestor's).
private type Matched = Vector[(Route, Params)]

// Render a matched branch: the head route's view, with its params on
// `ParamsContext` and the rest of the branch on `OutletContext` so its `Outlet()`
// renders the next route down. Recursion happens through `Outlet` reading the tail.
private def renderChain(chain: Matched): VNode = chain match
  case (route, params) +: rest =>
    ParamsContext.provide(params, OutletContext.provide(rest, route.build(params)))
  case _ => empty

// A branch's specificity, aggregated over the whole matched chain. Compared as a
// tuple: more literal segments beat fewer, then more params, then the absence of a
// splat, then a deeper branch. So `/users/new` beats `/users/:id`, both beat
// `/users/*`, and a matched nested branch outranks a shallower one.
private type Score = (Int, Int, Int, Int)

// Find the best-matching branch among `routes` for the path segments `seg`,
// threading inherited `parent` params down. A leaf must consume the whole
// remainder; a layout consumes a prefix and recurses into its children, falling
// back to terminating itself only when nothing is left to match.
private def matchBranch(routes: Vector[Route], seg: Vector[String], parent: Params): Option[(Matched, Score)] =
  val candidates = routes.iterator.flatMap { r =>
    matchPrefix(segments(r.pattern), seg).flatMap { (captured, leftover) =>
      val params  = parent ++ captured
      val pat     = segments(r.pattern)
      val literal = pat.count(s => s != "*" && !s.startsWith(":"))
      val param   = pat.count(_.startsWith(":"))
      val splat   = if pat.contains("*") then 1 else 0
      val self    = (literal, param, -splat, 1)
      val terminal = Option.when(leftover.isEmpty)((Vector((r, params)), self))
      if r.children.isEmpty then terminal
      else
        matchBranch(r.children, leftover, params) match
          case Some((childChain, (cl, cp, cs, cd))) =>
            Some(((r, params) +: childChain, (literal + cl, param + cp, -splat + cs, 1 + cd)))
          case None => terminal
    }
  }.toVector
  if candidates.isEmpty then None else Some(candidates.maxBy(_._2))

// The matched branch (outermost first) for `path`, or None if nothing matches.
private def matchRoutes(routes: Vector[Route], path: String): Option[Matched] =
  matchBranch(routes, segments(path), Map.empty).map(_._1)

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

// Whether `to` should count as active given the current `path`. With `end`, only
// an exact match; otherwise `to` may also be a path prefix (so `/users` stays
// active on `/users/7`) — but a `/` link is active only when the path is exactly
// `/`, since every path is "under" the root.
private def isActive(to: String, path: String, end: Boolean): Boolean =
  if end || to == "/" then path == to
  else path == to || path.startsWith(to + "/")

private val NavLinkComponent =
  component[(to: String, activeClass: String, end: Boolean, children: Children)] { p =>
    val path   = useLocation()
    val active = isActive(p.to, path, p.end)
    a(
      href := Location.hrefFor(p.to),
      if active then cls := p.activeClass else NoMod,
      onClick := { (e: dom.MouseEvent) =>
        if e.button == 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey then
          e.preventDefault()
          navigate(p.to)
      },
      p.children,
    )
  }

// A `Link` that adds `activeClass` (default "active") when its `to` matches the
// current location, for nav menus. Matching is prefix-based by default so a parent
// link stays lit on child routes; pass `end = true` for an exact match. Called
// curried — options first, then the children: `NavLink("/users")("Users")`,
// `NavLink("/", end = true)("Home")`.
def NavLink(to: String, activeClass: String = "active", end: Boolean = false)(children: VNode*): VNode =
  NavLinkComponent((to = to, activeClass = activeClass, end = end, children = children.toVector))
