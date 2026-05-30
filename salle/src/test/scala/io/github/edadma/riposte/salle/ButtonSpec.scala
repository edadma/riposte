package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// End-to-end coverage that proves the salle module is wired to riposte's core and
// renders into a real DOM (jsdom). Assertions are on the default SalleSkin's classes;
// the skin seam itself is covered in SkinSpec.
class ButtonSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def renderInto(node: VNode): dom.Element =
    val c = host()
    render(node, c)
    Scheduler.flushSync()
    c.querySelector("button")

  private def click(el: dom.Element): Unit =
    val init = new dom.MouseEventInit {}
    init.bubbles = true
    init.cancelable = true
    el.dispatchEvent(new dom.MouseEvent("click", init))

  test("renders the label with base and size classes, no colour/variant by default"):
    val btn = renderInto(Button("Save"))
    assert(btn.textContent == "Save")
    assert(btn.classList.contains("salle-btn"))
    assert(btn.classList.contains("salle-btn--md"))
    assert(!btn.classList.contains("salle-btn--primary"))

  test("colour, variant, and size are independent axes"):
    val btn = renderInto(Button("Go", color = Color.Primary, variant = ButtonVariant.Outline, size = Size.Lg))
    assert(btn.classList.contains("salle-btn--primary"))
    assert(btn.classList.contains("salle-btn--outline"))
    assert(btn.classList.contains("salle-btn--lg"))

  test("disabled reflects the attribute and data-state"):
    val btn = renderInto(Button("Off", disabled = true))
    assert(btn.hasAttribute("disabled"))
    assert(btn.getAttribute("data-state") == "disabled")

  test("an enabled button reports data-state default"):
    assert(renderInto(Button("On")).getAttribute("data-state") == "default")

  test("click invokes the handler"):
    val c = host()
    var clicks = 0
    render(Button("Go", onClick = () => clicks += 1), c)
    Scheduler.flushSync()
    click(c.querySelector("button"))
    assert(clicks == 1)
