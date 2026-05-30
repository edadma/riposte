package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom

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
