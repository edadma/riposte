package io.github.edadma.vdom

import org.scalajs.dom

// Re-rendering an existing tree: updating in place rather than recreating,
// swapping event listeners as handlers change, and toggling the empty
// placeholder against real content.
class PatchSpec extends DomSuite:

  test("patching updates text without recreating the node"):
    val c = host()
    val root = createRoot(c)
    root.render(p("one"))
    val node1 = c.querySelector("p")
    root.render(p("two"))
    val node2 = c.querySelector("p")
    assert(node2.textContent == "two")
    assert(node2 eq node1)

  test("changing tag replaces the node"):
    val c = host()
    val root = createRoot(c)
    root.render(div(cls := "x"))
    val first = c.firstChild
    root.render(span(cls := "x"))
    assert(c.querySelector("div") == null)
    assert(c.querySelector("span") != null)
    assert(!(c.firstChild eq first))

  test("empty renders a placeholder and toggles with content"):
    val c = host()
    val root = createRoot(c)
    def vnode(show: Boolean): VNode =
      div(if show then span(cls := "shown", "yes") else empty)
    root.render(vnode(false))
    assert(c.querySelector("span.shown") == null)
    root.render(vnode(true))
    assert(c.querySelector("span.shown").textContent == "yes")
    root.render(vnode(false))
    assert(c.querySelector("span.shown") == null)

  test("event listener is removed when the handler prop goes away"):
    val c = host()
    val root = createRoot(c)
    var clicks = 0
    root.render(button(onClick := (_ => clicks += 1), "x"))
    val btn = c.querySelector("button")
    btn.dispatchEvent(new dom.Event("click"))
    assert(clicks == 1)
    root.render(button("x")) // no handler this time
    btn.dispatchEvent(new dom.Event("click"))
    assert(clicks == 1)

  test("removed attribute is dropped from the element"):
    val c = host()
    val root = createRoot(c)
    root.render(div(id := "first", title := "t"))
    assert(c.querySelector("div").getAttribute("title") == "t")
    root.render(div(id := "first"))
    assert(c.querySelector("div").getAttribute("title") == null)
