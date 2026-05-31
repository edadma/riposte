package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Skeleton, SkeletonText and SkeletonImage are decorative loading placeholders. These specs
// pin the observable contract a Playwright suite will later drive: the skin class + the
// data-* state mirror, the inline geometry (size, circle, aspect-ratio), the composite
// structure (a line per requested line, the last one shortened; the centred image glyph),
// the animated toggle, aria-hidden on every root, and DaisySkin reskinning.
class SkeletonSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def one(c: dom.Element, sel: String): dom.html.Element =
    c.querySelector(sel).asInstanceOf[dom.html.Element]

  test("Skeleton renders a block with the skin class and the data-* mirror"):
    val c = host()
    render(Skeleton(width = "120px", height = "1.5rem"), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=skeleton]")
    assert(el != null)
    assert(el.className.contains("salle-skeleton"))
    assert(el.getAttribute("data-shape") == "rect")
    assert(el.getAttribute("data-animated") == "true")
    assert(el.getAttribute("aria-hidden") == "true")
    // geometry is inline so a block can be any size
    assert(el.style.width == "120px")
    assert(el.style.height == "1.5rem")

  test("circle = true rounds the block and marks the shape"):
    val c = host()
    render(Skeleton(width = "40px", height = "40px", circle = true), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=skeleton]")
    assert(el.getAttribute("data-shape") == "circle")
    assert(el.style.getPropertyValue("border-radius") == "50%")

  test("rounded = false squares the corners"):
    val c = host()
    render(Skeleton(rounded = false), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=skeleton]").style.getPropertyValue("border-radius") == "0")

  test("animated = false uses the skin's static class and mirrors the flag"):
    val c = host()
    render(Skeleton(animated = false), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=skeleton]")
    assert(el.className.contains("salle-skeleton--static"))
    assert(el.getAttribute("data-animated") == "false")

  test("SkeletonText renders one line per requested line, the last one shortened"):
    val c = host()
    render(SkeletonText(lines = 4, lastWidth = "50%"), c)
    Scheduler.flushSync()
    val root = one(c, "[data-part=skeleton-text]")
    assert(root.getAttribute("aria-hidden") == "true")
    val lines = c.querySelectorAll("[data-part=line]")
    assert(lines.length == 4)
    // each line carries its index and the skin class
    assert(lines(0).asInstanceOf[dom.html.Element].getAttribute("data-index") == "0")
    assert(lines(0).asInstanceOf[dom.html.Element].className.contains("salle-skeleton"))
    // full-width lines except the last, which is shortened
    assert(lines(0).asInstanceOf[dom.html.Element].style.width == "100%")
    assert(lines(3).asInstanceOf[dom.html.Element].style.width == "50%")

  test("SkeletonText with a single line is full width (no shortening)"):
    val c = host()
    render(SkeletonText(lines = 1), c)
    Scheduler.flushSync()
    val lines = c.querySelectorAll("[data-part=line]")
    assert(lines.length == 1)
    assert(lines(0).asInstanceOf[dom.html.Element].style.width == "100%")

  test("SkeletonText clamps a non-positive line count to one"):
    val c = host()
    render(SkeletonText(lines = 0), c)
    Scheduler.flushSync()
    assert(c.querySelectorAll("[data-part=line]").length == 1)

  test("SkeletonImage reserves the aspect ratio and centres a glyph"):
    val c = host()
    render(SkeletonImage(ratio = "16/10"), c)
    Scheduler.flushSync()
    val box = one(c, "[data-part=skeleton-image]")
    assert(box != null)
    assert(box.className.contains("salle-skeleton"))
    assert(box.getAttribute("aria-hidden") == "true")
    assert(box.style.getPropertyValue("aspect-ratio") == "16/10")
    val icon = one(c, "[data-part=icon]")
    assert(icon != null)
    assert(icon.querySelector("svg") != null) // the picture glyph is injected as SVG

  test("DaisySkin reskins the block via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Skeleton()), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=skeleton]")
    assert(el.className.contains("skeleton"))
    assert(!el.className.contains("salle-skeleton"))

  test("DaisySkin's non-animated block falls back to a muted fill"):
    val c = host()
    render(SkinProvider(DaisySkin)(Skeleton(animated = false)), c)
    Scheduler.flushSync()
    val el = one(c, "[data-part=skeleton]")
    assert(el.className.contains("bg-base-300"))
    assert(!el.className.contains("skeleton"))
