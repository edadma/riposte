package io.github.edadma.riposte

import org.scalajs.dom

// The builder DSL covers the everyday long tail of HTML — sectioning, tables,
// forms, media — plus the open-ended `aria-*` / `data-*` namespaces and the
// common event set. These render through the same `AttrKey` / `EventKey` /
// `h(tag)` machinery as the core tags, so a representative sample is enough to
// confirm the names resolve and reach the DOM as expected.
class DslSpec extends DomSuite:

  test("added element tags render with their DOM tag name"):
    val c = host()
    render(
      article(
        nav(a(href := "#", "home")),
        table(thead(tr(th("h"))), tbody(tr(td("c")))),
        figure(figcaption("cap")),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("article") != null)
    assert(c.querySelector("nav a").getAttribute("href") == "#")
    assert(c.querySelector("table thead th").textContent == "h")
    assert(c.querySelector("table tbody td").textContent == "c")
    assert(c.querySelector("figure figcaption").textContent == "cap")

  test("`mainTag` renders a <main> element (the name dodges Scala's `main`)"):
    val c = host()
    render(mainTag(h4("title")), c)
    Scheduler.flushSync()
    assert(c.querySelector("main h4").textContent == "title")

  test("string and int attributes are set on the element"):
    val c = host()
    render(input(typ := "number", min := 0, max := 10, step := 2), c)
    Scheduler.flushSync()
    val el = c.querySelector("input")
    assert(el.getAttribute("type") == "number")
    assert(el.getAttribute("min") == "0")
    assert(el.getAttribute("max") == "10")
    assert(el.getAttribute("step") == "2")

  test("a boolean attribute is present when true and absent when false"):
    val c = host()
    render(input(required := true, disabled := false), c)
    Scheduler.flushSync()
    val el = c.querySelector("input")
    assert(el.hasAttribute("required"))
    assert(!el.hasAttribute("disabled"))

  test("`open` toggles on a <details> across a re-render"):
    val c = host()
    val Disclosure = view {
      val (isOpen, _, update) = useState(false)
      details(
        open := isOpen,
        summary("more"),
        button(onClick := (_ => update(!_)), "toggle"),
      )
    }
    render(Disclosure(), c)
    Scheduler.flushSync()
    assert(!c.querySelector("details").hasAttribute("open"))
    fireClick(c.querySelector("button"))
    assert(c.querySelector("details").hasAttribute("open"))

  test("aria(...) and data(...) build aria-* and data-* attributes"):
    val c = host()
    render(div(aria("label") := "close", data("id") := "x1"), c)
    Scheduler.flushSync()
    val el = c.querySelector("div")
    assert(el.getAttribute("aria-label") == "close")
    assert(el.getAttribute("data-id") == "x1")

  test("an added event (dblclick) fires its handler"):
    val c = host()
    var hits = 0
    render(button(onDblClick := (_ => hits += 1), "go"), c)
    Scheduler.flushSync()
    c.querySelector("button").dispatchEvent(new dom.Event("dblclick"))
    Scheduler.flushSync()
    assert(hits == 1)
