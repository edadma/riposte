package io.github.edadma.riposte

import org.scalajs.dom

// Initial mount: turning a VNode tree into DOM — text, nesting, attributes, and
// the few props that must be set as live properties rather than attributes.
class RenderSpec extends DomSuite:

  test("renders text and nested elements"):
    val c = host()
    render(div(cls := "box", h1("hi"), p("there")), c)
    assert(c.querySelector("h1").textContent == "hi")
    assert(c.querySelector("p").textContent == "there")
    assert(c.querySelector("div.box") != null)

  test("applies attributes and the controlled value property"):
    val c = host()
    render(input(typ := "text", placeholder := "name", value := "bob"), c)
    val in = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(in.getAttribute("type") == "text")
    assert(in.getAttribute("placeholder") == "name")
    assert(in.value == "bob")

  test("repeated class mods are space-joined"):
    val c = host()
    render(div(cls := "a", cls := "b"), c)
    assert(c.querySelector("div").getAttribute("class") == "a b")

  test("inline styles are written onto element.style"):
    val c = host()
    render(div(css("color" -> "red", "font-weight" -> "bold")), c)
    val el = c.querySelector("div").asInstanceOf[dom.html.Element]
    assert(el.style.getPropertyValue("color") == "red")
    assert(el.style.getPropertyValue("font-weight") == "bold")
