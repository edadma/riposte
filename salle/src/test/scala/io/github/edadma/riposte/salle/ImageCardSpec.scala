package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// ImageCard is the gallery tile: lazy-loaded image, skeleton while loading, fade-in on
// load, fallback / error placeholder on failure, plus badge and hover-overlay slots.
// These pin the observable load lifecycle and the `data-*` state mirror a Playwright
// suite will later drive in a real browser. jsdom has no IntersectionObserver, so
// `useIntersectionObserver` reports "in view" immediately and the <img> renders at once;
// load/error are driven by dispatching the native events.
class ImageCardSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def card(c: dom.Element): dom.Element        = c.querySelector(".salle-image-card")
  private def image(c: dom.Element): dom.html.Image    = c.querySelector("[data-part=img]").asInstanceOf[dom.html.Image]
  private def state(c: dom.Element): String            = card(c).getAttribute("data-state")

  private def fire(el: dom.Element, event: String): Unit =
    el.dispatchEvent(new dom.Event(event))
    Scheduler.flushSync()

  test("renders a tile that loads the image immediately when IntersectionObserver is absent"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg", alt = "Mountains"), c)
    Scheduler.flushSync()
    val img = image(c)
    assert(img != null)
    assert(img.getAttribute("src") == "/w/1.jpg")
    assert(img.getAttribute("alt") == "Mountains")
    assert(card(c).getAttribute("data-inview") == "true")

  test("starts in the loading state with a skeleton, then loaded on the img load event"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg"), c)
    Scheduler.flushSync()
    assert(state(c) == "loading")
    assert(c.querySelector("[data-part=skeleton]") != null)
    fire(image(c), "load")
    assert(state(c) == "loaded")
    assert(c.querySelector("[data-part=skeleton]") == null) // skeleton gone once painted

  test("load event fires the onLoad callback"):
    var loaded = false
    val c      = host()
    render(ImageCard(src = "/w/1.jpg", onLoad = () => loaded = true), c)
    Scheduler.flushSync()
    fire(image(c), "load")
    assert(loaded)

  test("an error with no fallback shows the error placeholder and reports onError"):
    var errored = false
    val c       = host()
    render(ImageCard(src = "/bad.jpg", alt = "Sea", onError = () => errored = true), c)
    Scheduler.flushSync()
    fire(image(c), "error")
    assert(errored)
    assert(state(c) == "error")
    val ph = c.querySelector("[data-part=error]")
    assert(ph != null)
    assert(ph.getAttribute("role") == "img")
    assert(ph.getAttribute("aria-label") == "Sea")
    assert(c.querySelector("[data-part=img]") == null) // the failed img is gone

  test("an error swaps to the fallback source before giving up"):
    val c = host()
    render(ImageCard(src = "/bad.jpg", fallback = "/placeholder.jpg"), c)
    Scheduler.flushSync()
    fire(image(c), "error")
    // still showing an <img>, now pointed at the fallback (not the error placeholder)
    assert(state(c) != "error")
    assert(image(c).getAttribute("src") == "/placeholder.jpg")
    assert(c.querySelector("[data-part=error]") == null)

  test("when the fallback also fails, the error placeholder is shown"):
    val c = host()
    render(ImageCard(src = "/bad.jpg", fallback = "/also-bad.jpg"), c)
    Scheduler.flushSync()
    fire(image(c), "error") // primary fails -> fallback
    fire(image(c), "error") // fallback fails -> hard error
    assert(state(c) == "error")
    assert(c.querySelector("[data-part=error]") != null)

  test("fit maps to object-fit and ratio reserves the frame's aspect-ratio"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg", fit = ImageFit.Contain, ratio = "16/9"), c)
    Scheduler.flushSync()
    assert(image(c).style.getPropertyValue("object-fit") == "contain")
    val frame = c.querySelector("[data-part=frame]").asInstanceOf[dom.html.Element]
    assert(frame.style.getPropertyValue("aspect-ratio") == "16/9")

  test("the badge slot renders in the badge part"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg", badge = Some(span("4K"))), c)
    Scheduler.flushSync()
    val b = c.querySelector("[data-part=badge]")
    assert(b != null)
    assert(b.textContent.contains("4K"))

  test("the overlay slot renders in the overlay part"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg", overlay = Some(Button("Download"))), c)
    Scheduler.flushSync()
    val o = c.querySelector("[data-part=overlay]")
    assert(o != null)
    assert(o.querySelector("button") != null)

  test("clicking the tile fires onClick"):
    var clicks = 0
    val c      = host()
    render(ImageCard(src = "/w/1.jpg", onClick = () => clicks += 1), c)
    Scheduler.flushSync()
    card(c).asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(clicks == 1)

  test("rounded is a modifier class under the salle skin"):
    val c = host()
    render(ImageCard(src = "/w/1.jpg", rounded = true), c)
    Scheduler.flushSync()
    assert(card(c).classList.contains("salle-image-card--rounded"))

  test("DaisySkin re-skins the tile via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(ImageCard(src = "/w/1.jpg")), c)
    Scheduler.flushSync()
    val root = c.querySelector(".card")
    assert(root != null)
    assert(root.getAttribute("data-state") == "loading") // data-* state is skin-independent
    assert(c.querySelector(".salle-image-card") == null)
