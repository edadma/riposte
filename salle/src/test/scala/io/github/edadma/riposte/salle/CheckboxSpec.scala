package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Checkbox confirms useControllable generalizes from text to a boolean control, and
// that the boolean change path (a native `change` event) round-trips through state.
class CheckboxSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def setChecked(el: dom.html.Input, v: Boolean): Unit =
    el.checked = v
    el.dispatchEvent(new dom.Event("change"))
    Scheduler.flushSync()

  test("renders a checkbox with base and size classes"):
    val c = host()
    render(Checkbox(CheckboxProps(size = Size.Lg)), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(inp.getAttribute("type") == "checkbox")
    assert(inp.classList.contains("salle-checkbox"))
    assert(inp.classList.contains("salle-checkbox--lg"))

  test("colour maps to a modifier class"):
    val c = host()
    render(Checkbox(CheckboxProps(color = Color.Success)), c)
    Scheduler.flushSync()
    assert(c.querySelector("input").classList.contains("salle-checkbox--success"))

  test("uncontrolled: toggling updates state and fires onChange"):
    val c    = host()
    var last = false
    render(Checkbox(CheckboxProps(onChange = b => last = b)), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(!inp.checked)
    setChecked(inp, true)
    assert(last)
    assert(inp.checked)

  test("a label wraps the box so the text is clickable"):
    val c = host()
    render(Checkbox(CheckboxProps(label = "Accept")), c)
    Scheduler.flushSync()
    val lbl = c.querySelector("label.salle-check-label")
    assert(lbl != null)
    assert(lbl.textContent.contains("Accept"))
    assert(lbl.querySelector("input.salle-checkbox") != null)

  test("DaisySkin maps the checkbox axes"):
    val c = host()
    render(SkinProvider(DaisySkin)(Checkbox(CheckboxProps(color = Color.Primary, size = Size.Sm))), c)
    Scheduler.flushSync()
    val cl = c.querySelector("input").classList
    assert(cl.contains("checkbox"))
    assert(cl.contains("checkbox-primary"))
    assert(cl.contains("checkbox-sm"))
