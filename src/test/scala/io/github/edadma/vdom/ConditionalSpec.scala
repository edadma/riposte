package io.github.edadma.vdom

// Conditional-render ergonomics: `when` / `unless`, and the `Option[VNode]`
// child conversion. The subtle guarantee is that a hidden branch leaves an empty
// placeholder in its slot, so toggling it does not disturb the DOM identity or
// state of the siblings around it.
class ConditionalSpec extends DomSuite:

  test("when(true) shows the node and when(false) shows nothing"):
    val c = host()
    render(div(when(true)(span(cls := "a", "yes")), when(false)(span(cls := "b", "no"))), c)
    assert(c.querySelector("span.a") != null)
    assert(c.querySelector("span.b") == null)

  test("unless is the negation of when"):
    val c = host()
    render(div(unless(false)(span(cls := "a", "yes")), unless(true)(span(cls := "b", "no"))), c)
    assert(c.querySelector("span.a") != null)
    assert(c.querySelector("span.b") == null)

  test("when's node is built only when the condition holds"):
    val c = host()
    var built = 0
    val Comp = view {
      val (show, _, update) = useState(false)
      div(
        when(show) { built += 1; span(cls := "x", "x") },
        button(onClick := (_ => update(s => !s)), "toggle"),
      )
    }
    render(Comp(), c)
    assert(built == 0) // hidden → by-name node never evaluated
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.x") != null)
    assert(built == 1) // shown → built exactly once

  test("toggling a when keeps surrounding siblings' DOM identity"):
    val c = host()
    val Comp = view {
      val (show, _, update) = useState(true)
      div(
        when(show)(span(cls := "opt", "shown")),
        input(cls := "keep"),
        button(onClick := (_ => update(s => !s)), "toggle"),
      )
    }
    render(Comp(), c)
    val keep = c.querySelector("input.keep")
    assert(c.querySelector("span.opt") != null)
    // Hide it: the placeholder takes the slot, so the input is patched in place,
    // not torn down and rebuilt.
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.opt") == null)
    assert(c.querySelector("input.keep") == keep)
    // Show it again: still the same sibling node.
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.opt") != null)
    assert(c.querySelector("input.keep") == keep)

  test("an Option[VNode] child renders Some and hides None"):
    val c = host()
    val some: Option[VNode] = Some(span(cls := "s", "here"))
    val none: Option[VNode] = None
    render(div(some, none), c)
    assert(c.querySelector("span.s") != null)
    assert(c.textContent == "here")

  test("toggling an Option child between Some and None keeps siblings stable"):
    val c = host()
    val Comp = view {
      val (n, _, update) = useState(0)
      val maybe: Option[VNode] = if n % 2 == 0 then Some(span(cls := "m", n)) else None
      div(
        maybe,
        input(cls := "keep"),
        button(onClick := (_ => update(_ + 1)), "next"),
      )
    }
    render(Comp(), c)
    val keep = c.querySelector("input.keep")
    assert(c.querySelector("span.m") != null) // n=0 → Some
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.m") == null) // n=1 → None
    assert(c.querySelector("input.keep") == keep)
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.m") != null) // n=2 → Some
    assert(c.querySelector("input.keep") == keep)
