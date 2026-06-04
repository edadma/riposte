package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Empty is a pure display component (no timers/presence/keyboard). These specs pin its structure:
// a role=status region with an illustration, a description, and an optional action footer, all
// mirrored to data-*, with the illustration choice ([[EmptyImage]]) resolved by the component.
class EmptySpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def root(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=empty]").asInstanceOf[dom.html.Element]
  private def part(c: dom.Element, name: String): dom.html.Element =
    c.querySelector(s"[data-part=$name]").asInstanceOf[dom.html.Element]

  test("renders a role=status region with the default illustration, description, and no footer"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty()())
    Scheduler.flushSync()
    assert(root(c).getAttribute("role") == "status")
    assert(root(c).getAttribute("data-part") == "empty")
    val img = part(c, "image")
    assert(img != null)
    assert(img.querySelector("svg") != null)
    assert(img.querySelector("svg").getAttribute("viewBox") == "0 0 184 152")
    assert(part(c, "description").textContent == "No data")
    assert(part(c, "footer") == null)
    r.unmount()

  test("default (no description) labels the region with the fallback text"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty()())
    Scheduler.flushSync()
    assert(root(c).getAttribute("aria-label") == "No data")
    r.unmount()

  test("a custom description renders and the region falls back to the generic label"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty(description = Some("No wallpapers found": VNode))())
    Scheduler.flushSync()
    assert(part(c, "description").textContent == "No wallpapers found")
    assert(root(c).getAttribute("aria-label") == "Empty")
    r.unmount()

  test("EmptyImage.Simple swaps in the minimal illustration"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty(image = EmptyImage.Simple)())
    Scheduler.flushSync()
    assert(part(c, "image").querySelector("svg").getAttribute("viewBox") == "0 0 64 41")
    r.unmount()

  test("EmptyImage.Hidden omits the illustration entirely"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty(image = EmptyImage.Hidden)())
    Scheduler.flushSync()
    assert(part(c, "image") == null)
    assert(part(c, "description") != null)
    r.unmount()

  test("EmptyImage.Custom renders the supplied node in the image slot"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty(image = EmptyImage.Custom(div(id := "my-icon", "X")))())
    Scheduler.flushSync()
    assert(part(c, "image").querySelector("#my-icon") != null)
    assert(part(c, "image").querySelector("svg") == null)
    r.unmount()

  test("children render in the action footer"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty()(button(id := "act", typ := "button", "Clear filters")))
    Scheduler.flushSync()
    val footer = part(c, "footer")
    assert(footer != null)
    assert(footer.querySelector("#act") != null)
    assert(footer.querySelector("#act").textContent == "Clear filters")
    r.unmount()

  test("the description default applies the SalleSkin part classes"):
    val c = host()
    val r = createRoot(c)
    r.render(Empty()())
    Scheduler.flushSync()
    assert(root(c).getAttribute("class").contains("salle-empty"))
    assert(part(c, "description").getAttribute("class").contains("salle-empty__description"))
    r.unmount()

  test("DaisySkin re-skins the parts with DaisyUI/Tailwind classes"):
    val c = host()
    val r = createRoot(c)
    r.render(SkinProvider(DaisySkin)(Empty()(button(id := "act", typ := "button", "Add"))))
    Scheduler.flushSync()
    assert(root(c).getAttribute("class").contains("flex flex-col items-center"))
    assert(part(c, "description").getAttribute("class").contains("text-base-content/60"))
    assert(part(c, "footer").getAttribute("class").contains("mt-2"))
    r.unmount()
