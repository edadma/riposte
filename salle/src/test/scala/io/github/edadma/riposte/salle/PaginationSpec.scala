package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Pagination is a controlled page navigator over a pure range builder. These specs pin both:
// the dots algorithm (paginationRange / pageCount) directly, and the component contract a
// Playwright suite will later drive — the data-* mirror, prev/next disabling at the ends,
// onChange gating (out-of-range / same / disabled), the active page's aria-current, simple
// mode, and DaisySkin reskinning.
class PaginationSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def nav(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=pagination]").asInstanceOf[dom.html.Element]
  private def pages(c: dom.Element): Seq[Int] =
    val ns = c.querySelectorAll("[data-part=page]")
    (0 until ns.length).map(i => ns(i).asInstanceOf[dom.html.Element].getAttribute("data-page").toInt)

  // -- pure range builder ---------------------------------------------------

  test("pageCount is a ceiling, and zero for empty or non-positive sizes"):
    assert(pageCount(0, 10) == 0)
    assert(pageCount(10, 10) == 1)
    assert(pageCount(11, 10) == 2)
    assert(pageCount(95, 10) == 10)
    assert(pageCount(100, 0) == 0)

  test("paginationRange shows every page when they all fit (no dots)"):
    assert(paginationRange(1, 5) == Vector(1, 2, 3, 4, 5).map(PageItem.Page(_)))
    // window for siblingCount=1 is 7; exactly 7 pages still all fit
    assert(paginationRange(4, 7).forall(_.isInstanceOf[PageItem.Page]))

  test("paginationRange collapses the right side near the start"):
    // current=1 of 10: longer left run, dots, last
    assert(paginationRange(1, 10) == Vector(
      PageItem.Page(1), PageItem.Page(2), PageItem.Page(3), PageItem.Page(4), PageItem.Page(5),
      PageItem.Dots, PageItem.Page(10),
    ))

  test("paginationRange collapses the left side near the end"):
    assert(paginationRange(10, 10) == Vector(
      PageItem.Page(1), PageItem.Dots,
      PageItem.Page(6), PageItem.Page(7), PageItem.Page(8), PageItem.Page(9), PageItem.Page(10),
    ))

  test("paginationRange shows dots on both sides in the middle"):
    assert(paginationRange(5, 10) == Vector(
      PageItem.Page(1), PageItem.Dots,
      PageItem.Page(4), PageItem.Page(5), PageItem.Page(6),
      PageItem.Dots, PageItem.Page(10),
    ))

  test("paginationRange clamps an out-of-range current and handles tiny totals"):
    assert(paginationRange(99, 3) == Vector(1, 2, 3).map(PageItem.Page(_))) // clamp high, all fit
    assert(paginationRange(1, 1) == Vector(PageItem.Page(1)))
    assert(paginationRange(1, 0).isEmpty)

  // -- component contract ---------------------------------------------------

  test("renders a nav with the data-* mirror and a button per shown page"):
    val c = host()
    render(Pagination(current = 1, total = 100, pageSize = 10), c)
    Scheduler.flushSync()
    val n = nav(c)
    assert(n != null)
    assert(n.getAttribute("aria-label") == "Pagination")
    assert(n.getAttribute("data-current") == "1")
    assert(n.getAttribute("data-pages") == "10")
    // start of a 10-page strip: 1 2 3 4 5 … 10
    assert(pages(c) == Seq(1, 2, 3, 4, 5, 10))
    assert(c.querySelector("[data-part=dots]") != null)

  test("the active page carries aria-current and the active modifier"):
    val c = host()
    render(Pagination(current = 3, total = 100, pageSize = 10), c)
    Scheduler.flushSync()
    val active = c.querySelector("[data-active=true]").asInstanceOf[dom.html.Element]
    assert(active.getAttribute("data-page") == "3")
    assert(active.getAttribute("aria-current") == "page")
    assert(active.className.contains("salle-pagination__item--active"))

  test("clicking a page reports it via onChange"):
    var got = -1
    val c   = host()
    render(Pagination(current = 1, total = 100, pageSize = 10, onChange = p => got = p), c)
    Scheduler.flushSync()
    c.querySelector("[data-page='3']").asInstanceOf[dom.html.Element].click()
    assert(got == 3)

  test("clicking the current page does not fire onChange"):
    var fired = false
    val c     = host()
    render(Pagination(current = 2, total = 100, pageSize = 10, onChange = _ => fired = true), c)
    Scheduler.flushSync()
    c.querySelector("[data-page='2']").asInstanceOf[dom.html.Element].click()
    assert(!fired)

  test("prev is disabled on the first page, next on the last"):
    val c1 = host()
    render(Pagination(current = 1, total = 100, pageSize = 10), c1)
    Scheduler.flushSync()
    assert(c1.querySelector("[data-part=prev]").asInstanceOf[dom.html.Button].disabled)
    assert(!c1.querySelector("[data-part=next]").asInstanceOf[dom.html.Button].disabled)

    val c2 = host()
    render(Pagination(current = 10, total = 100, pageSize = 10), c2)
    Scheduler.flushSync()
    assert(!c2.querySelector("[data-part=prev]").asInstanceOf[dom.html.Button].disabled)
    assert(c2.querySelector("[data-part=next]").asInstanceOf[dom.html.Button].disabled)

  test("prev and next move by one and report via onChange"):
    var got = -1
    val c   = host()
    render(Pagination(current = 5, total = 100, pageSize = 10, onChange = p => got = p), c)
    Scheduler.flushSync()
    c.querySelector("[data-part=prev]").asInstanceOf[dom.html.Element].click()
    assert(got == 4)
    c.querySelector("[data-part=next]").asInstanceOf[dom.html.Element].click()
    assert(got == 6)

  test("disabled: no page click fires onChange and the buttons are disabled"):
    var fired = false
    val c     = host()
    render(Pagination(current = 3, total = 100, pageSize = 10, disabled = true, onChange = _ => fired = true), c)
    Scheduler.flushSync()
    c.querySelector("[data-page='5']").asInstanceOf[dom.html.Element].click()
    assert(!fired)
    assert(c.querySelector("[data-part=next]").asInstanceOf[dom.html.Button].disabled)

  test("simple mode shows a compact status and no page buttons"):
    val c = host()
    render(Pagination(current = 2, total = 50, pageSize = 10, simple = true), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=page]") == null)
    val status = c.querySelector("[data-part=status]")
    assert(status != null)
    assert(status.textContent == "2 / 5")

  test("zero pages renders an empty strip"):
    val c = host()
    render(Pagination(current = 1, total = 0, pageSize = 10), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=page]") == null)
    assert(nav(c).getAttribute("data-pages") == "0")

  test("DaisySkin reskins the strip via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Pagination(current = 1, total = 100, pageSize = 10)), c)
    Scheduler.flushSync()
    assert(nav(c).className.contains("join"))
    val page1 = c.querySelector("[data-page='1']").asInstanceOf[dom.html.Element]
    assert(page1.className.contains("join-item"))
    assert(page1.className.contains("btn-active")) // current page
