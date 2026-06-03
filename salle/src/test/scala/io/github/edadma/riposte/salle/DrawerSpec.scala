package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Drawer is a portal-rendered, presence-driven edge panel — the same overlay machinery as
// Modal, docked to an edge. These specs pin the observable contract a Playwright suite drives
// in a real browser: it portals to document.body only while open, carries the dialog ARIA +
// data-state/data-placement mirror, sizes along its docking axis, closes via the button / scrim
// / Escape (each gated by its prop), renders the title/extra/footer slots, and slides out before
// unmounting. The enter frame and exit delay are swapped for deterministic fakes via the shared
// Transition / Timers seams. Focus behaviour lives with the core useFocusTrap hook's own tests.
class DrawerSpec extends AnyFunSuite with BeforeAndAfter:

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
    // Drop any portal/listener a prior test left behind so body queries are unambiguous.
    dom.document.body.innerHTML = ""
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

  // The drawer portals to document.body, so queries target the body, not the mount host.
  private def root(): dom.Element = dom.document.body.querySelector("[data-part=drawer-root]")
  private def part(name: String): dom.html.Element =
    dom.document.body.querySelector(s"[data-part=$name]").asInstanceOf[dom.html.Element]

  private def keydown(target: dom.EventTarget, k: String): Unit =
    target.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("closed: nothing is portalled to the body"):
    val r = createRoot(host())
    r.render(Drawer(open = false)("body"))
    Scheduler.flushSync()
    assert(root() == null)
    r.unmount()

  test("open: portals a dialog panel with the placement, data-state mirror, and body"):
    val r = createRoot(host())
    r.render(Drawer(open = true, placement = DrawerPlacement.Right)("the body"))
    Scheduler.flushSync()
    assert(root() != null)
    assert(root().getAttribute("data-state") == "enter")
    assert(root().getAttribute("data-placement") == "right")
    val panel = part("panel")
    assert(panel.getAttribute("role") == "dialog")
    assert(panel.getAttribute("aria-modal") == "true")
    assert(panel.getAttribute("data-placement") == "right")
    assert(panel.className.contains("salle-drawer__panel--right"))
    assert(part("body").textContent == "the body")
    assert(part("mask") != null)
    frames.dequeue()() // a frame advances enter → open
    Scheduler.flushSync()
    assert(root().getAttribute("data-state") == "open")
    assert(part("panel").getAttribute("data-state") == "open")
    r.unmount()

  test("the title / extra / footer slots render, and the title labels the dialog"):
    val r = createRoot(host())
    r.render(
      Drawer(
        open = true,
        title = Some("Filters": VNode),
        extra = Some(span("e"): VNode),
        footer = Some(span("f"): VNode),
      )("body"),
    )
    Scheduler.flushSync()
    val title = part("title")
    assert(title.textContent == "Filters")
    assert(part("panel").getAttribute("aria-labelledby") == title.getAttribute("id"))
    assert(part("extra").textContent == "e")
    assert(part("footer").textContent == "f")
    r.unmount()

  test("ariaLabel names the dialog when there is no title"):
    val r = createRoot(host())
    r.render(Drawer(open = true, ariaLabel = "Settings panel")("x"))
    Scheduler.flushSync()
    assert(part("panel").getAttribute("aria-label") == "Settings panel")
    assert(dom.document.body.querySelector("[data-part=title]") == null)
    r.unmount()

  test("closable = false omits the close button"):
    val r = createRoot(host())
    r.render(Drawer(open = true, closable = false)("x"))
    Scheduler.flushSync()
    assert(dom.document.body.querySelector("[data-part=close]") == null)
    r.unmount()

  test("mask = false omits the scrim"):
    val r = createRoot(host())
    r.render(Drawer(open = true, mask = false)("x"))
    Scheduler.flushSync()
    assert(dom.document.body.querySelector("[data-part=mask]") == null)
    r.unmount()

  test("size sets the width for a side drawer and the height for a top/bottom drawer"):
    val r1 = createRoot(host())
    r1.render(Drawer(open = true, placement = DrawerPlacement.Right, size = "400px")("x"))
    Scheduler.flushSync()
    assert(part("panel").style.width == "400px")
    r1.unmount()
    Scheduler.flushSync()
    val r2 = createRoot(host())
    r2.render(Drawer(open = true, placement = DrawerPlacement.Bottom, size = "240px")("x"))
    Scheduler.flushSync()
    assert(part("panel").style.height == "240px")
    r2.unmount()

  test("the close button reports onClose"):
    var closed = false
    val r      = createRoot(host())
    r.render(Drawer(open = true, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    part("close").click()
    assert(closed)
    r.unmount()

  test("clicking the scrim closes; clicking the panel does not"):
    var closed = false
    val r      = createRoot(host())
    r.render(Drawer(open = true, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    part("panel").click() // the panel is a sibling of the mask, not a close target
    Scheduler.flushSync()
    assert(!closed)
    part("mask").click()
    Scheduler.flushSync()
    assert(closed)
    r.unmount()

  test("maskClosable = false: clicking the scrim does not close"):
    var closed = false
    val r      = createRoot(host())
    r.render(Drawer(open = true, maskClosable = false, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    part("mask").click()
    Scheduler.flushSync()
    assert(!closed)
    r.unmount()

  test("Escape closes when closeOnEsc, and is ignored when not"):
    var closed = 0
    val r      = createRoot(host())
    r.render(Drawer(open = true, closeOnEsc = true, onClose = () => closed += 1)("x"))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(closed == 1)
    r.unmount()
    Scheduler.flushSync()

    val r2 = createRoot(host())
    r2.render(Drawer(open = true, closeOnEsc = false, onClose = () => closed += 1)("x"))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(closed == 1) // unchanged
    r2.unmount()

  test("closing animates out (exit phase) and unmounts only after the delay elapses"):
    var open = true
    var bump: Int => Unit = null
    val Comp = view {
      val (_, set, _) = useState(0)
      bump = set
      Drawer(open = open, exitMs = 250)("x")
    }
    val r = createRoot(host())
    r.render(Comp())
    Scheduler.flushSync()
    frames.dequeue()() // enter → open
    Scheduler.flushSync()
    assert(root().getAttribute("data-state") == "open")
    open = false
    bump(1) // re-render closed → exit phase, still mounted
    Scheduler.flushSync()
    assert(root().getAttribute("data-state") == "exit")
    timers.dequeue()() // the exit delay elapses → unmount
    Scheduler.flushSync()
    assert(root() == null)
    r.unmount()

  test("DaisySkin re-skins the panel and mask via the provider"):
    val r = createRoot(host())
    r.render(SkinProvider(DaisySkin)(Drawer(open = true, placement = DrawerPlacement.Left)("x")))
    Scheduler.flushSync()
    assert(part("panel").className.contains("fixed"))
    assert(part("panel").className.contains("data-[state=open]:translate-x-0"))
    assert(part("mask").className.contains("bg-black/50"))
    r.unmount()
