package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Lightbox is a portal-rendered, presence-driven image viewer; Image is a previewable image
// that opens one; ImagePreviewGroup wires a set of Images into one shared, navigable viewer.
// These specs pin the observable contract a Playwright suite will later drive in a real
// browser: the lightbox portals to document.body only while open, carries the dialog ARIA +
// data-state mirror, navigates a multi-image set (with looping and clamping), zooms, and
// closes via the button / scrim / Escape; an Image becomes clickable once loaded and opens
// its viewer; a group opens one shared viewer at the clicked image and steps through them all.
// The enter frame and the exit delay are swapped for deterministic fakes via the shared
// Transition / Timers seams. jsdom does not load images, so load/error are dispatched.
class LightboxSpec extends AnyFunSuite with BeforeAndAfter:

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
    // Portals render to the body; clear it so an earlier test's still-mounted overlay can't
    // poison the next test's body queries.
    dom.document.body.textContent = ""
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

  // The lightbox portals to the body, so queries for it target the body, not the mount host.
  private def overlay(): dom.Element       = dom.document.body.querySelector("[data-part=overlay]")
  private def bodyPart(n: String): dom.Element = dom.document.body.querySelector(s"[data-part=$n]")
  private def lightImg(): dom.Element      = dom.document.body.querySelector("[data-part=image]")

  private def items3 = Vector(
    Preview("/a.jpg", "Alpha"),
    Preview("/b.jpg", "Beta"),
    Preview("/c.jpg", "Gamma"),
  )

  private def keydown(target: dom.EventTarget, k: String): Unit =
    target.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  private def fire(el: dom.Element, event: String): Unit =
    el.dispatchEvent(new dom.Event(event))
    Scheduler.flushSync()

  private def click(el: dom.Element): Unit =
    el.asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()

  // ---- Lightbox: presence + ARIA -------------------------------------------------------

  test("closed: nothing is portalled to the body"):
    val root = createRoot(host())
    root.render(Lightbox(open = false, items = items3))
    Scheduler.flushSync()
    assert(overlay() == null)
    root.unmount()

  test("open: portals a labelled dialog with the data-state mirror and the current image"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    val o = overlay()
    assert(o != null)
    assert(o.getAttribute("role") == "dialog")
    assert(o.getAttribute("aria-modal") == "true")
    assert(o.getAttribute("data-state") == "enter")
    assert(lightImg().getAttribute("src") == "/a.jpg")
    assert(lightImg().getAttribute("alt") == "Alpha")
    frames.dequeue()() // a frame advances enter → open
    Scheduler.flushSync()
    assert(overlay().getAttribute("data-state") == "open")
    root.unmount()

  test("empty item set: nothing is portalled even when open"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = Vector.empty))
    Scheduler.flushSync()
    assert(overlay() == null)
    root.unmount()

  // ---- Lightbox: navigation controls ---------------------------------------------------

  test("a single image shows no nav controls or counter"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = Vector(Preview("/only.jpg", "Only"))))
    Scheduler.flushSync()
    assert(bodyPart("prev") == null)
    assert(bodyPart("next") == null)
    assert(bodyPart("counter") == null)
    root.unmount()

  test("a multi-image set shows prev/next and an n / total counter"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    assert(bodyPart("prev") != null)
    assert(bodyPart("next") != null)
    assert(bodyPart("counter").textContent == "1 / 3")
    root.unmount()

  test("next/prev step the shown image and update the counter"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    click(bodyPart("next"))
    assert(lightImg().getAttribute("src") == "/b.jpg")
    assert(bodyPart("counter").textContent == "2 / 3")
    click(bodyPart("prev"))
    assert(lightImg().getAttribute("src") == "/a.jpg")
    assert(bodyPart("counter").textContent == "1 / 3")
    root.unmount()

  test("with loop (default), prev from the first wraps to the last"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    click(bodyPart("prev"))
    assert(lightImg().getAttribute("src") == "/c.jpg")
    assert(bodyPart("counter").textContent == "3 / 3")
    root.unmount()

  test("loop = false disables prev at the first image and next at the last"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3, loop = false))
    Scheduler.flushSync()
    val prev = bodyPart("prev")
    assert(prev.getAttribute("data-disabled") == "true")
    assert(prev.hasAttribute("disabled"))
    assert(bodyPart("next").getAttribute("data-disabled") == "false")
    // advance to the last image; now next is disabled and prev is enabled
    click(bodyPart("next"))
    click(bodyPart("next"))
    assert(bodyPart("counter").textContent == "3 / 3")
    assert(bodyPart("next").getAttribute("data-disabled") == "true")
    assert(bodyPart("prev").getAttribute("data-disabled") == "false")
    root.unmount()

  test("arrow keys navigate the set from the document"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    keydown(dom.document, "ArrowRight")
    assert(bodyPart("counter").textContent == "2 / 3")
    keydown(dom.document, "ArrowLeft")
    assert(bodyPart("counter").textContent == "1 / 3")
    root.unmount()

  test("onIndexChange reports navigation"):
    var last = -1
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3, onIndexChange = i => last = i))
    Scheduler.flushSync()
    click(bodyPart("next"))
    assert(last == 1)
    root.unmount()

  test("defaultIndex opens at the given image"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3, defaultIndex = 2))
    Scheduler.flushSync()
    assert(lightImg().getAttribute("src") == "/c.jpg")
    assert(bodyPart("counter").textContent == "3 / 3")
    root.unmount()

  // ---- Lightbox: zoom ------------------------------------------------------------------

  test("clicking the image toggles the zoom state and a navigation resets it"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3))
    Scheduler.flushSync()
    assert(lightImg().getAttribute("data-zoom") == "out")
    click(lightImg())
    assert(lightImg().getAttribute("data-zoom") == "in")
    // navigating to another image starts un-zoomed again
    click(bodyPart("next"))
    assert(lightImg().getAttribute("data-zoom") == "out")
    root.unmount()

  test("zoomable = false: clicking the image does not zoom"):
    val root = createRoot(host())
    root.render(Lightbox(open = true, items = items3, zoomable = false))
    Scheduler.flushSync()
    click(lightImg())
    assert(lightImg().getAttribute("data-zoom") == "out")
    root.unmount()

  // ---- Lightbox: dismissal -------------------------------------------------------------

  test("the close button reports onClose"):
    var closed = false
    val root   = createRoot(host())
    root.render(Lightbox(open = true, items = items3, onClose = () => closed = true))
    Scheduler.flushSync()
    click(bodyPart("close"))
    assert(closed)
    root.unmount()

  test("clicking the scrim closes; clicking the image does not"):
    var closed = false
    val root   = createRoot(host())
    root.render(Lightbox(open = true, items = items3, onClose = () => closed = true))
    Scheduler.flushSync()
    click(lightImg()) // target is the image, not the overlay → no close
    assert(!closed)
    click(overlay()) // target == overlay → close
    assert(closed)
    root.unmount()

  test("maskClosable = false: clicking the scrim does not close"):
    var closed = false
    val root   = createRoot(host())
    root.render(Lightbox(open = true, items = items3, maskClosable = false, onClose = () => closed = true))
    Scheduler.flushSync()
    click(overlay())
    assert(!closed)
    root.unmount()

  test("Escape closes when closeOnEsc, and is ignored when not"):
    var closedA = false
    val rootA   = createRoot(host())
    rootA.render(Lightbox(open = true, items = items3, onClose = () => closedA = true))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(closedA)
    rootA.unmount()

    var closedB = false
    val rootB   = createRoot(host())
    rootB.render(Lightbox(open = true, items = items3, closeOnEsc = false, onClose = () => closedB = true))
    Scheduler.flushSync()
    keydown(dom.document, "Escape")
    assert(!closedB)
    rootB.unmount()

  test("closing animates out (exit phase) and unmounts only after the delay elapses"):
    val c                       = host()
    var setOpen: Boolean => Unit = null
    val App = view {
      val (open, set, _) = useState(true)
      setOpen = set
      Lightbox(open = open, items = items3)
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
    assert(overlay().getAttribute("data-state") == "exit") // …animating out
    assert(timers.length == 1)

    timers.dequeue()() // exit delay elapses
    Scheduler.flushSync()
    assert(overlay() == null) // now unmounted
    root.unmount()

  test("DaisySkin re-skins the overlay via the provider"):
    val root = createRoot(host())
    root.render(SkinProvider(DaisySkin)(Lightbox(open = true, items = items3)))
    Scheduler.flushSync()
    assert(dom.document.body.querySelector(".salle-lightbox__overlay") == null)
    val o = overlay()
    assert(o != null) // data-part is skin-independent
    assert(o.classList.contains("fixed"))
    root.unmount()

  // ---- Image ---------------------------------------------------------------------------

  private def imgPart(c: dom.Element): dom.html.Image =
    c.querySelector("[data-part=img]").asInstanceOf[dom.html.Image]

  test("Image renders the source and starts in the loading state"):
    val c = host()
    render(Image(src = "/p.jpg", alt = "Picture"), c)
    Scheduler.flushSync()
    assert(imgPart(c).getAttribute("src") == "/p.jpg")
    assert(imgPart(c).getAttribute("alt") == "Picture")
    assert(c.querySelector("[data-part=root]").getAttribute("data-state") == "loading")
    // not yet loaded → not previewable
    assert(imgPart(c).getAttribute("role") == null)

  test("once loaded, the Image is previewable and clicking opens its own lightbox"):
    val c = host()
    render(Image(src = "/p.jpg", alt = "Picture"), c)
    Scheduler.flushSync()
    fire(imgPart(c), "load")
    assert(c.querySelector("[data-part=root]").getAttribute("data-state") == "loaded")
    assert(imgPart(c).getAttribute("role") == "button")
    assert(imgPart(c).getAttribute("tabindex") == "0")
    assert(overlay() == null)
    click(imgPart(c))
    assert(overlay() != null)
    assert(lightImg().getAttribute("src") == "/p.jpg")
    render(VEmpty, c) // tear down the portal

  test("Enter on a loaded previewable Image opens the lightbox"):
    val c = host()
    render(Image(src = "/p.jpg"), c)
    Scheduler.flushSync()
    fire(imgPart(c), "load")
    keydown(imgPart(c), "Enter")
    assert(overlay() != null)
    render(VEmpty, c)

  test("preview = false: a loaded Image is not clickable"):
    val c = host()
    render(Image(src = "/p.jpg", preview = false), c)
    Scheduler.flushSync()
    fire(imgPart(c), "load")
    assert(imgPart(c).getAttribute("role") == null)
    click(imgPart(c))
    assert(overlay() == null)

  test("an error with no fallback shows the Image error placeholder"):
    val c = host()
    render(Image(src = "/bad.jpg", alt = "Sea"), c)
    Scheduler.flushSync()
    fire(imgPart(c), "error")
    assert(c.querySelector("[data-part=root]").getAttribute("data-state") == "error")
    val ph = c.querySelector("[data-part=error]")
    assert(ph != null)
    assert(ph.getAttribute("role") == "img")
    assert(ph.getAttribute("aria-label") == "Sea")

  test("an Image error swaps to the fallback before giving up"):
    val c = host()
    render(Image(src = "/bad.jpg", fallback = "/fb.jpg"), c)
    Scheduler.flushSync()
    fire(imgPart(c), "error")
    assert(imgPart(c).getAttribute("src") == "/fb.jpg")
    fire(imgPart(c), "error") // fallback also fails → hard error
    assert(c.querySelector("[data-part=error]") != null)

  // ---- ImagePreviewGroup ---------------------------------------------------------------

  test("a group opens one shared lightbox at the clicked image and steps through the set"):
    val c = host()
    render(
      ImagePreviewGroup()(
        Image(src = "/g1.jpg", alt = "G1"),
        Image(src = "/g2.jpg", alt = "G2"),
        Image(src = "/g3.jpg", alt = "G3"),
      ),
      c,
    )
    Scheduler.flushSync()
    // load all three so each is previewable
    val imgs = c.querySelectorAll("[data-part=img]")
    for i <- 0 until imgs.length do fire(imgs.item(i).asInstanceOf[dom.Element], "load")
    // each Image defers to the group, so none renders its own lightbox until a click
    assert(overlay() == null)
    // click the second image → shared lightbox opens at index 1 of 3
    click(c.querySelector("img[alt=G2]"))
    assert(overlay() != null)
    assert(bodyPart("counter").textContent == "2 / 3")
    assert(lightImg().getAttribute("src") == "/g2.jpg")
    // navigation moves through the whole set
    keydown(dom.document, "ArrowRight")
    assert(bodyPart("counter").textContent == "3 / 3")
    assert(lightImg().getAttribute("src") == "/g3.jpg")
    render(VEmpty, c)

  test("a grouped Image opens the shared lightbox at its own position"):
    val c = host()
    render(
      ImagePreviewGroup()(
        Image(src = "/g1.jpg", alt = "G1"),
        Image(src = "/g2.jpg", alt = "G2"),
      ),
      c,
    )
    Scheduler.flushSync()
    val imgs = c.querySelectorAll("[data-part=img]")
    for i <- 0 until imgs.length do fire(imgs.item(i).asInstanceOf[dom.Element], "load")
    click(c.querySelector("img[alt=G1]"))
    assert(bodyPart("counter").textContent == "1 / 2")
    assert(lightImg().getAttribute("src") == "/g1.jpg")
    render(VEmpty, c)
