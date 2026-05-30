package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Toggle is a checkbox-shaped control with switch semantics; this pins the role and
// aria-checked wiring plus the boolean change round-trip.
class ToggleSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def setChecked(el: dom.html.Input, v: Boolean): Unit =
    el.checked = v
    el.dispatchEvent(new dom.Event("change"))
    Scheduler.flushSync()

  test("renders a switch with role and base+size classes"):
    val c = host()
    render(Toggle(size = Size.Lg), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(inp.getAttribute("type") == "checkbox")
    assert(inp.getAttribute("role") == "switch")
    assert(inp.classList.contains("salle-toggle"))
    assert(inp.classList.contains("salle-toggle--lg"))

  test("aria-checked reflects state and updates on toggle"):
    val c = host()
    render(Toggle(defaultChecked = false), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input").asInstanceOf[dom.html.Input]
    assert(inp.getAttribute("aria-checked") == "false")
    setChecked(inp, true)
    assert(inp.getAttribute("aria-checked") == "true")

  test("uncontrolled: toggling fires onChange"):
    val c    = host()
    var last = false
    render(Toggle(onChange = b => last = b), c)
    Scheduler.flushSync()
    setChecked(c.querySelector("input").asInstanceOf[dom.html.Input], true)
    assert(last)

  test("DaisySkin maps the toggle axes"):
    val c = host()
    render(SkinProvider(DaisySkin)(Toggle(color = Color.Accent, size = Size.Sm)), c)
    Scheduler.flushSync()
    val cl = c.querySelector("input").classList
    assert(cl.contains("toggle"))
    assert(cl.contains("toggle-accent"))
    assert(cl.contains("toggle-sm"))
