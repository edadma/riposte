package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Hero is a pure, stateless banner (no timers/keyboard). These specs pin the structure: a content
// box wrapping the children, an optional scrim, the backdrop/height inline style, and the data-*
// mirror.
class HeroSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def hero(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=hero]").asInstanceOf[dom.html.Element]
  private def content(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=content]").asInstanceOf[dom.html.Element]

  test("renders a hero wrapping its children in a content box"):
    val c = host()
    val r = createRoot(c)
    r.render(Hero()(span(id := "cta", "Browse")))
    Scheduler.flushSync()
    assert(hero(c) != null)
    assert(content(c).querySelector("#cta") != null)
    assert(hero(c).getAttribute("data-overlay") == "false")
    r.unmount()

  test("no overlay is rendered by default; overlay = true adds the scrim and mirrors data-overlay"):
    val c1 = host()
    val r1 = createRoot(c1)
    r1.render(Hero()(span("x")))
    Scheduler.flushSync()
    assert(c1.querySelector("[data-part=overlay]") == null)
    r1.unmount()

    val c2 = host()
    val r2 = createRoot(c2)
    r2.render(Hero(overlay = true)(span("x")))
    Scheduler.flushSync()
    assert(c2.querySelector("[data-part=overlay]") != null)
    assert(hero(c2).getAttribute("data-overlay") == "true")
    r2.unmount()

  test("bgImage and minHeight ride the root inline style"):
    val c = host()
    val r = createRoot(c)
    r.render(Hero(bgImage = Some("/banner.jpg"), minHeight = Some("60vh"))(span("x")))
    Scheduler.flushSync()
    assert(hero(c).style.backgroundImage.contains("banner.jpg"))
    assert(hero(c).style.getPropertyValue("min-height") == "60vh")
    r.unmount()

  test("SalleSkin applies its hero part classes"):
    val c = host()
    val r = createRoot(c)
    r.render(Hero(overlay = true)(span("x")))
    Scheduler.flushSync()
    assert(hero(c).getAttribute("class").contains("salle-hero"))
    assert(content(c).getAttribute("class").contains("salle-hero__content"))
    assert(c.querySelector("[data-part=overlay]").getAttribute("class").contains("salle-hero__overlay"))
    r.unmount()

  test("DaisySkin emits the DaisyUI hero vocabulary"):
    val c = host()
    val r = createRoot(c)
    r.render(SkinProvider(DaisySkin)(Hero(overlay = true)(span("x"))))
    Scheduler.flushSync()
    assert(hero(c).getAttribute("class").contains("hero"))
    assert(content(c).getAttribute("class").contains("hero-content"))
    assert(c.querySelector("[data-part=overlay]").getAttribute("class").contains("hero-overlay"))
    r.unmount()
