package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Tabs is a synchronous, data-driven tablist (no timers/presence). These specs pin the
// observable contract a Playwright suite will later drive in a real browser: it renders a
// role=tablist of role=tab buttons and the one active role=tabpanel, wires the ARIA ids,
// mirrors state to data-*, selects on click, and implements roving tabindex with automatic
// activation (Left/Right/Home/End move and select, skipping disabled, wrapping).
class TabsSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val sample: Seq[TabItem] = Seq(
    Tab(key = "a", label = "Alpha", content = "Panel A"),
    Tab(key = "b", label = "Beta", content = "Panel B"),
    Tab(key = "c", label = "Gamma", content = "Panel C", disabled = true),
    Tab(key = "d", label = "Delta", content = "Panel D"),
  )

  private def tabsOf(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=tab]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def tabKey(c: dom.Element, k: String): dom.html.Element =
    c.querySelector(s"[data-part=tab][data-key=$k]").asInstanceOf[dom.html.Element]
  private def panel(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=tabpanel]").asInstanceOf[dom.html.Element]
  private def list(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=tablist]").asInstanceOf[dom.html.Element]

  private def fireKey(el: dom.EventTarget, keyName: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = keyName; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("renders a tablist of tabs and the first panel, with ARIA roles"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    assert(list(c).getAttribute("role") == "tablist")
    assert(tabsOf(c).length == 4)
    assert(tabsOf(c).forall(_.getAttribute("role") == "tab"))
    val p = panel(c)
    assert(p.getAttribute("role") == "tabpanel")
    assert(p.textContent.contains("Panel A"))
    assert(tabKey(c, "a").getAttribute("aria-selected") == "true")
    assert(tabKey(c, "b").getAttribute("aria-selected") == "false")
    root.unmount()

  test("only the active panel is rendered, with id/labelledby linking"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    assert(c.querySelectorAll("[data-part=tabpanel]").length == 1)
    val p = panel(c)
    assert(p.getAttribute("aria-labelledby") == tabKey(c, "a").getAttribute("id"))
    assert(tabKey(c, "a").getAttribute("aria-controls") == p.getAttribute("id"))
    root.unmount()

  test("defaultActiveKey seeds the selection"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, defaultActiveKey = Some("b")))
    Scheduler.flushSync()
    assert(panel(c).textContent.contains("Panel B"))
    assert(tabKey(c, "b").getAttribute("data-active") == "true")
    root.unmount()

  test("clicking a tab activates it and switches the panel"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    tabKey(c, "b").click()
    Scheduler.flushSync()
    assert(panel(c).textContent.contains("Panel B"))
    assert(tabKey(c, "b").getAttribute("aria-selected") == "true")
    assert(tabKey(c, "a").getAttribute("aria-selected") == "false")
    root.unmount()

  test("onChange reports the chosen key"):
    val c        = host()
    var reported = Option.empty[String]
    val root     = createRoot(c)
    root.render(Tabs(items = sample, onChange = k => reported = Some(k)))
    Scheduler.flushSync()
    tabKey(c, "d").click()
    Scheduler.flushSync()
    assert(reported.contains("d"))
    root.unmount()

  test("a disabled tab carries the disabled attribute and does not activate"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    assert(tabKey(c, "c").hasAttribute("disabled"))
    assert(tabKey(c, "c").getAttribute("data-disabled") == "true")
    tabKey(c, "c").click()
    Scheduler.flushSync()
    assert(panel(c).textContent.contains("Panel A")) // unchanged
    root.unmount()

  test("roving tabindex: only the active tab is tabbable"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, defaultActiveKey = Some("b")))
    Scheduler.flushSync()
    assert(tabKey(c, "b").getAttribute("tabindex") == "0")
    assert(tabKey(c, "a").getAttribute("tabindex") == "-1")
    assert(tabKey(c, "d").getAttribute("tabindex") == "-1")
    root.unmount()

  test("ArrowRight moves to and activates the next tab"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    fireKey(tabKey(c, "a"), "ArrowRight")
    assert(panel(c).textContent.contains("Panel B"))
    assert(tabKey(c, "b").getAttribute("tabindex") == "0")
    root.unmount()

  test("ArrowRight skips a disabled tab"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, defaultActiveKey = Some("b")))
    Scheduler.flushSync()
    fireKey(tabKey(c, "b"), "ArrowRight") // c is disabled → lands on d
    assert(panel(c).textContent.contains("Panel D"))
    root.unmount()

  test("ArrowRight wraps from the last enabled tab to the first"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, defaultActiveKey = Some("d")))
    Scheduler.flushSync()
    fireKey(tabKey(c, "d"), "ArrowRight")
    assert(panel(c).textContent.contains("Panel A"))
    root.unmount()

  test("ArrowLeft from the first wraps to the last enabled tab"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample))
    Scheduler.flushSync()
    fireKey(tabKey(c, "a"), "ArrowLeft")
    assert(panel(c).textContent.contains("Panel D"))
    root.unmount()

  test("Home and End jump to the first and last enabled tabs"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, defaultActiveKey = Some("b")))
    Scheduler.flushSync()
    fireKey(tabKey(c, "b"), "End")
    assert(panel(c).textContent.contains("Panel D"))
    fireKey(tabKey(c, "d"), "Home")
    assert(panel(c).textContent.contains("Panel A"))
    root.unmount()

  test("controlled: activeKey is authoritative; a click only reports onChange"):
    val c        = host()
    var reported = Option.empty[String]
    val root     = createRoot(c)
    root.render(Tabs(items = sample, activeKey = Some("a"), onChange = k => reported = Some(k)))
    Scheduler.flushSync()
    tabKey(c, "b").click()
    Scheduler.flushSync()
    assert(reported.contains("b"))                  // intent reported…
    assert(panel(c).textContent.contains("Panel A")) // …but the controlled key keeps A shown
    root.unmount()

  test("variant and size map to the strip's classes and data-variant"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, variant = TabsVariant.Box, size = Size.Lg))
    Scheduler.flushSync()
    assert(list(c).className.contains("salle-tabs__list--box"))
    assert(list(c).className.contains("salle-tabs__list--lg"))
    assert(c.querySelector("[data-part=tabs]").getAttribute("data-variant") == "box")
    root.unmount()

  test("position bottom mirrors data-position and renders the panel before the strip"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tabs(items = sample, position = TabsPosition.Bottom))
    Scheduler.flushSync()
    val rootEl = c.querySelector("[data-part=tabs]")
    assert(rootEl.getAttribute("data-position") == "bottom")
    val kids = rootEl.childNodes
    // first child is the panel, second the tablist
    assert(kids.item(0).asInstanceOf[dom.Element].getAttribute("data-part") == "tabpanel")
    assert(kids.item(1).asInstanceOf[dom.Element].getAttribute("data-part") == "tablist")
    root.unmount()

  test("an icon slot renders inside the tab"):
    val c    = host()
    val root = createRoot(c)
    val withIcon = Seq(
      Tab(key = "a", label = "Alpha", content = "Panel A", icon = Some(span(cls := "ic", "★"))),
      Tab(key = "b", label = "Beta", content = "Panel B"),
    )
    root.render(Tabs(items = withIcon))
    Scheduler.flushSync()
    assert(tabKey(c, "a").querySelector("[data-part=icon]") != null)
    root.unmount()

  test("DaisySkin re-skins the strip via the provider"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Tabs(items = sample, variant = TabsVariant.Lift)))
    Scheduler.flushSync()
    assert(list(c).className.contains("tabs"))
    assert(list(c).className.contains("tabs-lift"))
    assert(tabKey(c, "a").className.contains("tab-active"))
    assert(!list(c).className.contains("salle-tabs__list"))
    root.unmount()
