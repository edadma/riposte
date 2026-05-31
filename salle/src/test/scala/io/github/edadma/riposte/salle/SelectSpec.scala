package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Select is a custom ARIA combobox/listbox. These specs pin the observable contract a
// Playwright suite will later drive in a real browser: opening/closing, selection by
// pointer and keyboard, disabled handling, click-outside dismissal, the controlled vs
// uncontrolled value path, and the `data-*` state mirror that tests select on.
class SelectSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val fruits =
    Seq(Opt("a", "Apple"), Opt("b", "Banana"), Opt("c", "Cherry", disabled = true), Opt("d", "Date"))

  private def trigger(c: dom.Element): dom.html.Element =
    c.querySelector("[role=combobox]").asInstanceOf[dom.html.Element]

  private def keydown(el: dom.Element, k: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("renders a combobox trigger showing the placeholder when nothing is selected"):
    val c = host()
    render(Select(options = fruits, placeholder = "Pick a fruit"), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t != null)
    assert(t.getAttribute("aria-haspopup") == "listbox")
    assert(t.getAttribute("aria-expanded") == "false")
    assert(c.querySelector("[role=listbox]") == null) // closed: no popup in the DOM
    val value = c.querySelector(".salle-select__value")
    assert(value.getAttribute("data-placeholder") == "true")
    assert(value.textContent == "Pick a fruit")

  test("clicking the trigger opens the listbox with an option per item and correct roles"):
    val c = host()
    render(Select(options = fruits), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    val list = c.querySelector("[role=listbox]")
    assert(list != null)
    assert(trigger(c).getAttribute("aria-expanded") == "true")
    assert(c.querySelector(".salle-select").getAttribute("data-state") == "open")
    val options = c.querySelectorAll("[role=option]")
    assert(options.length == 4)
    // the disabled item is marked for assistive tech and for styling/tests
    val cherry = c.querySelector("[data-value=c]")
    assert(cherry.getAttribute("aria-disabled") == "true")
    assert(cherry.getAttribute("data-disabled") == "true")

  test("uncontrolled: selecting an option sets the value, closes, and reports onChange"):
    var captured = ""
    val c        = host()
    render(Select(options = fruits, onChange = s => captured = s), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    c.querySelector("[data-value=b]").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(captured == "b")
    assert(c.querySelector(".salle-select").getAttribute("data-value") == "b")
    assert(c.querySelector(".salle-select__value").textContent == "Banana")
    assert(c.querySelector("[role=listbox]") == null) // closed on select

  test("a disabled option cannot be selected"):
    var captured = ""
    val c        = host()
    render(Select(options = fruits, onChange = s => captured = s), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    c.querySelector("[data-value=c]").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(captured == "")
    assert(c.querySelector("[role=listbox]") != null) // stays open

  test("keyboard: ArrowDown opens and skips disabled options; Enter selects the active one"):
    var captured = ""
    val c        = host()
    render(Select(options = fruits, onChange = s => captured = s), c)
    Scheduler.flushSync()
    val t = trigger(c)
    keydown(t, "ArrowDown") // opens, active = first enabled (Apple)
    assert(c.querySelector("[role=listbox]") != null)
    assert(c.querySelector("[data-active=true]").getAttribute("data-value") == "a")
    keydown(t, "ArrowDown") // -> Banana
    assert(c.querySelector("[data-active=true]").getAttribute("data-value") == "b")
    keydown(t, "ArrowDown") // skips disabled Cherry -> Date
    assert(c.querySelector("[data-active=true]").getAttribute("data-value") == "d")
    keydown(t, "Enter")
    assert(captured == "d")
    assert(c.querySelector("[role=listbox]") == null)

  test("keyboard: aria-activedescendant tracks the active option"):
    val c = host()
    render(Select(options = fruits), c)
    Scheduler.flushSync()
    val t = trigger(c)
    keydown(t, "ArrowDown")
    val active = c.querySelector("[data-active=true]")
    assert(t.getAttribute("aria-activedescendant") == active.getAttribute("id"))

  test("Escape closes an open menu"):
    val c = host()
    render(Select(options = fruits), c)
    Scheduler.flushSync()
    val t = trigger(c)
    t.click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=listbox]") != null)
    keydown(t, "Escape")
    assert(c.querySelector("[role=listbox]") == null)

  test("controlled: stays at the caller's value but still reports the requested change"):
    var captured = ""
    val c        = host()
    render(Select(options = fruits, value = Some("a"), onChange = s => captured = s), c)
    Scheduler.flushSync()
    assert(c.querySelector(".salle-select__value").textContent == "Apple")
    trigger(c).click()
    Scheduler.flushSync()
    c.querySelector("[data-value=d]").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(captured == "d")
    // controlled: display is restored to the caller's value on re-render
    assert(c.querySelector(".salle-select").getAttribute("data-value") == "a")
    assert(c.querySelector(".salle-select__value").textContent == "Apple")

  test("clearable: the clear control resets the value"):
    var captured = "x"
    val c        = host()
    render(Select(options = fruits, defaultValue = "b", clearable = true, onChange = s => captured = s), c)
    Scheduler.flushSync()
    val clear = c.querySelector("[data-part=clear]")
    assert(clear != null)
    clear.asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(captured == "")
    assert(c.querySelector(".salle-select").getAttribute("data-value") == "")
    assert(c.querySelector(".salle-select__value").getAttribute("data-placeholder") == "true")

  test("clearable: no clear control when there is no value"):
    val c = host()
    render(Select(options = fruits, clearable = true), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=clear]") == null)

  test("a press outside the control closes the menu"):
    val c = host()
    render(Select(options = fruits), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=listbox]") != null)
    dom.document.body.dispatchEvent(new dom.Event("pointerdown", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()
    assert(c.querySelector("[role=listbox]") == null)

  test("disabled: does not open, marks state, and is removed from the tab order"):
    val c = host()
    render(Select(options = fruits, disabled = true), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t.getAttribute("aria-disabled") == "true")
    assert(t.getAttribute("tabindex") == "-1")
    assert(c.querySelector(".salle-select").getAttribute("data-state") == "disabled")
    t.click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=listbox]") == null)

  test("invalid sets aria-invalid and the skin's error treatment"):
    val c = host()
    render(Select(options = fruits, invalid = true), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t.getAttribute("aria-invalid") == "true")
    assert(t.className.contains("salle-select__trigger--error"))

  test("name emits a hidden input mirroring the value for form submission"):
    val c = host()
    render(Select(options = fruits, defaultValue = "b", name = "fruit"), c)
    Scheduler.flushSync()
    val hidden = c.querySelector("input[type=hidden]").asInstanceOf[dom.html.Input]
    assert(hidden.getAttribute("name") == "fruit")
    assert(hidden.value == "b")

  test("DaisySkin re-skins the trigger via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Select(options = fruits)), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t.className.contains("input"))
    assert(!t.className.contains("salle-select__trigger"))
