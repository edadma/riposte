package io.github.edadma.vdom

// Components that take props: positional multi-arg factories, named-tuple props,
// re-rendering when a parent passes new props, and — the subtle part — keeping
// their own hook state across prop changes (matched by component identity, not
// props).
class PropsSpec extends DomSuite:

  private val Card = component[String, Int]("Card") { (title, count) =>
    div(cls := "card", span(cls := "title", title), span(cls := "count", count))
  }

  test("a positional multi-arg child receives its props"):
    val c = container()
    render(Card("hello", 3), c)
    assert(c.querySelector("span.title").textContent == "hello")
    assert(c.querySelector("span.count").textContent == "3")

  test("a three-arg positional component receives all three props"):
    val c = container()
    val Row = component[String, Int, Boolean]("Row") { (label, n, on) =>
      div(span(cls := "l", label), span(cls := "n", n), span(cls := "on", on.toString))
    }
    render(Row("x", 5, true), c)
    assert(c.querySelector("span.l").textContent == "x")
    assert(c.querySelector("span.n").textContent == "5")
    assert(c.querySelector("span.on").textContent == "true")

  test("a named-tuple child receives its props by name"):
    val c = container()
    val Badge = component[(text: String, tone: String)]("Badge") { p =>
      div(cls := "badge", span(cls := "text", p.text), span(cls := "tone", p.tone))
    }
    render(Badge((text = "new", tone = "info")), c)
    assert(c.querySelector("span.text").textContent == "new")
    assert(c.querySelector("span.tone").textContent == "info")

  test("a child re-renders when the parent passes new props"):
    val c = container()
    val Parent = view("Parent") {
      val (n, _, update) = useState(0)
      div(
        Card("n", n),
        button(onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    render(Parent(), c)
    assert(c.querySelector("span.count").textContent == "0")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.count").textContent == "1")

  test("a child keeps its own hook state across prop changes"):
    val c = container()
    // Child takes a single Int prop and also owns local state.
    val Child = component[Int]("Child") { p =>
      val (local, _, bump) = useState(0)
      div(
        span(cls := "prop", p),
        span(cls := "local", local),
        button(cls := "bump-local", onClick := (_ => bump(_ + 1)), "local"),
      )
    }
    val Parent = view("Parent") {
      val (n, _, update) = useState(0)
      div(
        Child(n),
        button(cls := "bump-prop", onClick := (_ => update(_ + 1)), "prop"),
      )
    }
    render(Parent(), c)
    // Drive the child's own state up.
    fireClick(c.querySelector("button.bump-local"))
    assert(c.querySelector("span.local").textContent == "1")
    // Now change the prop from the parent — the child re-renders with the new
    // prop but its local state survives (same instance, matched by identity).
    fireClick(c.querySelector("button.bump-prop"))
    assert(c.querySelector("span.prop").textContent == "1")
    assert(c.querySelector("span.local").textContent == "1")
