package io.github.edadma.riposte

import org.scalajs.dom

// A portal renders its child into a foreign DOM container while keeping the
// child in the component tree, so state and re-renders still flow to it. A
// placeholder anchor holds the slot in the main tree.
class PortalSpec extends DomSuite:

  test("a portal renders its child into the target, not the main tree"):
    val main   = host()
    val target = host()
    render(div(cls := "main", portal(target, span(cls := "p", "hi"))), main)
    Scheduler.flushSync()
    assert(main.querySelector("span.p") == null)
    assert(target.querySelector("span.p").textContent == "hi")
    // The slot in the main tree is held by a comment anchor, not the child.
    val mainDiv = main.querySelector("div.main")
    assert(mainDiv.childNodes.length == 1)
    assert(mainDiv.childNodes(0).nodeType == dom.Node.COMMENT_NODE)

  test("a portal child updates in place across a re-render"):
    val main   = host()
    val target = host()
    val App = view {
      val (n, _, update) = useState(0)
      div(
        button(onClick := (_ => update(_ + 1)), "inc"),
        portal(target, span(cls := "p", n)),
      )
    }
    render(App(), main)
    Scheduler.flushSync()
    assert(target.querySelector("span.p").textContent == "0")
    fireClick(main.querySelector("button"))
    assert(target.querySelector("span.p").textContent == "1")
    // Still exactly one child in the target — updated, not duplicated.
    assert(target.querySelectorAll("span.p").length == 1)

  test("unmounting a portal removes its child from the target"):
    val main   = host()
    val target = host()
    val App = view {
      val (show, setShow, _) = useState(true)
      div(
        button(onClick := (_ => setShow(false)), "hide"),
        when(show)(portal(target, span(cls := "p", "x"))),
      )
    }
    render(App(), main)
    Scheduler.flushSync()
    assert(target.querySelector("span.p") != null)
    fireClick(main.querySelector("button"))
    assert(target.querySelector("span.p") == null)
