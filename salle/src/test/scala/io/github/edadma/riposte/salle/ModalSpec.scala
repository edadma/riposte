package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Modal is a portal-rendered, presence-driven dialog. These specs pin the observable
// contract a Playwright suite will later drive in a real browser: it portals to
// document.body only while open, carries the dialog ARIA + data-state mirror, closes via
// the button / scrim / Escape (each gated by its prop), renders the title/footer slots,
// and animates out before unmounting. The enter frame and the exit delay are swapped for
// deterministic fakes via the shared Transition / Timers seams, installed per-test.
class ModalSpec extends AnyFunSuite with BeforeAndAfter:

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

  // The modal portals to document.body, so queries target the body, not the mount host.
  private def overlay(): dom.Element = dom.document.body.querySelector(".salle-modal__overlay")
  private def part(name: String): dom.Element =
    dom.document.body.querySelector(s"[data-part=$name]")

  private def keydown(target: dom.EventTarget, k: String): Unit =
    target.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("closed: nothing is portalled to the body"):
    val root = createRoot(host())
    root.render(Modal(open = false)("body"))
    Scheduler.flushSync()
    assert(overlay() == null)
    root.unmount()

  test("open: portals a labelled dialog with the title, body, and data-state mirror"):
    val root = createRoot(host())
    root.render(Modal(open = true, title = Some("Settings": VNode))("the body"))
    Scheduler.flushSync()
    val o = overlay()
    assert(o != null) // portalled to the body
    assert(o.getAttribute("data-state") == "enter")
    val box = part("box")
    assert(box.getAttribute("role") == "dialog")
    assert(box.getAttribute("aria-modal") == "true")
    val titleEl = part("title")
    assert(titleEl.textContent == "Settings")
    assert(box.getAttribute("aria-labelledby") == titleEl.getAttribute("id"))
    assert(part("body").textContent == "the body")
    frames.dequeue()() // a frame advances enter → open
    Scheduler.flushSync()
    assert(overlay().getAttribute("data-state") == "open")
    root.unmount()

  test("the close button reports onClose"):
    var closed = false
    val root   = createRoot(host())
    root.render(Modal(open = true, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    val close = part("close")
    assert(close != null)
    close.asInstanceOf[dom.html.Element].click()
    assert(closed)
    root.unmount()

  test("closable = false omits the close button"):
    val root = createRoot(host())
    root.render(Modal(open = true, closable = false)("x"))
    Scheduler.flushSync()
    assert(part("close") == null)
    root.unmount()

  test("clicking the scrim closes; clicking the box does not"):
    var closed = false
    val root   = createRoot(host())
    root.render(Modal(open = true, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    // a click whose target is the box bubbles to the overlay but must not close
    part("box").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(!closed)
    // a click on the scrim itself (target == the overlay) closes
    overlay().asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(closed)
    root.unmount()

  test("maskClosable = false: clicking the scrim does not close"):
    var closed = false
    val root   = createRoot(host())
    root.render(Modal(open = true, maskClosable = false, onClose = () => closed = true)("x"))
    Scheduler.flushSync()
    overlay().asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(!closed)
    root.unmount()

  test("Escape closes when closeOnEsc, and is ignored when not"):
    var closedA = false
    val rootA   = createRoot(host())
    rootA.render(Modal(open = true, onClose = () => closedA = true)("x"))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(closedA)
    rootA.unmount()

    var closedB = false
    val rootB   = createRoot(host())
    rootB.render(Modal(open = true, closeOnEsc = false, onClose = () => closedB = true)("x"))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(!closedB)
    rootB.unmount()

  test("alert = true switches the role to alertdialog"):
    val root = createRoot(host())
    root.render(Modal(open = true, alert = true)("x"))
    Scheduler.flushSync()
    assert(part("box").getAttribute("role") == "alertdialog")
    root.unmount()

  test("the footer slot renders in its own region"):
    val root = createRoot(host())
    root.render(Modal(open = true, footer = Some(Button("OK")))("x"))
    Scheduler.flushSync()
    val f = part("footer")
    assert(f != null)
    assert(f.textContent.contains("OK"))
    root.unmount()

  test("ariaLabel names the dialog when there is no title"):
    val root = createRoot(host())
    root.render(Modal(open = true, ariaLabel = "Image preview")("x"))
    Scheduler.flushSync()
    val box = part("box")
    assert(box.getAttribute("aria-label") == "Image preview")
    assert(box.getAttribute("aria-labelledby") == null)
    root.unmount()

  test("closing animates out (exit phase) and unmounts only after the delay elapses"):
    val c = host()
    var setOpen: Boolean => Unit = null
    val App = view {
      val (open, set, _) = useState(true)
      setOpen = set
      Modal(open = open)("x")
    }
    val root = createRoot(c)
    root.render(App())
    Scheduler.flushSync()
    frames.dequeue()() // settle enter → open
    Scheduler.flushSync()
    assert(overlay().getAttribute("data-state") == "open")

    setOpen(false)
    Scheduler.flushSync()
    assert(overlay() != null)                              // still mounted…
    assert(overlay().getAttribute("data-state") == "exit") // …now animating out
    assert(timers.length == 1)

    timers.dequeue()() // exit delay elapses
    Scheduler.flushSync()
    assert(overlay() == null) // now unmounted
    root.unmount()

  test("DaisySkin re-skins the overlay via the provider"):
    val root = createRoot(host())
    root.render(SkinProvider(DaisySkin)(Modal(open = true)("x")))
    Scheduler.flushSync()
    // SalleSkin's overlay class is absent; DaisyUI's modal vocabulary is present.
    assert(overlay() == null) // no salle-modal overlay…
    val daisy = dom.document.body.querySelector(".modal")
    assert(daisy != null)
    assert(daisy.getAttribute("data-part") == "overlay")
    root.unmount()
