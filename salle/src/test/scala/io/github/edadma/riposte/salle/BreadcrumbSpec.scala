package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Breadcrumb is a pure, data-driven display component (no timers/keyboard). These specs pin its
// structure: a nav[aria-label] landmark wrapping an ordered list of crumbs, links for navigable
// crumbs, the last crumb as the aria-current page, custom separators rendered as their own items,
// and the data-* mirror.
class BreadcrumbSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def nav(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=breadcrumb]").asInstanceOf[dom.html.Element]
  private def crumbs(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=crumb]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def seps(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=separator]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])

  private val trail: Seq[BreadcrumbItem] = Seq(
    Crumb(label = "Home", href = Some("/")),
    Crumb(label = "Nature", href = Some("/nature")),
    Crumb(label = "Forests"),
  )

  test("renders a nav landmark wrapping a list of crumbs"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail))
    Scheduler.flushSync()
    assert(nav(c).tagName.toLowerCase == "nav")
    assert(nav(c).getAttribute("aria-label") == "Breadcrumb")
    assert(crumbs(c).length == 3)
    assert(crumbs(c).map(_.textContent) == Seq("Home", "Nature", "Forests"))
    r.unmount()

  test("navigable crumbs render links with their href; the last is plain text"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail))
    Scheduler.flushSync()
    val cs = crumbs(c)
    assert(cs(0).querySelector("a").getAttribute("href") == "/")
    assert(cs(1).querySelector("a").getAttribute("href") == "/nature")
    assert(cs(2).querySelector("a") == null)
    r.unmount()

  test("the last crumb is the current page"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail))
    Scheduler.flushSync()
    val cs = crumbs(c)
    assert(cs(2).getAttribute("aria-current") == "page")
    assert(cs(2).getAttribute("data-current") == "true")
    assert(cs(0).getAttribute("aria-current") == null)
    assert(cs(0).getAttribute("data-current") == "false")
    r.unmount()

  test("an onClick-only crumb gets a focusable link that does not navigate, and fires on click"):
    val c = host()
    val r = createRoot(c)
    var hits = 0
    r.render(
      Breadcrumb(items =
        Seq(
          Crumb(label = "Home", onClick = Some(() => hits += 1)),
          Crumb(label = "Here"),
        ),
      ),
    )
    Scheduler.flushSync()
    val link = crumbs(c)(0).querySelector("a").asInstanceOf[dom.html.Element]
    assert(link.getAttribute("href") == "#")
    link.click()
    Scheduler.flushSync()
    assert(hits == 1)
    r.unmount()

  test("no custom separator renders no explicit separator items (the skin draws them)"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail))
    Scheduler.flushSync()
    assert(seps(c).isEmpty)
    assert(nav(c).getAttribute("data-custom-sep") == "false")
    r.unmount()

  test("a custom separator is rendered between crumbs as aria-hidden items"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail, separator = Some("›": VNode)))
    Scheduler.flushSync()
    val s = seps(c)
    assert(s.length == 2) // one between each of the three crumbs
    assert(s.forall(_.getAttribute("aria-hidden") == "true"))
    assert(s.forall(_.textContent == "›"))
    assert(nav(c).getAttribute("data-custom-sep") == "true")
    r.unmount()

  test("an icon renders before the crumb label"):
    val c = host()
    val r = createRoot(c)
    r.render(
      Breadcrumb(items =
        Seq(
          Crumb(label = "Home", href = Some("/"), icon = Some(span(id := "home-icon", "H"))),
          Crumb(label = "Here"),
        ),
      ),
    )
    Scheduler.flushSync()
    assert(crumbs(c)(0).querySelector("#home-icon") != null)
    r.unmount()

  test("SalleSkin applies its breadcrumb part classes"):
    val c = host()
    val r = createRoot(c)
    r.render(Breadcrumb(items = trail))
    Scheduler.flushSync()
    assert(nav(c).getAttribute("class").contains("salle-breadcrumb"))
    assert(crumbs(c)(0).getAttribute("class").contains("salle-breadcrumb__item"))
    assert(crumbs(c)(0).querySelector("a").getAttribute("class").contains("salle-breadcrumb__link"))
    r.unmount()

  test("DaisySkin emits the breadcrumbs vocabulary and suppresses its chevron for custom separators"):
    val c = host()
    val r = createRoot(c)
    r.render(SkinProvider(DaisySkin)(Breadcrumb(items = trail, separator = Some("›": VNode))))
    Scheduler.flushSync()
    assert(nav(c).getAttribute("class").contains("breadcrumbs"))
    assert(nav(c).getAttribute("class").contains("[&_li::before]:hidden"))
    assert(seps(c).head.getAttribute("class").contains("text-base-content/50"))
    r.unmount()
