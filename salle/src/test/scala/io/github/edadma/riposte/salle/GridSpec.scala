package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Grid is Row + Col: a 24/30-column system whose geometry is inline CSS custom
// properties + a stylesheet (so the responsive spans can live in @media rules). jsdom
// doesn't compute grid layout, so these tests assert the *contract* — the inline custom
// props and styles each Col/Row emits, and the data-* mirror. A Playwright suite drives
// the real reflow across breakpoints.
class GridSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def row(c: dom.Element): dom.html.Element  = c.querySelector(".salle-row").asInstanceOf[dom.html.Element]
  private def cols(c: dom.Element): dom.NodeList[dom.Node] = c.querySelectorAll(".salle-col")
  private def col0(c: dom.Element): dom.html.Element = c.querySelector(".salle-col").asInstanceOf[dom.html.Element]
  private def sty(el: dom.html.Element, p: String): String = el.style.getPropertyValue(p)

  test("a Row is a CSS grid of `cols` equal tracks, mirrored to data-cols"):
    val c = host()
    render(Row()(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    val r = row(c)
    assert(r.getAttribute("data-cols") == "24")
    assert(sty(r, "display") == "grid")
    assert(sty(r, "grid-template-columns") == "repeat(24, minmax(0, 1fr))")
    assert(sty(r, "width") == "100%")

  test("cols=30 changes the track count"):
    val c = host()
    render(Row(cols = 30)(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    assert(row(c).getAttribute("data-cols") == "30")
    assert(sty(row(c), "grid-template-columns") == "repeat(30, minmax(0, 1fr))")

  test("the horizontal gutter is negative row margin plus per-col padding"):
    val c = host()
    render(Row(gutterX = 24)(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(row(c), "margin-left") == "-12px")
    assert(sty(row(c), "margin-right") == "-12px")
    assert(sty(col0(c), "padding-left") == "12px")
    assert(sty(col0(c), "padding-right") == "12px")

  test("the vertical gutter is row-gap; no gutter means no margin/padding"):
    val c = host()
    render(Row(gutterX = 0, gutterY = 16)(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(row(c), "row-gap") == "16px")
    assert(sty(row(c), "margin-left") == "")
    assert(sty(col0(c), "padding-left") == "")

  test("justify maps to justify-content and align to align-items"):
    val c = host()
    render(Row(justify = "between", align = "center")(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(row(c), "justify-content") == "space-between")
    assert(sty(row(c), "align-items") == "center")

  test("a Col emits its span as --sc-base and mirrors data-span"):
    val c = host()
    render(Row()(Col(span = 8)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(col0(c), "--sc-base") == "8")
    assert(col0(c).getAttribute("data-span") == "8")

  test("responsive spans fill forward across the breakpoint custom properties"):
    // xs=24, sm=12, md=8 set; lg/xl/xxl inherit md.
    val c = host()
    render(Row()(Col(xs = 24, sm = 12, md = 8)(div("a"))), c)
    Scheduler.flushSync()
    val col = col0(c)
    assert(sty(col, "--sc-base") == "24") // base = xs when span unset
    assert(sty(col, "--sc-sm") == "12")
    assert(sty(col, "--sc-md") == "8")
    assert(sty(col, "--sc-lg") == "8")  // inherits md
    assert(sty(col, "--sc-xl") == "8")
    assert(sty(col, "--sc-xxl") == "8")

  test("span defaults to the full row width when nothing is specified"):
    val c = host()
    render(Row(cols = 30)(Col()(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(col0(c), "--sc-base") == "30")

  test("offset becomes --sc-start (offset+1) and order is emitted"):
    val c = host()
    render(Row()(Col(span = 6, offset = 6, order = 2)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(col0(c), "--sc-start") == "7")
    assert(sty(col0(c), "order") == "2")

  test("no offset means no --sc-start; no order means no order"):
    val c = host()
    render(Row()(Col(span = 6)(div("a"))), c)
    Scheduler.flushSync()
    assert(sty(col0(c), "--sc-start") == "")
    assert(sty(col0(c), "order") == "")

  test("a Row renders all its Col children"):
    val c = host()
    render(Row()(Col(span = 6)(div("a")), Col(span = 6)(div("b")), Col(span = 6)(div("c"))), c)
    Scheduler.flushSync()
    assert(cols(c).length == 3)

  test("custom classNames are appended to the salle base classes"):
    val c = host()
    render(Row(className = "hero")(Col(span = 6, className = "cell")(div("a"))), c)
    Scheduler.flushSync()
    assert(row(c).classList.contains("salle-row"))
    assert(row(c).classList.contains("hero"))
    assert(col0(c).classList.contains("salle-col"))
    assert(col0(c).classList.contains("cell"))
