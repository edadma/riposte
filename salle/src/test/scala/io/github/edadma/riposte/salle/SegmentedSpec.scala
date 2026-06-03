package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Segmented is a synchronous, data-driven single-choice control (no timers/presence). These
// specs pin the observable contract a Playwright suite will later drive in a real browser: it
// renders a role=radiogroup of role=radio segments, mirrors state to data-*, selects on click,
// and implements roving tabindex with selection-follows-focus (Left/Up/Right/Down/Home/End move
// and select, skipping disabled, wrapping).
class SegmentedSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val sample: Seq[SegmentedOption] = Seq(
    SegmentedOpt(value = "grid", label = "Grid"),
    SegmentedOpt(value = "list", label = "List"),
    SegmentedOpt(value = "map", label = "Map", disabled = true),
    SegmentedOpt(value = "card", label = "Card"),
  )

  private def segsOf(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=segment]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def seg(c: dom.Element, v: String): dom.html.Element =
    c.querySelector(s"[data-part=segment][data-value=$v]").asInstanceOf[dom.html.Element]
  private def group(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=segmented]").asInstanceOf[dom.html.Element]

  private def fireKey(el: dom.EventTarget, keyName: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = keyName; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("renders a radiogroup of radio segments with ARIA roles and selects the first by default"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    assert(group(c).getAttribute("role") == "radiogroup")
    assert(segsOf(c).length == 4)
    assert(segsOf(c).forall(_.getAttribute("role") == "radio"))
    assert(seg(c, "grid").getAttribute("aria-checked") == "true")
    assert(seg(c, "list").getAttribute("aria-checked") == "false")
    assert(group(c).getAttribute("data-value") == "grid")
    root.unmount()

  test("defaultValue seeds the selection"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, defaultValue = Some("list")))
    Scheduler.flushSync()
    assert(seg(c, "list").getAttribute("data-checked") == "true")
    assert(group(c).getAttribute("data-value") == "list")
    root.unmount()

  test("clicking a segment selects it"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    seg(c, "list").click()
    Scheduler.flushSync()
    assert(seg(c, "list").getAttribute("aria-checked") == "true")
    assert(seg(c, "grid").getAttribute("aria-checked") == "false")
    assert(group(c).getAttribute("data-value") == "list")
    root.unmount()

  test("onChange reports the chosen value"):
    val c        = host()
    var reported = Option.empty[String]
    val root     = createRoot(c)
    root.render(Segmented(options = sample, onChange = v => reported = Some(v)))
    Scheduler.flushSync()
    seg(c, "card").click()
    Scheduler.flushSync()
    assert(reported.contains("card"))
    root.unmount()

  test("a disabled segment carries the disabled attribute and does not select"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    assert(seg(c, "map").hasAttribute("disabled"))
    assert(seg(c, "map").getAttribute("data-disabled") == "true")
    seg(c, "map").click()
    Scheduler.flushSync()
    assert(group(c).getAttribute("data-value") == "grid") // unchanged
    root.unmount()

  test("disabling the whole control disables every segment"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, disabled = true))
    Scheduler.flushSync()
    assert(segsOf(c).forall(_.hasAttribute("disabled")))
    assert(group(c).getAttribute("data-disabled") == "true")
    root.unmount()

  test("roving tabindex: only the selected segment is tabbable"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, defaultValue = Some("list")))
    Scheduler.flushSync()
    assert(seg(c, "list").getAttribute("tabindex") == "0")
    assert(seg(c, "grid").getAttribute("tabindex") == "-1")
    assert(seg(c, "card").getAttribute("tabindex") == "-1")
    root.unmount()

  test("ArrowRight moves to and selects the next segment"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    fireKey(seg(c, "grid"), "ArrowRight")
    assert(group(c).getAttribute("data-value") == "list")
    assert(seg(c, "list").getAttribute("tabindex") == "0")
    root.unmount()

  test("ArrowDown also moves to the next segment"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    fireKey(seg(c, "grid"), "ArrowDown")
    assert(group(c).getAttribute("data-value") == "list")
    root.unmount()

  test("ArrowRight skips a disabled segment"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, defaultValue = Some("list")))
    Scheduler.flushSync()
    fireKey(seg(c, "list"), "ArrowRight") // map is disabled → lands on card
    assert(group(c).getAttribute("data-value") == "card")
    root.unmount()

  test("ArrowRight wraps from the last enabled segment to the first"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, defaultValue = Some("card")))
    Scheduler.flushSync()
    fireKey(seg(c, "card"), "ArrowRight")
    assert(group(c).getAttribute("data-value") == "grid")
    root.unmount()

  test("ArrowLeft from the first wraps to the last enabled segment"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample))
    Scheduler.flushSync()
    fireKey(seg(c, "grid"), "ArrowLeft")
    assert(group(c).getAttribute("data-value") == "card")
    root.unmount()

  test("Home and End jump to the first and last enabled segments"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, defaultValue = Some("list")))
    Scheduler.flushSync()
    fireKey(seg(c, "list"), "End")
    assert(group(c).getAttribute("data-value") == "card")
    fireKey(seg(c, "card"), "Home")
    assert(group(c).getAttribute("data-value") == "grid")
    root.unmount()

  test("controlled: value is authoritative; a click only reports onChange"):
    val c        = host()
    var reported = Option.empty[String]
    val root     = createRoot(c)
    root.render(Segmented(options = sample, value = Some("grid"), onChange = v => reported = Some(v)))
    Scheduler.flushSync()
    seg(c, "list").click()
    Scheduler.flushSync()
    assert(reported.contains("list"))                          // intent reported…
    assert(group(c).getAttribute("data-value") == "grid")      // …but the controlled value sticks
    root.unmount()

  test("size and block map to the strip's classes and data-*"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, size = Size.Lg, block = true))
    Scheduler.flushSync()
    assert(group(c).className.contains("salle-segmented--lg"))
    assert(group(c).className.contains("salle-segmented--block"))
    assert(group(c).getAttribute("data-size") == "lg")
    assert(group(c).getAttribute("data-block") == "true")
    root.unmount()

  test("an icon slot renders inside the segment"):
    val c    = host()
    val root = createRoot(c)
    val withIcon = Seq(
      SegmentedOpt(value = "grid", label = "Grid", icon = Some(span(cls := "ic", "▦"))),
      SegmentedOpt(value = "list", label = "List"),
    )
    root.render(Segmented(options = withIcon))
    Scheduler.flushSync()
    assert(seg(c, "grid").querySelector("[data-part=icon]") != null)
    root.unmount()

  test("ariaLabel names the group"):
    val c    = host()
    val root = createRoot(c)
    root.render(Segmented(options = sample, ariaLabel = "View mode"))
    Scheduler.flushSync()
    assert(group(c).getAttribute("aria-label") == "View mode")
    root.unmount()

  test("DaisySkin re-skins the strip via the provider"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Segmented(options = sample)))
    Scheduler.flushSync()
    assert(group(c).className.contains("join"))
    assert(seg(c, "grid").className.contains("join-item"))
    assert(seg(c, "grid").className.contains("btn-active"))
    assert(!group(c).className.contains("salle-segmented"))
    root.unmount()
