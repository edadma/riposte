package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Theming drives the `data-theme` attribute on the document root. These pin the
// observable behaviour: a default theme on mount, setTheme/toggle updating the
// attribute + localStorage, an open theme set (any name applies), and restore on load.
class ThemeSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def rootTheme: String | Null =
    dom.document.documentElement.getAttribute("data-theme")

  // Render a probe component and surface the hook's API to the test.
  private def mountProbe(): (() => String, String => Unit, () => Unit) =
    var snap: String         = ""
    var setT: String => Unit = _ => ()
    var tog: () => Unit      = () => ()
    val Probe = view {
      val t = useTheme()
      snap = t.theme
      setT = t.setTheme
      tog = t.toggle
      div()
    }
    render(Probe(), host())
    Scheduler.flushSync()
    (() => snap, setT, tog)

  test("applies a data-theme on mount, defaulting to light (no OS matchMedia in jsdom)"):
    resetThemeForTest()
    mountProbe()
    assert(rootTheme == "light")

  test("setTheme updates the attribute and persists to localStorage"):
    resetThemeForTest()
    val (_, setT, _) = mountProbe()
    setT("dark")
    assert(rootTheme == "dark")
    assert(dom.window.localStorage.getItem("salle-theme") == "dark")

  test("toggle flips between light and dark"):
    resetThemeForTest()
    val (_, _, tog) = mountProbe()
    assert(rootTheme == "light")
    tog()
    assert(rootTheme == "dark")
    tog()
    assert(rootTheme == "light")

  test("any theme name is applied (open theme set)"):
    resetThemeForTest()
    val (_, setT, _) = mountProbe()
    setT("dracula")
    assert(rootTheme == "dracula")
    assert(dom.window.localStorage.getItem("salle-theme") == "dracula")

  test("a persisted theme is restored on first use"):
    resetThemeForTest()
    dom.window.localStorage.setItem("salle-theme", "dark")
    val (snap, _, _) = mountProbe()
    assert(rootTheme == "dark")
    assert(snap() == "dark")

  test("ThemeToggle renders an icon button and clicking it flips the theme"):
    resetThemeForTest()
    val c = host()
    render(ThemeToggle(), c)
    Scheduler.flushSync()
    val btn = c.querySelector("button.salle-theme-toggle")
    assert(btn != null)
    assert(btn.querySelector("svg") != null) // moon icon in light mode
    btn.asInstanceOf[dom.html.Button].click()
    Scheduler.flushSync()
    assert(rootTheme == "dark")
