package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Toast is an imperatively-driven, portal-rendered notification layer. These specs pin the
// observable contract a Playwright suite will later drive in a real browser: a mounted
// Toaster portals each toast to document.body, the toast carries its type/role/aria-live +
// data-state mirror, it animates in (enter → frame → open) and out (exit → unmount) on the
// shared Transition / Timers seams, auto-dismisses after its duration (sticky when 0), is
// dismissed by its close button, and groups by placement. The imperative `toast.*` API and
// `clear()` / `dismiss(id)` round out the surface. The frame and the delay are swapped for
// deterministic fakes via the seams, installed per-test, exactly as ModalSpec does.
class ToastSpec extends AnyFunSuite with BeforeAndAfter:

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
    Transition.cancelFrame = _ => ()
    Timers.schedule = (fn, _) => { timers += fn; () => { timers -= fn; () } }
    ToastStore.resetForTest()
  }

  after {
    Transition.requestFrame = savedReq
    Transition.cancelFrame  = savedCancel
    Timers.schedule         = savedSchedule
    // Defensively remove any region left behind by a test that threw before unmounting, so a
    // leaked toast can't be seen by the next test.
    val regions = dom.document.body.querySelectorAll("[data-part=toast-region]")
    var i       = 0
    while i < regions.length do
      val n = regions(i)
      if n.parentNode != null then n.parentNode.removeChild(n)
      i += 1
    ToastStore.resetForTest()
  }

  // Toasts portal to document.body, so queries target the body, not the mount host.
  private def toastEl(): dom.Element = dom.document.body.querySelector("[data-part=toast]")
  private def part(name: String): dom.Element =
    dom.document.body.querySelector(s"[data-part=$name]")

  private def fireFrame(): Unit =
    frames.dequeue()()
    Scheduler.flushSync()

  private def fireTimer(): Unit =
    timers.dequeue()()
    Scheduler.flushSync()

  test("a success toast portals to the body with role=status, the polite live region, and the data mirror"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.success("Downloaded")
    Scheduler.flushSync()
    val t = toastEl()
    assert(t != null) // portalled to the body
    assert(t.getAttribute("data-type") == "success")
    assert(t.getAttribute("role") == "status")
    assert(t.getAttribute("aria-live") == "polite")
    assert(t.textContent.contains("Downloaded"))
    val region = part("toast-region")
    assert(region != null)
    assert(region.getAttribute("data-placement") == "top-right") // the default placement
    root.unmount()

  test("error and warning toasts announce assertively via role=alert; info/success do so politely"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.error("Upload failed")
    Scheduler.flushSync()
    val err = toastEl()
    assert(err.getAttribute("data-type") == "error")
    assert(err.getAttribute("role") == "alert")
    assert(err.getAttribute("aria-live") == "assertive")
    toast.warning("Almost full")
    Scheduler.flushSync()
    val warn = dom.document.body.querySelector("[data-type=warning]")
    assert(warn.getAttribute("role") == "alert")
    toast.info("Heads up")
    Scheduler.flushSync()
    val info = dom.document.body.querySelector("[data-type=info]")
    assert(info.getAttribute("role") == "status")
    root.unmount()

  test("a toast enters then a frame advances its data-state to open"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.success("Saved")
    Scheduler.flushSync()
    assert(toastEl().getAttribute("data-state") == "enter")
    fireFrame() // a frame settles enter → open
    assert(toastEl().getAttribute("data-state") == "open")
    root.unmount()

  test("the close button animates the toast out (exit) then removes it after the delay; onClose fires"):
    var closed = false
    val root   = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.show("Bye", onClose = () => closed = true)
    Scheduler.flushSync()
    fireFrame() // settle to open
    val close = part("close")
    assert(close != null)
    close.asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(closed)
    assert(toastEl() != null) // still mounted…
    assert(toastEl().getAttribute("data-state") == "exit") // …animating out
    fireTimer() // exit delay elapses
    assert(toastEl() == null) // now removed
    root.unmount()

  test("closable = false omits the close button"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.show("No close", closable = false)
    Scheduler.flushSync()
    assert(toastEl() != null)
    assert(part("close") == null)
    root.unmount()

  test("a toast auto-dismisses after its duration elapses"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.success("Temporary")
    Scheduler.flushSync()
    fireFrame() // settle to open
    assert(toastEl().getAttribute("data-state") == "open")
    fireTimer() // the duration timer fires → begins exit
    assert(toastEl().getAttribute("data-state") == "exit")
    fireTimer() // the exit delay elapses → removed
    assert(toastEl() == null)
    root.unmount()

  test("a sticky toast (duration = 0) is never auto-dismissed"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.show("Stays", duration = 0)
    Scheduler.flushSync()
    fireFrame() // settle to open
    assert(timers.isEmpty) // no auto-dismiss timer was scheduled
    assert(toastEl() != null)
    root.unmount()

  test("a loading toast shows a spinner, reads as loading, and is sticky"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.loading("Preparing…")
    Scheduler.flushSync()
    val t = toastEl()
    assert(t.getAttribute("data-type") == "loading")
    assert(part("spinner") != null)
    fireFrame()
    assert(timers.isEmpty) // sticky: no auto-dismiss
    root.unmount()

  test("a description renders in its own part"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.success("Downloaded", description = Some("wallpaper-4k.jpg saved": VNode))
    Scheduler.flushSync()
    val d = part("description")
    assert(d != null)
    assert(d.textContent.contains("wallpaper-4k.jpg saved"))
    root.unmount()

  test("a clickable toast is focusable, mirrors data-clickable, and reports clicks"):
    var clicked = false
    val root    = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.show("Open me", onClick = Some(() => clicked = true))
    Scheduler.flushSync()
    val t = toastEl()
    assert(t.getAttribute("data-clickable") == "true")
    assert(t.getAttribute("tabindex") == "0")
    t.asInstanceOf[dom.html.Element].click()
    assert(clicked)
    root.unmount()

  test("clear() removes every toast at once"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.success("One")
    toast.error("Two")
    Scheduler.flushSync()
    assert(dom.document.body.querySelectorAll("[data-part=toast]").length == 2)
    toast.clear()
    Scheduler.flushSync()
    assert(dom.document.body.querySelector("[data-part=toast]") == null)
    root.unmount()

  test("dismiss(id) removes a single toast by id"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    val id = toast.success("Keep")
    toast.error("Drop")
    Scheduler.flushSync()
    assert(dom.document.body.querySelectorAll("[data-part=toast]").length == 2)
    toast.dismiss(id)
    Scheduler.flushSync()
    val remaining = dom.document.body.querySelectorAll("[data-part=toast]")
    assert(remaining.length == 1)
    assert(remaining(0).getAttribute("data-type") == "error")
    root.unmount()

  test("toasts at different placements render in separate regions"):
    val root = createRoot(host())
    root.render(Toaster())
    Scheduler.flushSync()
    toast.show("Up", placement = ToastPlacement.TopLeft)
    toast.show("Down", placement = ToastPlacement.BottomRight)
    Scheduler.flushSync()
    assert(dom.document.body.querySelector("[data-placement=top-left]") != null)
    assert(dom.document.body.querySelector("[data-placement=bottom-right]") != null)
    root.unmount()

  test("DaisySkin re-skins the region and item via the provider"):
    val root = createRoot(host())
    root.render(SkinProvider(DaisySkin)(Toaster()))
    Scheduler.flushSync()
    toast.success("Reskinned")
    Scheduler.flushSync()
    val region = part("toast-region")
    assert(region.getAttribute("class").contains("toast"))
    assert(part("toast").getAttribute("class").contains("alert"))
    root.unmount()
