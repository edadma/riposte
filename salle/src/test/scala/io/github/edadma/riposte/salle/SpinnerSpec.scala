package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Spinner, Progress and RadialProgress are loading affordances. These specs pin the observable
// contract a Playwright suite will later drive: the skin class, the data-* state mirror, the
// indicator vs overlay structure, the determinate/indeterminate progress attributes, the radial
// ring's --value custom property and centre text, the ARIA exposure, and DaisySkin reskinning.
class SpinnerSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def one(c: dom.Element, sel: String): dom.html.Element =
    c.querySelector(sel).asInstanceOf[dom.html.Element]

  // ---- Spinner -------------------------------------------------------------

  test("Spinner renders a status indicator with the skin class and an off-screen label"):
    val c = host()
    render(Spinner()(), c)
    Scheduler.flushSync()
    val box = one(c, "[data-part=spinner]")
    assert(box != null)
    assert(box.getAttribute("role") == "status")
    assert(box.getAttribute("aria-live") == "polite")
    val mark = one(c, "[data-part=indicator]")
    assert(mark.className.contains("salle-spinner"))
    assert(mark.className.contains("salle-spinner--spinner"))
    assert(mark.className.contains("salle-spinner--md"))
    assert(mark.getAttribute("aria-hidden") == "true")
    val sr = one(c, "[data-part=label]")
    assert(sr.textContent == "Loading")

  test("Spinner with spinning = false renders nothing"):
    val c = host()
    render(Spinner(spinning = false)(), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=spinner]") == null)
    assert(c.querySelector("[data-part=indicator]") == null)

  test("Spinner type and size and colour map to indicator modifiers"):
    val c = host()
    render(Spinner(kind = SpinnerType.Dots, size = Size.Lg, color = Color.Primary)(), c)
    Scheduler.flushSync()
    val mark = one(c, "[data-part=indicator]")
    assert(mark.className.contains("salle-spinner--dots"))
    assert(mark.className.contains("salle-spinner--lg"))
    assert(mark.className.contains("salle-spinner--primary"))

  test("Spinner tip renders a visible caption instead of the off-screen label"):
    val c = host()
    render(Spinner(tip = "Loading wallpapers…")(), c)
    Scheduler.flushSync()
    val tip = one(c, "[data-part=tip]")
    assert(tip.textContent == "Loading wallpapers…")
    assert(c.querySelector("[data-part=label]") == null)

  test("Spinner label overrides the default off-screen text"):
    val c = host()
    render(Spinner(label = "Fetching")(), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=label]").textContent == "Fetching")

  test("Spinner with children is an overlay over dimmed, inert content while spinning"):
    val c = host()
    render(Spinner(spinning = true)(div(cls := "child", "content")), c)
    Scheduler.flushSync()
    val wrap = one(c, "[data-part=spinner-wrap]")
    assert(wrap.getAttribute("aria-busy") == "true")
    assert(c.querySelector("[data-part=overlay]") != null)
    val content = one(c, "[data-part=content]")
    assert(content.getAttribute("aria-hidden") == "true")
    assert(content.className.contains("salle-spinner-content--busy"))
    assert(c.querySelector(".child") != null)

  test("Spinner overlay clears once spinning is false"):
    val c = host()
    render(Spinner(spinning = false)(div(cls := "child", "content")), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=overlay]") == null)
    val content = one(c, "[data-part=content]")
    assert(content.getAttribute("aria-hidden") == "false")
    assert(c.querySelector(".child") != null)

  test("Spinner reskins to DaisyUI's loading classes via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Spinner(size = Size.Md)()), c)
    Scheduler.flushSync()
    val mark = one(c, "[data-part=indicator]")
    assert(mark.className.contains("loading"))
    assert(mark.className.contains("loading-spinner"))
    assert(mark.className.contains("loading-md"))
    assert(!mark.className.contains("salle-spinner"))

  // ---- Progress (linear) ---------------------------------------------------

  test("Progress renders a determinate native bar with value, max and the data-* mirror"):
    val c = host()
    render(Progress(value = Some(70.0)), c)
    Scheduler.flushSync()
    val bar = one(c, "[data-part=progress]")
    assert(bar.tagName.toLowerCase == "progress")
    assert(bar.className.contains("salle-progress"))
    assert(bar.getAttribute("value") == "70")
    assert(bar.getAttribute("max") == "100")
    assert(bar.getAttribute("data-indeterminate") == "false")
    assert(bar.getAttribute("data-value") == "70")

  test("Progress with no value is indeterminate (no value attribute)"):
    val c = host()
    render(Progress(value = None), c)
    Scheduler.flushSync()
    val bar = one(c, "[data-part=progress]")
    assert(bar.getAttribute("value") == null)
    assert(bar.getAttribute("data-indeterminate") == "true")

  test("Progress colour maps to a bar modifier"):
    val c = host()
    render(Progress(value = Some(40.0), color = Color.Success), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=progress]").className.contains("salle-progress--success"))

  test("Progress label sets an accessible name"):
    val c = host()
    render(Progress(value = Some(10.0), label = "Upload"), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=progress]").getAttribute("aria-label") == "Upload")

  test("Progress reskins to DaisyUI's progress classes via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Progress(value = Some(50.0), color = Color.Primary)), c)
    Scheduler.flushSync()
    val bar = one(c, "[data-part=progress]")
    assert(bar.className.contains("progress"))
    assert(bar.className.contains("progress-primary"))
    assert(!bar.className.contains("salle-progress"))

  // ---- RadialProgress ------------------------------------------------------

  test("RadialProgress renders a progressbar with the --value property and centre percentage"):
    val c = host()
    render(RadialProgress(value = 70)(), c)
    Scheduler.flushSync()
    val ring = one(c, "[data-part=radial-progress]")
    assert(ring.getAttribute("role") == "progressbar")
    assert(ring.getAttribute("aria-valuenow") == "70")
    assert(ring.getAttribute("aria-valuemin") == "0")
    assert(ring.getAttribute("aria-valuemax") == "100")
    assert(ring.getAttribute("data-value") == "70")
    assert(ring.style.getPropertyValue("--value") == "70")
    assert(one(c, "[data-part=value]").textContent == "70%")

  test("RadialProgress with showValue = false renders no centre text"):
    val c = host()
    render(RadialProgress(value = 30, showValue = false)(), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=value]") == null)

  test("RadialProgress with children renders custom centre content (no percentage)"):
    val c = host()
    render(RadialProgress(value = 30)(span(cls := "centre", "★")), c)
    Scheduler.flushSync()
    assert(c.querySelector(".centre") != null)
    assert(c.querySelector("[data-part=value]") == null)

  test("RadialProgress size and thickness set inline custom properties"):
    val c = host()
    render(RadialProgress(value = 50, size = "6rem", thickness = "4px")(), c)
    Scheduler.flushSync()
    val ring = one(c, "[data-part=radial-progress]")
    assert(ring.style.getPropertyValue("--size") == "6rem")
    assert(ring.style.getPropertyValue("--thickness") == "4px")

  test("RadialProgress colour maps to a ring modifier"):
    val c = host()
    render(RadialProgress(value = 50, color = Color.Accent)(), c)
    Scheduler.flushSync()
    assert(one(c, "[data-part=radial-progress]").className.contains("salle-radial-progress--accent"))

  test("RadialProgress reskins to DaisyUI's radial-progress classes via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(RadialProgress(value = 50, color = Color.Primary)()), c)
    Scheduler.flushSync()
    val ring = one(c, "[data-part=radial-progress]")
    assert(ring.className.contains("radial-progress"))
    assert(ring.className.contains("text-primary"))
    assert(!ring.className.contains("salle-radial-progress"))
