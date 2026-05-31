package io.github.edadma.riposte

import org.scalajs.dom
import scala.collection.mutable

// The .capture / .once / .passive modifiers on an event key. These drive real
// addEventListener options, so the tests assert observable DOM behaviour: a
// capture listener firing before a descendant's bubble listener, a once listener
// firing exactly once, and a passive listener coexisting with a normal one.
class EventOptionsSpec extends DomSuite:

  // A click that propagates, so capture/bubble phases reach ancestors.
  private def bubblingClick(el: dom.Element): Unit =
    val init = new dom.MouseEventInit { bubbles = true; cancelable = true }
    el.dispatchEvent(new dom.MouseEvent("click", init))
    Scheduler.flushSync()

  test("a capture-phase listener fires before a descendant's bubble listener"):
    val c     = host()
    val order = mutable.ArrayBuffer.empty[String]
    render(
      div(
        onClick.capture := (_ => order += "ancestor-capture"),
        button(onClick := (_ => order += "target-bubble")),
      ),
      c,
    )
    Scheduler.flushSync()
    bubblingClick(c.querySelector("button"))
    assert(order.toList == List("ancestor-capture", "target-bubble"))

  test("capture and bubble handlers for the same event coexist (distinct slots)"):
    val c     = host()
    val order = mutable.ArrayBuffer.empty[String]
    // Both on the same element: the capture key compiles to a separate listener
    // slot, so neither overwrites the other.
    render(
      div(
        onClick.capture := (_ => order += "capture"),
        onClick         := (_ => order += "bubble"),
        button(),
      ),
      c,
    )
    Scheduler.flushSync()
    bubblingClick(c.querySelector("button"))
    assert(order.contains("capture"))
    assert(order.contains("bubble"))

  test("a once listener fires only the first time"):
    val c     = host()
    var fired = 0
    render(button(onClick.once := (_ => fired += 1)), c)
    Scheduler.flushSync()
    val btn = c.querySelector("button")
    fireClick(btn)
    fireClick(btn)
    fireClick(btn)
    assert(fired == 1)

  test("a passive listener still receives the event"):
    val c     = host()
    var fired = false
    render(div(onScroll.passive := (_ => fired = true)), c)
    Scheduler.flushSync()
    c.querySelector("div").dispatchEvent(new dom.Event("scroll"))
    Scheduler.flushSync()
    assert(fired)
