package io.github.edadma.riposte

import org.scalajs.dom

// Typed event handlers: each event key carries its DOM event type, so handler
// bodies use typed members (`e.key`, `e.clientX`) with no cast. That these
// bodies compile is itself the type-level assertion; the runtime checks confirm
// the right event still reaches the listener.
class EventSpec extends DomSuite:

  test("onKeyDown delivers a typed KeyboardEvent"):
    val c = host()
    var pressed = ""
    val Comp = view {
      input(onKeyDown := (e => pressed = e.key)) // e: dom.KeyboardEvent, .key — no cast
    }
    render(Comp(), c)
    val ev = new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = "Enter" })
    c.querySelector("input").dispatchEvent(ev)
    assert(pressed == "Enter")

  test("onClick delivers a typed MouseEvent"):
    val c = host()
    var x = -1.0
    val Comp = view {
      button(onClick := (e => x = e.clientX)) // e: dom.MouseEvent, .clientX — no cast
    }
    render(Comp(), c)
    val ev = new dom.MouseEvent("click", new dom.MouseEventInit { clientX = 42.0 })
    c.querySelector("button").dispatchEvent(ev)
    assert(x == 42.0)

  test("a typed handler still drives state and re-renders"):
    val c = host()
    val Comp = view {
      val (last, set, _) = useState("")
      div(
        input(onKeyDown := (e => set(e.key))),
        span(cls := "last", last),
      )
    }
    render(Comp(), c)
    val ev = new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = "x" })
    c.querySelector("input").dispatchEvent(ev)
    Scheduler.flushSync()
    assert(c.querySelector("span.last").textContent == "x")

  test("on(name) gives a plain dom.Event handler for arbitrary events"):
    val c = host()
    var fired = false
    val Comp = view {
      div(on("customping") := (_ => fired = true))
    }
    render(Comp(), c)
    c.querySelector("div").dispatchEvent(new dom.Event("customping"))
    assert(fired)
