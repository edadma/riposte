package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Tooltip is a presence-driven floating hint with delayed hover/focus/click triggers. These
// specs pin the observable contract a Playwright suite will later drive in a real browser: it
// mounts only while shown, carries role=tooltip + the data-state mirror + aria-describedby,
// honours the enter/leave delays, opens for the right trigger, and dismisses via Escape / an
// outside click. The enter frame and the delay/exit timers are swapped for deterministic fakes
// via the shared Transition / Timers seams, installed per-test.
class TooltipSpec extends AnyFunSuite with BeforeAndAfter:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val frames = mutable.Queue.empty[() => Unit]
  private val timers = mutable.Queue.empty[() => Unit]

  private var savedReq:      (() => Unit) => Int                = null
  private var savedCancel:   Int => Unit                       = null
  private var savedSchedule: (() => Unit, Int) => (() => Unit) = null

  before {
    savedReq      = Transition.requestFrame
    savedCancel   = Transition.cancelFrame
    savedSchedule = Timers.schedule
    frames.clear()
    timers.clear()
    Transition.requestFrame = cb => { frames += cb; frames.length }
    Transition.cancelFrame  = _ => ()
    Timers.schedule         = (fn, _) => { timers += fn; () => { timers -= fn; () } }
  }

  after {
    Transition.requestFrame = savedReq
    Transition.cancelFrame  = savedCancel
    Timers.schedule         = savedSchedule
  }

  private val hint: VNode = "Hint"

  // Drain the currently-pending timers (a snapshot — a timer that schedules another leaves the
  // new one for the next call, so exit-after-close stays observable).
  private def runTimers(): Unit =
    timers.dequeueAll(_ => true).foreach(_())
    Scheduler.flushSync()

  private def runFrames(): Unit =
    frames.dequeueAll(_ => true).foreach(_())
    Scheduler.flushSync()

  private def fire(el: dom.EventTarget, name: String): Unit =
    el.dispatchEvent(new dom.Event(name, new dom.EventInit { bubbles = true; cancelable = true }))
    Scheduler.flushSync()

  private def wrap(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=tooltip]").asInstanceOf[dom.html.Element]
  private def tip(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=tip]").asInstanceOf[dom.html.Element]

  test("closed by default: no tip, data-state closed, no aria-describedby"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint)(Button("trigger")))
    Scheduler.flushSync()
    assert(tip(c) == null)
    assert(wrap(c).getAttribute("data-state") == "closed")
    assert(wrap(c).getAttribute("aria-describedby") == null)
    root.unmount()

  test("hover shows after the enter delay, then fades to open"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint)(Button("trigger")))
    Scheduler.flushSync()
    fire(wrap(c), "mouseenter")
    assert(tip(c) == null) // still within the enter delay
    runTimers()            // enter delay elapses → shows
    val t = tip(c)
    assert(t != null)
    assert(t.getAttribute("role") == "tooltip")
    assert(t.getAttribute("data-state") == "enter")
    assert(wrap(c).getAttribute("aria-describedby") == t.getAttribute("id"))
    runFrames()            // a frame advances enter → open
    assert(tip(c).getAttribute("data-state") == "open")
    root.unmount()

  test("a quick hover-in-then-out never shows the tip"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint)(Button("trigger")))
    Scheduler.flushSync()
    fire(wrap(c), "mouseenter") // schedules show
    fire(wrap(c), "mouseleave") // cancels it, schedules hide
    runTimers()
    assert(tip(c) == null)
    root.unmount()

  test("leaving fades out (exit) and unmounts only after the delay"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint)(Button("trigger")))
    Scheduler.flushSync()
    fire(wrap(c), "mouseenter")
    runTimers()
    runFrames()
    assert(tip(c).getAttribute("data-state") == "open")
    fire(wrap(c), "mouseleave")
    runTimers() // leave delay elapses → setOpen(false) → exit phase + exit timer
    assert(tip(c) != null)
    assert(tip(c).getAttribute("data-state") == "exit")
    runTimers() // exit delay elapses → unmount
    assert(tip(c) == null)
    root.unmount()

  test("placement maps to a tip modifier and the wrapper data-placement"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, placement = TooltipPlacement.Bottom, defaultOpen = true)(Button("t")))
    Scheduler.flushSync()
    assert(tip(c).className.contains("salle-tooltip__tip--bottom"))
    assert(wrap(c).getAttribute("data-placement") == "bottom")
    root.unmount()

  test("colour maps to a tip modifier"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, color = Color.Primary, defaultOpen = true)(Button("t")))
    Scheduler.flushSync()
    assert(tip(c).className.contains("salle-tooltip__tip--primary"))
    root.unmount()

  test("Focus trigger shows on focus, not on hover"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, trigger = TooltipTrigger.Focus)(Button("t")))
    Scheduler.flushSync()
    fire(wrap(c), "mouseenter")
    runTimers()
    assert(tip(c) == null) // hover does nothing in Focus mode
    fire(wrap(c), "focusin")
    runTimers()
    assert(tip(c) != null)
    root.unmount()

  test("Click trigger toggles open and closed"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, trigger = TooltipTrigger.Click)(Button("t")))
    Scheduler.flushSync()
    wrap(c).asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(tip(c) != null) // click shows immediately (no delay)
    wrap(c).asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    runTimers() // exit delay → unmount
    assert(tip(c) == null)
    root.unmount()

  test("Click trigger dismisses on an outside pointerdown"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, trigger = TooltipTrigger.Click)(Button("t")))
    Scheduler.flushSync()
    wrap(c).asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(tip(c) != null)
    dom.document.body.dispatchEvent(new dom.Event("pointerdown", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()
    runTimers()
    assert(tip(c) == null)
    root.unmount()

  test("Escape dismisses an open tooltip"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, defaultOpen = true)(Button("t")))
    Scheduler.flushSync()
    assert(tip(c) != null)
    dom.document.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = "Escape"; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()
    runTimers()
    assert(tip(c) == null)
    root.unmount()

  test("disabled never shows, even with defaultOpen, and mirrors data-disabled"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, disabled = true, defaultOpen = true)(Button("t")))
    Scheduler.flushSync()
    assert(tip(c) == null)
    assert(wrap(c).getAttribute("data-disabled") == "true")
    fire(wrap(c), "mouseenter")
    runTimers()
    assert(tip(c) == null)
    root.unmount()

  test("controlled: open is authoritative, hide only reports onOpenChange"):
    val c        = host()
    var reported = Option.empty[Boolean]
    val root     = createRoot(c)
    root.render(Tooltip(tip = hint, open = Some(true), onOpenChange = b => reported = Some(b))(Button("t")))
    Scheduler.flushSync()
    assert(tip(c) != null)
    fire(wrap(c), "mouseleave")
    runTimers()
    assert(reported.contains(false)) // intent reported…
    assert(tip(c) != null)           // …but the controlled `open` keeps it shown
    root.unmount()

  test("defaultOpen shows the tip on first mount"):
    val c    = host()
    val root = createRoot(c)
    root.render(Tooltip(tip = hint, defaultOpen = true)(Button("t")))
    Scheduler.flushSync()
    assert(tip(c) != null)
    root.unmount()

  test("DaisySkin re-skins the tip via the provider"):
    val c    = host()
    val root = createRoot(c)
    root.render(SkinProvider(DaisySkin)(Tooltip(tip = hint, defaultOpen = true)(Button("t"))))
    Scheduler.flushSync()
    val t = tip(c)
    assert(t != null)
    assert(t.className.contains("bg-neutral"))
    assert(!t.className.contains("salle-tooltip__tip"))
    root.unmount()
