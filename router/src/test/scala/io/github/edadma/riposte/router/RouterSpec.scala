package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

// The integration: navigation changes the rendered route, the most specific match
// wins, params reach the view and its descendants, Link clicks navigate in-app,
// hash mode works, and a popstate re-renders from the current location.
class RouterSpec extends RouterSuite:

  test("renders the matched route and updates when the location changes"):
    start()
    val c = host()
    render(
      Routes(
        route("/")(span(cls := "v", "home")),
        route("/users/:id")(p => span(cls := "v", p("id"))),
        route("*")(span(cls := "v", "nf")),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "home")
    navigate("/users/7")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "7")
    navigate("/nowhere")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "nf") // the catch-all

  test("the most specific route wins regardless of declaration order"):
    start()
    val c = host()
    render(
      Routes(
        route("/users/:id")(_ => span(cls := "v", "param")),
        route("/users/new")(span(cls := "v", "new")), // less specific declared first
      ),
      c,
    )
    navigate("/users/new")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "new")
    navigate("/users/42")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "param")

  test("a Link click navigates in-app without reloading"):
    start()
    val c = host()
    render(
      div(
        Link("/users/3", "go"),
        Routes(
          route("/")(span(cls := "v", "home")),
          route("/users/:id")(p => span(cls := "v", p("id"))),
        ),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "home")
    click(c.querySelector("a"))
    assert(c.querySelector("span.v").textContent == "3")
    assert(dom.window.location.pathname == "/users/3")

  test("useParams exposes the matched params to descendant components"):
    start()
    val c = host()
    val Inner = view {
      val p = useParams()
      span(cls := "v", p("id"))
    }
    render(Routes(route("/users/:id")(_ => Inner())), c)
    navigate("/users/9")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "9")

  test("hash mode reads and writes the location after the #"):
    start(RouterMode.Hash)
    val c = host()
    render(
      Routes(
        route("/")(span(cls := "v", "home")),
        route("/about")(span(cls := "v", "about")),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "home")
    navigate("/about")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "about")
    assert(dom.window.location.hash == "#/about")

  test("a popstate event re-renders from the current location"):
    start()
    val c = host()
    render(
      Routes(
        route("/")(span(cls := "v", "home")),
        route("/x")(span(cls := "v", "x")),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "home")
    // Simulate the back/forward button. jsdom's history.back() doesn't fire
    // popstate, so set the URL and dispatch the event the browser would.
    dom.window.history.replaceState(null, "", "/x")
    dom.window.dispatchEvent(new dom.Event("popstate"))
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "x")

  test("nested routes render the matched child at the layout's Outlet"):
    start()
    val c       = host()
    val Layout  = view { div(cls := "layout", span(cls := "tag", "L"), Outlet()) }
    render(
      Routes(
        route("/users")(Layout())(
          index(span(cls := "v", "index")),
          route(":id")(p => span(cls := "v", p("id"))),
        ),
      ),
      c,
    )
    navigate("/users")
    Scheduler.flushSync()
    assert(c.querySelector("div.layout span.tag").textContent == "L")
    assert(c.querySelector("span.v").textContent == "index") // the index route
    navigate("/users/7")
    Scheduler.flushSync()
    assert(c.querySelector("div.layout") != null)            // layout persists
    assert(c.querySelector("span.v").textContent == "7")     // only the child swapped

  test("nested params accumulate down the branch"):
    start()
    val c    = host()
    val Team = view {
      val p = useParams()
      span(cls := "v", s"${p("org")}/${p("team")}")
    }
    val Org = view { div(Outlet()) }
    render(
      Routes(
        route("/org/:org")(Org())(
          route("team/:team")(Team()),
        ),
      ),
      c,
    )
    navigate("/org/acme/team/7")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "acme/7")

  test("NavLink adds its active class only while its path matches"):
    start()
    val c = host()
    render(
      div(
        NavLink("/users")("users"),
        NavLink("/about", end = true)("about"),
      ),
      c,
    )
    navigate("/users/7")
    Scheduler.flushSync()
    assert(c.querySelectorAll("a.active").length == 1)       // prefix match keeps /users lit
    assert(c.querySelector("a.active").textContent == "users")
    navigate("/about")
    Scheduler.flushSync()
    assert(c.querySelector("a.active").textContent == "about") // exact (end) match

  test("useSearchParams reads the query and its setter navigates with a new one"):
    start()
    val c    = host()
    val View = view {
      val (params, setParams) = useSearchParams()
      div(
        span(cls := "q", params.getOrElse("q", "-")),
        button(onClick := (_ => setParams(Map("q" -> "hi"), false)), "set"),
      )
    }
    render(View(), c)
    navigate("/search?q=hello")
    Scheduler.flushSync()
    assert(c.querySelector("span.q").textContent == "hello")
    click(c.querySelector("button"))
    assert(c.querySelector("span.q").textContent == "hi")
    assert(dom.window.location.search == "?q=hi")

  test("hash mode separates the path from the query"):
    start(RouterMode.Hash)
    val c    = host()
    val View = view {
      val (params, _) = useSearchParams()
      span(cls := "q", params.getOrElse("q", "-"))
    }
    render(Routes(route("/s")(View())), c)
    navigate("/s?q=hash")
    Scheduler.flushSync()
    assert(c.querySelector("span.q").textContent == "hash")

  test("catchErrors on a route shows its fallback when the view throws"):
    start()
    val c    = host()
    val Boom = view { throw new RuntimeException("kaboom") }
    render(
      Routes(
        route("/")(span(cls := "v", "home")),
        route("/boom")(Boom())
          .catchErrors(e => span(cls := "v", s"caught:${e.getMessage}")),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "home")
    navigate("/boom")
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "caught:kaboom") // boundary contained it

  test("a lazy view shows the fallback until its chunk loads, then the loaded view"):
    given ExecutionContext = ExecutionContext.parasitic
    start()
    val c    = host()
    val gate = Promise[VNode]()
    render(lazyView(() => gate.future, fallback = span(cls := "v", "loading")), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "loading")
    gate.success(span(cls := "v", "loaded")) // parasitic runs the completion now
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "loaded")

  test("a lazy view renders onError when its chunk fails to load"):
    given ExecutionContext = ExecutionContext.parasitic
    start()
    val c    = host()
    val gate = Promise[VNode]()
    render(
      lazyView(
        () => gate.future,
        fallback = span(cls := "v", "loading"),
        onError = e => span(cls := "v", s"err:${e.getMessage}"),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "loading")
    gate.failure(new RuntimeException("offline")) // parasitic runs the completion now
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "err:offline")

  test("ScrollRestoration scrolls to top on a new path and restores on return"):
    start()
    var pos = (0d, 0d)
    resetScrollRestoration(() => pos, (x, y) => pos = (x, y))
    val c = host()
    render(ScrollRestoration(), c)
    Scheduler.flushSync()                              // mounts on "/", scrolls to top
    pos = (0d, 320d)                                   // the user scrolls down on "/"
    dom.window.dispatchEvent(new dom.Event("scroll"))  // ScrollRestoration records it
    navigate("/b")
    Scheduler.flushSync()
    assert(pos == (0d, 0d))                             // a fresh page starts at the top
    navigate("/")
    Scheduler.flushSync()
    assert(pos == (0d, 320d))                           // returning restores where we were
