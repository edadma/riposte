package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// End-to-end coverage that proves the salle module is wired to riposte's core and
// renders into a real DOM (jsdom, via the module's Test jsEnv). Assertions are on the
// default SalleSkin's emitted classes; the skin seam itself is covered in SkinSpec.
class ButtonSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def render1(props: ButtonProps): dom.Element =
    val c = host()
    render(Button(props), c)
    Scheduler.flushSync()
    c.querySelector("button")

  private def click(el: dom.Element): Unit =
    val init = new dom.MouseEventInit {}
    init.bubbles = true
    init.cancelable = true
    el.dispatchEvent(new dom.MouseEvent("click", init))

  test("renders the label with base and size classes, no colour/variant by default"):
    val btn = render1(ButtonProps("Save"))
    assert(btn.textContent == "Save")
    assert(btn.classList.contains("salle-btn"))
    assert(btn.classList.contains("salle-btn--md"))
    assert(!btn.classList.contains("salle-btn--primary"))

  test("colour, variant, and size are independent axes"):
    val btn = render1(
      ButtonProps("Go", color = Color.Primary, variant = ButtonVariant.Outline, size = Size.Lg),
    )
    assert(btn.classList.contains("salle-btn--primary"))
    assert(btn.classList.contains("salle-btn--outline"))
    assert(btn.classList.contains("salle-btn--lg"))

  test("disabled reflects the attribute and data-state"):
    val btn = render1(ButtonProps("Off", disabled = true))
    assert(btn.hasAttribute("disabled"))
    assert(btn.getAttribute("data-state") == "disabled")

  test("an enabled button reports data-state default"):
    assert(render1(ButtonProps("On")).getAttribute("data-state") == "default")

  test("click invokes the handler"):
    val c = host()
    var clicks = 0
    render(Button(ButtonProps("Go", onClick = () => clicks += 1)), c)
    Scheduler.flushSync()
    click(c.querySelector("button"))
    assert(clicks == 1)
