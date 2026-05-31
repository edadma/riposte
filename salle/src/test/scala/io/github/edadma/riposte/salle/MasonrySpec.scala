package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Masonry packs tiles of differing heights into balanced columns. The packing math is
// the pure `layoutMasonry`, tested here directly with synthetic heights (no browser
// needed). The component itself can't be measured in jsdom (offsetWidth/Height are 0),
// so its tests cover the DOM contract — `data-*` mirror, item wrappers, graceful
// normal-flow fallback when unmeasurable; a Playwright suite drives the real layout.
class MasonrySpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  // -- layoutMasonry (pure) -------------------------------------------------

  test("layoutMasonry packs into the shortest column, left-to-right then balancing"):
    // heights [100,180,140,200,160,130], 3 cols, gap 16, width 632 → columnWidth 200,
    // column stride 216. Row 1 fills cols 0,1,2; then each tile lands atop the shortest.
    val l = layoutMasonry(Seq(100, 180, 140, 200, 160, 130), columns = 3, gap = 16, containerWidth = 632)
    assert(l.columnWidth == 200.0)
    val p = l.positions
    assert(p.length == 6)
    assert(p(0) == (left = 0.0, top = 0.0, col = 0))
    assert(p(1) == (left = 216.0, top = 0.0, col = 1))
    assert(p(2) == (left = 432.0, top = 0.0, col = 2))
    assert(p(3) == (left = 0.0, top = 116.0, col = 0))   // col 0 was shortest (100)
    assert(p(4) == (left = 432.0, top = 156.0, col = 2)) // col 2 next (140)
    assert(p(5) == (left = 216.0, top = 196.0, col = 1)) // col 1 next (180)
    assert(l.containerHeight == 326.0)                   // tallest column minus trailing gap

  test("layoutMasonry returns an empty layout for no tiles"):
    val l = layoutMasonry(Seq.empty, columns = 3, gap = 16, containerWidth = 600)
    assert(l.positions.isEmpty)
    assert(l.containerHeight == 0.0)

  test("layoutMasonry coerces a column count below 1 to a single column"):
    val l = layoutMasonry(Seq(50, 60), columns = 0, gap = 10, containerWidth = 300)
    assert(l.columnWidth == 300.0)
    assert(l.positions(0) == (left = 0.0, top = 0.0, col = 0))
    assert(l.positions(1) == (left = 0.0, top = 60.0, col = 0)) // stacked in one column
    assert(l.containerHeight == 120.0) // 50 + 10 gap + 60

  test("layoutMasonry breaks column-height ties toward the lowest index"):
    // All equal heights, 2 columns: tiles alternate 0,1,0,1 — ties always pick col 0.
    val l = layoutMasonry(Seq(100, 100, 100, 100), columns = 2, gap = 0, containerWidth = 200)
    assert(l.positions.map(_.col) == Seq(0, 1, 0, 1))
    assert(l.containerHeight == 200.0)

  // -- MasonryColumns.resolve ----------------------------------------------

  test("MasonryColumns.Fixed always resolves to its count (min 1)"):
    assert(MasonryColumns.Fixed(4).resolve(320) == 4)
    assert(MasonryColumns.Fixed(0).resolve(9999) == 1)

  test("MasonryColumns.Responsive picks by viewport width and fills forward"):
    val c = MasonryColumns.Responsive(xs = 1, sm = 2, md = 3, lg = 4, xl = -1, xxl = -1)
    assert(c.resolve(320) == 1)   // below sm
    assert(c.resolve(700) == 2)   // sm
    assert(c.resolve(800) == 3)   // md
    assert(c.resolve(1100) == 4)  // lg
    assert(c.resolve(1400) == 4)  // xl unset → inherits lg
    assert(c.resolve(2000) == 4)  // xxl unset → inherits lg

  // -- component DOM contract ----------------------------------------------

  test("renders a container with one data-part=item wrapper per child, indexed in order"):
    val c = host()
    render(Masonry(columns = 3)(div("a"), div("b"), div("c")), c)
    Scheduler.flushSync()
    val items = c.querySelectorAll("[data-part=item]")
    assert(items.length == 3)
    assert(items(0).asInstanceOf[dom.Element].getAttribute("data-index") == "0")
    assert(items(2).asInstanceOf[dom.Element].getAttribute("data-index") == "2")
    // children live inside their wrappers
    assert(items(0).asInstanceOf[dom.Element].textContent.contains("a"))

  test("mirrors the resolved column count to data-columns on the container"):
    val c = host()
    render(Masonry(columns = 5)(div("a")), c)
    Scheduler.flushSync()
    val container = c.querySelector(".salle-masonry").asInstanceOf[dom.Element]
    assert(container.getAttribute("data-columns") == "5")

  test("falls back to normal flow when the container is unmeasurable (jsdom)"):
    // jsdom offsetWidth is 0, so the engine can't pack; tiles stay relative + visible
    // rather than absolutely positioned at the same spot.
    val c = host()
    render(Masonry(columns = 3)(div("a"), div("b")), c)
    Scheduler.flushSync()
    val item = c.querySelector("[data-part=item]").asInstanceOf[dom.html.Element]
    assert(item.style.getPropertyValue("position") == "relative")
    assert(item.style.getPropertyValue("visibility") == "visible")

  test("a custom className is appended to the container's salle class"):
    val c = host()
    render(Masonry(className = "gallery")(div("a")), c)
    Scheduler.flushSync()
    val container = c.querySelector(".salle-masonry").asInstanceOf[dom.Element]
    assert(container.classList.contains("salle-masonry"))
    assert(container.classList.contains("gallery"))
