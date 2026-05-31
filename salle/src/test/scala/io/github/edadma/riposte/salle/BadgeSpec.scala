package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Badge, Tag and CheckableTag are small label pills. These specs pin the observable
// contract a Playwright suite will later drive: the skin class + the data-* mirror, the
// colour/variant/size modifier classes, pill/dot geometry, Tag's icon slot + click +
// closable (with stopPropagation so close doesn't trigger the tag's onClick), and
// CheckableTag's toggle + keyboard + aria-pressed. Plus DaisySkin reskinning.
class BadgeSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def one(c: dom.Element, sel: String): dom.html.Element =
    c.querySelector(sel).asInstanceOf[dom.html.Element]

  private def keydown(el: dom.Element, k: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  // -- Badge ----------------------------------------------------------------

  test("Badge renders its children with the base class and data-part"):
    val c = host()
    render(Badge()("4K"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=badge]")
    assert(el != null)
    assert(el.className.contains("salle-badge"))
    assert(el.textContent == "4K")
    assert(el.getAttribute("data-dot") == "false")

  test("Badge colour, variant, and size are independent modifier classes"):
    val c = host()
    render(Badge(color = Color.Success, variant = BadgeVariant.Outline, size = Size.Sm)("New"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=badge]")
    assert(el.className.contains("salle-badge--success"))
    assert(el.className.contains("salle-badge--outline"))
    assert(el.className.contains("salle-badge--sm"))

  test("Solid variant and Default colour emit no modifier (base look only)"):
    val c = host()
    render(Badge()("x"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=badge]")
    assert(!el.className.contains("salle-badge--solid"))
    // no colour token for Default
    assert(!el.className.contains("salle-badge--default"))

  test("pill rounds to a full capsule"):
    val c = host()
    render(Badge(pill = true)("x"), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=badge]").style.getPropertyValue("border-radius") == "999px")

  test("dot collapses to a tiny circle with no text"):
    val c = host()
    render(Badge(dot = true)("ignored"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=badge]")
    assert(el.getAttribute("data-dot") == "true")
    assert(el.textContent == "") // dot shows no children
    assert(el.style.getPropertyValue("border-radius") == "50%")

  // -- Tag ------------------------------------------------------------------

  test("Tag renders as a tag with its label and no close button by default"):
    val c = host()
    render(Tag()("Nature"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=tag]")
    assert(el != null)
    assert(el.className.contains("salle-tag"))
    assert(el.textContent.contains("Nature"))
    assert(el.getAttribute("data-closable") == "false")
    assert(c.querySelector("[data-part=close]") == null)

  test("Tag onClick fires when the tag is clicked"):
    var clicked = false
    val c       = host()
    render(Tag(onClick = () => clicked = true)("clickable"), c)
    Scheduler.flushSync()
    one(c, "[data-part=tag]").click()
    assert(clicked)

  test("closable: the close button fires onClose and does not bubble to onClick"):
    var closed  = false
    var clicked = false
    val c       = host()
    render(Tag(closable = true, onClose = () => closed = true, onClick = () => clicked = true)("x"), c)
    Scheduler.flushSync()
    val close = one(c, "[data-part=close]")
    assert(close != null)
    close.click()
    Scheduler.flushSync()
    assert(closed)
    assert(!clicked) // stopPropagation kept the close click off the tag's onClick

  test("the icon slot renders before the label"):
    val c = host()
    render(Tag(icon = Some(span(cls := "the-icon", "*")))("Tagged"), c)
    Scheduler.flushSync()
    val icon = one(c, "[data-part=icon]")
    assert(icon != null)
    assert(icon.querySelector(".the-icon") != null)

  // -- CheckableTag ---------------------------------------------------------

  test("CheckableTag is a button with aria-pressed and a data-checked mirror"):
    val c = host()
    render(CheckableTag(checked = true)("Abstract"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=checkable-tag]")
    assert(el.getAttribute("role") == "button")
    assert(el.getAttribute("tabindex") == "0")
    assert(el.getAttribute("aria-pressed") == "true")
    assert(el.getAttribute("data-checked") == "true")
    // checked reads as a filled primary pill
    assert(el.className.contains("salle-badge--primary"))

  test("CheckableTag unchecked uses the quiet neutral-soft look"):
    val c = host()
    render(CheckableTag(checked = false)("x"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=checkable-tag]")
    assert(el.getAttribute("aria-pressed") == "false")
    assert(el.className.contains("salle-badge--neutral"))
    assert(el.className.contains("salle-badge--soft"))

  test("CheckableTag click requests the flipped value via onChange"):
    var got = false
    val c   = host()
    render(CheckableTag(checked = false, onChange = b => got = b)("x"), c)
    Scheduler.flushSync()
    one(c, "[data-part=checkable-tag]").click()
    assert(got) // false -> requested true

  test("CheckableTag toggles on Enter and Space"):
    var count = 0
    val c     = host()
    render(CheckableTag(checked = false, onChange = _ => count += 1)("x"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=checkable-tag]")
    keydown(el, "Enter")
    keydown(el, " ")
    assert(count == 2)

  // -- DaisySkin ------------------------------------------------------------

  test("DaisySkin reskins the Badge via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Badge(color = Color.Primary)("x")), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=badge]")
    assert(el.className.contains("badge"))
    assert(el.className.contains("badge-primary"))
    assert(!el.className.contains("salle-badge"))

  test("DaisySkin reskins the Tag's root and close button"):
    val c = host()
    render(SkinProvider(DaisySkin)(Tag(closable = true)("x")), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=tag]").className.contains("badge"))
    assert(one(c, "[data-part=close]").className.contains("btn-circle"))
