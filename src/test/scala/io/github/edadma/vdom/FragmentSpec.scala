package io.github.edadma.vdom

// Fragments: splicing a group of siblings into the parent with no wrapper
// element, updating them in place, and letting a component render a fragment.
class FragmentSpec extends DomSuite:

  test("splices children into the parent without a wrapper"):
    val c = container()
    render(div(cls := "row", fragment(span("a"), span("b")), span("c")), c)
    val spans = c.querySelectorAll("span")
    assert(spans.length == 3)
    assert(spans(0).textContent == "a")
    assert(spans(1).textContent == "b")
    assert(spans(2).textContent == "c")
    // The spans are direct children of div.row — no intermediate wrapper.
    assert(c.querySelectorAll("div.row > span").length == 3)

  test("fragment content updates in place"):
    val c = container()
    val root = createRoot(c)
    root.render(div(fragment(span(cls := "x", "one"))))
    val node = c.querySelector("span.x")
    root.render(div(fragment(span(cls := "x", "two"))))
    assert(c.querySelector("span.x").textContent == "two")
    assert(c.querySelector("span.x") eq node)

  test("a component may render a fragment"):
    val c = container()
    val Two = view("Two") {
      fragment(span(cls := "a", "A"), span(cls := "b", "B"))
    }
    render(div(Two(), span(cls := "c", "C")), c)
    val spans = c.querySelectorAll("span")
    assert(spans.length == 3)
    assert(c.querySelector("span.a").textContent == "A")
    assert(c.querySelector("span.b").textContent == "B")
    assert(c.querySelector("span.c").textContent == "C")
