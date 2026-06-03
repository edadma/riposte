package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// The page-shell frame: Layout (+ Header/Content/Footer/Sider), Navbar, and the content
// Footer. These are mostly structural, so the specs pin the observable contract a Playwright
// suite drives in a real browser: the right landmark elements and ARIA roles, the data-* state
// mirror, the collapsible Sider's controllable/uncontrolled collapse, the Navbar's zones and
// toggle wiring, and that re-skinning to DaisySkin swaps the class vocabulary. matchMedia is
// absent in jsdom, so `data-narrow` is always false here and the responsive *hide* is left to
// the e2e suite; the toggle's state still flips, which these specs verify.
class LayoutSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def el(c: dom.Element, part: String): dom.html.Element =
    c.querySelector(s"[data-part=$part]").asInstanceOf[dom.html.Element]

  // ---- Layout container + bands ---------------------------------------------------------

  test("Layout renders a div column by default, mirroring data-part and has-sider"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout()(span("x")))
    Scheduler.flushSync()
    val l = el(c, "layout")
    assert(l.tagName == "DIV")
    assert(l.getAttribute("data-has-sider") == "false")
    assert(l.className.contains("salle-layout"))
    assert(!l.className.contains("salle-layout--has-sider"))
    root.unmount()

  test("Layout(hasSider = true) mirrors has-sider and the row modifier"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout(hasSider = true)(span("x")))
    Scheduler.flushSync()
    val l = el(c, "layout")
    assert(l.getAttribute("data-has-sider") == "true")
    assert(l.className.contains("salle-layout--has-sider"))
    root.unmount()

  test("Layout.Header is a <header> banner landmark with its children"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Header(span("brand")))
    Scheduler.flushSync()
    val h = el(c, "header")
    assert(h.tagName == "HEADER")
    assert(h.getAttribute("role") == "banner")
    assert(h.textContent.contains("brand"))
    root.unmount()

  test("Layout.Content is a <main> element"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Content(p("body")))
    Scheduler.flushSync()
    val m = el(c, "content")
    assert(m.tagName == "MAIN")
    assert(m.textContent.contains("body"))
    root.unmount()

  test("Layout.Footer band is a <footer> contentinfo landmark"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Footer(span("foot")))
    Scheduler.flushSync()
    val f = el(c, "layout-footer")
    assert(f.tagName == "FOOTER")
    assert(f.getAttribute("role") == "contentinfo")
    root.unmount()

  test("a nested layout composes a column shell with a row sider area"):
    val c    = host()
    val root = createRoot(c)
    root.render(
      Layout()(
        Layout.Header(span("top")),
        Layout(hasSider = true)(
          Layout.Sider()(span("nav")),
          Layout.Content(span("main")),
        ),
        Layout.Footer(span("bottom")),
      ),
    )
    Scheduler.flushSync()
    assert(c.querySelectorAll("[data-part=layout]").length == 2)
    assert(el(c, "header").textContent.contains("top"))
    assert(el(c, "sider").textContent.contains("nav"))
    assert(el(c, "content").textContent.contains("main"))
    assert(el(c, "layout-footer").textContent.contains("bottom"))
    root.unmount()

  // ---- Sider ----------------------------------------------------------------------------

  test("Sider is an <aside> with an inner wrapper and the open width applied"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider(width = "240px")(span("links")))
    Scheduler.flushSync()
    val a = el(c, "sider")
    assert(a.tagName == "ASIDE")
    assert(a.style.width == "240px")
    assert(a.getAttribute("data-collapsed") == "false")
    assert(a.getAttribute("aria-expanded") == "true")
    assert(el(c, "sider-inner").textContent.contains("links"))
    root.unmount()

  test("defaultCollapsed seeds the folded state and the collapsed width"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider(width = "240px", collapsedWidth = "64px", defaultCollapsed = true)(span("x")))
    Scheduler.flushSync()
    val a = el(c, "sider")
    assert(a.getAttribute("data-collapsed") == "true")
    assert(a.style.width == "64px")
    assert(a.getAttribute("aria-expanded") == "false")
    root.unmount()

  test("a non-collapsible sider shows no trigger"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider()(span("x")))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=sider-trigger]") == null)
    root.unmount()

  test("the collapsible trigger toggles the uncontrolled collapse and width"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider(width = "200px", collapsedWidth = "80px", collapsible = true)(span("x")))
    Scheduler.flushSync()
    assert(el(c, "sider").style.width == "200px")
    el(c, "sider-trigger").click()
    Scheduler.flushSync()
    assert(el(c, "sider").getAttribute("data-collapsed") == "true")
    assert(el(c, "sider").style.width == "80px")
    el(c, "sider-trigger").click()
    Scheduler.flushSync()
    assert(el(c, "sider").getAttribute("data-collapsed") == "false")
    assert(el(c, "sider").style.width == "200px")
    root.unmount()

  test("the trigger's aria-label reflects the collapse direction"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider(collapsible = true)(span("x")))
    Scheduler.flushSync()
    assert(el(c, "sider-trigger").getAttribute("aria-label") == "Collapse sidebar")
    el(c, "sider-trigger").click()
    Scheduler.flushSync()
    assert(el(c, "sider-trigger").getAttribute("aria-label") == "Expand sidebar")
    root.unmount()

  test("a controlled sider reports intent but does not change itself"):
    val c                          = host()
    val root                       = createRoot(c)
    var reported: Option[Boolean]  = None
    root.render(
      Layout.Sider(collapsed = Some(false), collapsible = true, onCollapse = b => reported = Some(b))(span("x")),
    )
    Scheduler.flushSync()
    el(c, "sider-trigger").click()
    Scheduler.flushSync()
    assert(reported == Some(true))
    assert(el(c, "sider").getAttribute("data-collapsed") == "false") // unchanged: caller owns it
    root.unmount()

  test("the sider theme is mirrored and picks the theme class"):
    val c    = host()
    val root = createRoot(c)
    root.render(Layout.Sider(theme = SiderTheme.Light)(span("x")))
    Scheduler.flushSync()
    val a = el(c, "sider")
    assert(a.getAttribute("data-sider-theme") == "light")
    assert(a.className.contains("salle-layout__sider--light"))
    root.unmount()

  // ---- Navbar ---------------------------------------------------------------------------

  test("Navbar is a <nav> navigation landmark with start/center/end zones"):
    val c    = host()
    val root = createRoot(c)
    root.render(Navbar(start = span("brand"), center = span("links"), end = span("actions")))
    Scheduler.flushSync()
    val n = el(c, "navbar")
    assert(n.tagName == "NAV")
    assert(n.getAttribute("role") == "navigation")
    assert(el(c, "start").textContent.contains("brand"))
    assert(el(c, "center").textContent.contains("links"))
    assert(el(c, "end").textContent.contains("actions"))
    root.unmount()

  test("a non-collapsible navbar shows no toggle and mirrors narrow=false in jsdom"):
    val c    = host()
    val root = createRoot(c)
    root.render(Navbar(start = span("brand")))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=toggle]") == null)
    assert(el(c, "navbar").getAttribute("data-narrow") == "false")
    assert(el(c, "navbar").getAttribute("data-collapsible") == "false")
    root.unmount()

  test("a collapsible navbar renders a toggle whose click flips data-open"):
    val c    = host()
    val root = createRoot(c)
    root.render(Navbar(start = span("brand"), end = span("menu"), collapsible = true))
    Scheduler.flushSync()
    val t = el(c, "toggle")
    assert(t.getAttribute("aria-expanded") == "false")
    assert(el(c, "navbar").getAttribute("data-open") == "false")
    t.click()
    Scheduler.flushSync()
    assert(el(c, "toggle").getAttribute("aria-expanded") == "true")
    assert(el(c, "navbar").getAttribute("data-open") == "true")
    root.unmount()

  test("color, sticky, shadow and rounded shape the navbar root class"):
    val c    = host()
    val root = createRoot(c)
    root.render(
      Navbar(
        start = span("b"),
        color = Color.Primary,
        sticky = true,
        shadow = NavbarShadow.Md,
        rounded = NavbarRounded.Lg,
      ),
    )
    Scheduler.flushSync()
    val cls = el(c, "navbar").className
    assert(cls.contains("salle-navbar--primary"))
    assert(cls.contains("salle-navbar--sticky"))
    assert(cls.contains("salle-navbar--shadow-md"))
    assert(cls.contains("salle-navbar--rounded-lg"))
    root.unmount()

  // ---- Footer (content) -----------------------------------------------------------------

  test("Footer is a <footer> stacking its columns"):
    val c    = host()
    val root = createRoot(c)
    root.render(Footer()(span("col")))
    Scheduler.flushSync()
    val f = el(c, "footer")
    assert(f.tagName == "FOOTER")
    assert(f.getAttribute("data-center") == "false")
    assert(f.getAttribute("data-horizontal") == "false")
    assert(f.textContent.contains("col"))
    root.unmount()

  test("Footer center/horizontal mirror to data-* and the modifier classes"):
    val c    = host()
    val root = createRoot(c)
    root.render(Footer(center = true, horizontal = true)(span("x")))
    Scheduler.flushSync()
    val f = el(c, "footer")
    assert(f.getAttribute("data-center") == "true")
    assert(f.getAttribute("data-horizontal") == "true")
    assert(f.className.contains("salle-footer--center"))
    assert(f.className.contains("salle-footer--horizontal"))
    root.unmount()

  test("Footer.Title is an <h6> column heading"):
    val c    = host()
    val root = createRoot(c)
    root.render(Footer()(Footer.Title("Company")))
    Scheduler.flushSync()
    val t = el(c, "footer-title")
    assert(t.tagName == "H6")
    assert(t.textContent.contains("Company"))
    root.unmount()

  // ---- DaisySkin re-skin ----------------------------------------------------------------

  test("DaisySkin swaps the Layout class vocabulary"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Layout(hasSider = true)(Layout.Header(span("x")))))
    Scheduler.flushSync()
    assert(el(c, "layout").className.contains("flex-row"))
    assert(el(c, "header").className.contains("bg-base-300"))
    root.unmount()

  test("DaisySkin Sider surfaces vary by theme"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Layout.Sider(theme = SiderTheme.Dark)(span("x"))))
    Scheduler.flushSync()
    assert(el(c, "sider").className.contains("bg-base-200"))
    root.unmount()

  test("DaisySkin Navbar emits the navbar vocabulary and a group-data toggle"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Navbar(start = span("b"), color = Color.Primary, collapsible = true)))
    Scheduler.flushSync()
    val cls = el(c, "navbar").className
    assert(cls.contains("navbar"))
    assert(cls.contains("group/navbar"))
    assert(cls.contains("bg-primary"))
    assert(el(c, "toggle").className.contains("group-data-[narrow=true]/navbar:inline-flex"))
    root.unmount()

  test("DaisySkin Footer emits the footer vocabulary"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Footer(center = true)(Footer.Title("Co"))))
    Scheduler.flushSync()
    assert(el(c, "footer").className.contains("footer-center"))
    assert(el(c, "footer-title").className.contains("footer-title"))
    root.unmount()
