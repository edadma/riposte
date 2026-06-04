package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// Carousel is a data-driven slide rotator with arrows, dots, keyboard, autoplay, and swipe.
// These specs pin the observable contract a Playwright suite drives in a real browser: it renders
// a role=region of role=group slides with the data-* mirror, advances on arrow/dot clicks and the
// keyboard, wraps (or clamps) at the ends, and autoplays through the Timers seam. The autoplay
// timer is swapped for a deterministic fake, installed per-test; pointer-swipe is a browser-only
// behaviour left to the e2e suite.
class CarouselSpec extends AnyFunSuite with BeforeAndAfter:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val timers = mutable.Queue.empty[() => Unit]
  private var savedSchedule: (() => Unit, Int) => (() => Unit) = null

  before {
    savedSchedule = Timers.schedule
    timers.clear()
    Timers.schedule = (fn, _) => { timers += fn; () => { timers -= fn; () } }
  }

  after {
    Timers.schedule = savedSchedule
  }

  // Drain the currently-pending timers (a snapshot — a re-armed timer is left for the next call,
  // so each autoplay tick is observable on its own).
  private def runTimers(): Unit =
    timers.dequeueAll(_ => true).foreach(_())
    Scheduler.flushSync()

  private val slides: Seq[VNode] = Seq(
    div(id := "s0", "Zero"),
    div(id := "s1", "One"),
    div(id := "s2", "Two"),
  )

  private def region(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=carousel]").asInstanceOf[dom.html.Element]
  private def slideEls(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=slide]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def dotEls(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=dot]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def arrow(c: dom.Element, dir: String): dom.html.Element =
    c.querySelector(s"[data-part=arrow][data-dir=$dir]").asInstanceOf[dom.html.Element]

  private def fireKey(el: dom.EventTarget, keyName: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = keyName; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  // --- structure & ARIA ------------------------------------------------------------------------

  test("renders a region of slides with the slide ARIA roles and the data-* mirror"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    assert(region(c).getAttribute("role") == "region")
    assert(region(c).getAttribute("aria-roledescription") == "carousel")
    assert(region(c).getAttribute("data-active-index") == "0")
    assert(slideEls(c).length == 3)
    assert(slideEls(c).forall(_.getAttribute("aria-roledescription") == "slide"))
    assert(slideEls(c).head.getAttribute("aria-label") == "Slide 1 of 3")
    assert(slideEls(c).head.getAttribute("data-active") == "true")
    assert(slideEls(c)(1).getAttribute("aria-hidden") == "true")
    r.unmount()

  test("defaultActiveIndex seeds the active slide"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, defaultActiveIndex = 1))
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "1")
    assert(slideEls(c)(1).getAttribute("data-active") == "true")
    r.unmount()

  test("dots are a tablist of tabs with aria-selected tracking the active slide"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=dots]").getAttribute("role") == "tablist")
    assert(dotEls(c).length == 3)
    assert(dotEls(c).forall(_.getAttribute("role") == "tab"))
    assert(dotEls(c).head.getAttribute("aria-selected") == "true")
    assert(dotEls(c)(1).getAttribute("aria-selected") == "false")
    r.unmount()

  // --- navigation ------------------------------------------------------------------------------

  test("clicking the next arrow advances, and a dot jumps to its slide"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    arrow(c, "next").click()
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "1")
    dotEls(c)(2).click()
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "2")
    r.unmount()

  test("infinite wraps past the last slide back to the first, and the prev arrow wraps backwards"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, defaultActiveIndex = 2))
    Scheduler.flushSync()
    arrow(c, "next").click()
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "0")
    arrow(c, "prev").click()
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "2")
    r.unmount()

  test("non-infinite clamps at the ends and disables the boundary arrow"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, infinite = false))
    Scheduler.flushSync()
    // At the first slide the prev arrow is disabled; clicking next walks to the last.
    assert(arrow(c, "prev").asInstanceOf[dom.html.Button].disabled)
    arrow(c, "next").click(); Scheduler.flushSync()
    arrow(c, "next").click(); Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "2")
    assert(arrow(c, "next").asInstanceOf[dom.html.Button].disabled)
    arrow(c, "next").click(); Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "2") // clamped, no wrap
    r.unmount()

  test("ArrowRight/ArrowLeft on the region step forward and back"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    fireKey(region(c), "ArrowRight")
    assert(region(c).getAttribute("data-active-index") == "1")
    fireKey(region(c), "ArrowLeft")
    assert(region(c).getAttribute("data-active-index") == "0")
    r.unmount()

  test("a vertical carousel steps with ArrowDown/ArrowUp instead"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, vertical = true))
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-vertical") == "true")
    fireKey(region(c), "ArrowRight") // ignored on a vertical carousel
    assert(region(c).getAttribute("data-active-index") == "0")
    fireKey(region(c), "ArrowDown")
    assert(region(c).getAttribute("data-active-index") == "1")
    r.unmount()

  // --- autoplay --------------------------------------------------------------------------------

  test("autoplay advances each time its scheduled timer fires"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, autoplay = true))
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "0")
    runTimers()
    assert(region(c).getAttribute("data-active-index") == "1")
    runTimers()
    assert(region(c).getAttribute("data-active-index") == "2")
    runTimers() // wraps
    assert(region(c).getAttribute("data-active-index") == "0")
    r.unmount()

  test("pauseOnHover halts autoplay while the pointer is over the carousel"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, autoplay = true))
    Scheduler.flushSync()
    region(c).dispatchEvent(new dom.Event("mouseenter", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()
    runTimers() // no timer armed while paused
    assert(region(c).getAttribute("data-active-index") == "0")
    region(c).dispatchEvent(new dom.Event("mouseleave", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()
    runTimers()
    assert(region(c).getAttribute("data-active-index") == "1")
    r.unmount()

  test("no autoplay timer is armed when autoplay is off"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    assert(timers.isEmpty)
    r.unmount()

  // --- controlled mode -------------------------------------------------------------------------

  test("controlled: activeIndex pins the slide and clicks only report through onChange"):
    val c = host()
    val r = createRoot(c)
    var reported = -1
    r.render(Carousel(slides = slides, activeIndex = Some(1), onChange = i => reported = i))
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-active-index") == "1")
    arrow(c, "next").click()
    Scheduler.flushSync()
    assert(reported == 2)                                         // intent reported
    assert(region(c).getAttribute("data-active-index") == "1")   // but the caller owns the index
    r.unmount()

  // --- effects & toggles -----------------------------------------------------------------------

  test("the fade effect gives each slide an opacity transition with only the active one shown"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, effect = CarouselEffect.Fade))
    Scheduler.flushSync()
    assert(region(c).getAttribute("data-effect") == "fade")
    val first = slideEls(c).head
    assert(first.style.opacity == "1")
    assert(slideEls(c)(1).style.opacity == "0")
    assert(first.style.position == "relative")
    assert(slideEls(c)(1).style.position == "absolute")
    r.unmount()

  test("arrows and dots can be turned off"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides, arrows = false, dots = false))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=arrow]") == null)
    assert(c.querySelector("[data-part=dots]") == null)
    r.unmount()

  test("a single slide shows no arrows or dots"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = Seq(div("only"))))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=arrow]") == null)
    assert(c.querySelector("[data-part=dots]") == null)
    r.unmount()

  // --- skins -----------------------------------------------------------------------------------

  test("SalleSkin applies its carousel part classes"):
    val c = host()
    val r = createRoot(c)
    r.render(Carousel(slides = slides))
    Scheduler.flushSync()
    assert(region(c).getAttribute("class").contains("salle-carousel"))
    assert(dotEls(c).head.getAttribute("class").contains("salle-carousel__dot--active"))
    assert(dotEls(c)(1).getAttribute("class").contains("salle-carousel__dot"))
    r.unmount()

  test("DaisySkin emits the DaisyUI carousel vocabulary"):
    val c = host()
    val r = createRoot(c)
    r.render(SkinProvider(DaisySkin)(Carousel(slides = slides)))
    Scheduler.flushSync()
    assert(region(c).getAttribute("class").contains("carousel"))
    assert(slideEls(c).head.getAttribute("class").contains("carousel-item"))
    assert(arrow(c, "next").getAttribute("class").contains("btn-circle"))
    r.unmount()
