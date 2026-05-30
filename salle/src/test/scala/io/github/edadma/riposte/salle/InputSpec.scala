package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Input exercises the form-control surface: the three skin axes, validity wiring,
// and the controlled/uncontrolled value path through useControllable.
class InputSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def typeInto(el: dom.html.Input, text: String): Unit =
    el.value = text
    el.dispatchEvent(new dom.Event("input"))
    Scheduler.flushSync()

  test("renders an input with placeholder, type, and base+size classes"):
    val c = host()
    render(Input(placeholder = "Email", inputType = "email"), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(inp.getAttribute("placeholder") == "Email")
    assert(inp.getAttribute("type") == "email")
    assert(inp.classList.contains("salle-input"))
    assert(inp.classList.contains("salle-input--md"))

  test("colour and size map to modifier classes"):
    val c = host()
    render(Input(color = Color.Success, size = Size.Lg), c)
    Scheduler.flushSync()
    val cl = c.querySelector("input").classList
    assert(cl.contains("salle-input--success"))
    assert(cl.contains("salle-input--lg"))

  test("invalid sets the error class, aria-invalid, and data-state, overriding colour"):
    val c = host()
    render(Input(invalid = true, color = Color.Primary), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input")
    assert(inp.classList.contains("salle-input--error"))
    assert(!inp.classList.contains("salle-input--primary"))
    assert(inp.getAttribute("aria-invalid") == "true")
    assert(inp.getAttribute("data-state") == "invalid")

  test("uncontrolled: typing updates the value and fires onChange"):
    val c    = host()
    var last = ""
    render(Input(defaultValue = "", onChange = s => last = s), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    typeInto(inp, "hello")
    assert(last == "hello")
    assert(inp.value == "hello")

  test("DaisySkin maps the input axes"):
    val c = host()
    render(SkinProvider(DaisySkin)(Input(color = Color.Primary, size = Size.Sm)), c)
    Scheduler.flushSync()
    val cl = c.querySelector("input").classList
    assert(cl.contains("input"))
    assert(cl.contains("input-primary"))
    assert(cl.contains("input-sm"))
    assert(!cl.contains("salle-input"))
