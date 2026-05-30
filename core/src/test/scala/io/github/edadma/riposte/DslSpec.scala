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

  // ARIA states are enumerated "true"/"false" strings — `:= false` must write
  // "false", not drop the attribute (absent ≠ "false" to a screen reader).

  test("an ARIA boolean state renders as the string \"true\"/\"false\", never absent"):
    val c = host()
    render(
      div(
        aria("expanded") := false,
        aria("hidden")   := true,
        aria("level")    := 2,        // numeric states still pass through
        aria("current")  := "page",   // token states still pass through
      ),
      c,
    )
    Scheduler.flushSync()
    val el = c.querySelector("div")
    assert(el.getAttribute("aria-expanded") == "false")
    assert(el.hasAttribute("aria-expanded")) // present, not dropped
    assert(el.getAttribute("aria-hidden") == "true")
    assert(el.getAttribute("aria-level") == "2")
    assert(el.getAttribute("aria-current") == "page")

  test("an ARIA boolean state stays present as it toggles across a re-render"):
    val c = host()
    val Toggle = view {
      val (open, _, update) = useState(false)
      button(aria("expanded") := open, onClick := (_ => update(!_)), "menu")
    }
    render(Toggle(), c)
    Scheduler.flushSync()
    assert(c.querySelector("button").getAttribute("aria-expanded") == "false")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("button").getAttribute("aria-expanded") == "true")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("button").getAttribute("aria-expanded") == "false")

  test("draggable is the enumerated \"true\"/\"false\", not presence"):
    val c = host()
    render(div(draggable := true), c)
    Scheduler.flushSync()
    assert(c.querySelector("div").getAttribute("draggable") == "true")

  test("a genuine HTML boolean attribute still uses presence semantics"):
    val c = host()
    render(input(required := true, disabled := false), c)
    Scheduler.flushSync()
    val el = c.querySelector("input")
    // Presence, not the string "true": the attribute exists but its value is "".
    assert(el.hasAttribute("required"))
    assert(el.getAttribute("required") == "")
    assert(!el.hasAttribute("disabled"))

  test("an added event (dblclick) fires its handler"):
    val c = host()
    var hits = 0
    render(button(onDblClick := (_ => hits += 1), "go"), c)
    Scheduler.flushSync()
    c.querySelector("button").dispatchEvent(new dom.Event("dblclick"))
    Scheduler.flushSync()
    assert(hits == 1)

  test("a Double attribute value renders as its string form"):
    val c = host()
    render(svg(circle(cx := 1.5, cy := 2.5, r := 0.25)), c)
    Scheduler.flushSync()
    val circ = c.querySelector("circle")
    assert(circ.getAttribute("cx") == "1.5")
    assert(circ.getAttribute("cy") == "2.5")
    assert(circ.getAttribute("r") == "0.25")

  // A `<select>`'s `value` is a DOM property that only "takes" once the matching
  // `<option>` exists, so the reconciler mounts children before applying props.

  test("a controlled <select> selects the right option on first mount"):
    val c = host()
    render(
      select(
        value := "b",
        option(value := "a", "A"),
        option(value := "b", "B"),
        option(value := "c", "C"),
      ),
      c,
    )
    Scheduler.flushSync()
    assert(c.querySelector("select").asInstanceOf[dom.html.Select].value == "b")

  test("a controlled <select> selects a value whose <option> is added in the same patch"):
    val c = host()
    val Sel = view {
      val (n, _, update) = useState(2)
      div(
        select(
          value := s"o${n - 1}",
          (0 until n).map(i => option(key := i, value := s"o$i", s"O$i")): Seq[VNode],
        ),
        button(onClick := (_ => update(_ + 1)), "more"),
      )
    }
    render(Sel(), c)
    Scheduler.flushSync()
    assert(c.querySelector("select").asInstanceOf[dom.html.Select].value == "o1")
    fireClick(c.querySelector("button"))
    // n is now 3 → value "o2", whose <option> is mounted in this very patch.
    assert(c.querySelector("select").asInstanceOf[dom.html.Select].value == "o2")

  // `defaultValue` / `defaultChecked` seed a field once, then leave it to the
  // DOM — the uncontrolled counterpart to `value` / `checked`.

  test("defaultValue seeds an input's initial value"):
    val c = host()
    render(input(cls := "f", defaultValue := "seed"), c)
    Scheduler.flushSync()
    assert(c.querySelector("input.f").asInstanceOf[dom.html.Input].value == "seed")

  test("defaultChecked seeds an uncontrolled checkbox"):
    val c = host()
    render(input(typ := "checkbox", defaultChecked := true), c)
    Scheduler.flushSync()
    assert(c.querySelector("input").asInstanceOf[dom.html.Input].checked)

  test("a defaultValue field is uncontrolled: a typed value survives a re-render"):
    val c = host()
    val Form = view {
      val (n, _, update) = useState(0)
      div(
        input(cls := "f", defaultValue := "seed"),
        button(onClick := (_ => update(_ + 1)), s"re$n"),
      )
    }
    render(Form(), c)
    Scheduler.flushSync()
    val inp = c.querySelector("input.f").asInstanceOf[dom.html.Input]
    assert(inp.value == "seed")
    inp.value = "typed"                  // the user edits; the DOM owns the value
    fireClick(c.querySelector("button")) // an unrelated re-render
    // Were the field controlled, this would snap back to "seed"; it must not.
    assert(inp.value == "typed")

  test("unsafeHtml sets the element's inner HTML verbatim"):
    val c = host()
    render(div(cls := "rich", unsafeHtml("<b>hi</b> <i>there</i>")), c)
    Scheduler.flushSync()
    val el = c.querySelector("div.rich")
    assert(el.innerHTML == "<b>hi</b> <i>there</i>")
    assert(el.querySelector("b").textContent == "hi")

  test("unsafeHtml updates when the html changes and clears when removed"):
    val c = host()
    val Rich = view {
      val (n, _, update) = useState(0)
      div(
        span(
          cls := "box",
          if n == 0 then unsafeHtml("<b>a</b>")
          else if n == 1 then unsafeHtml("<i>b</i>")
          else NoMod,
        ),
        button(onClick := (_ => update(_ + 1)), "next"),
      )
    }
    render(Rich(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.box").innerHTML == "<b>a</b>")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.box").innerHTML == "<i>b</i>")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.box").innerHTML == "")
