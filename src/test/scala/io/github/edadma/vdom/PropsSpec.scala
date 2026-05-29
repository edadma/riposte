package io.github.edadma.vdom

// Components that take props: receiving them, re-rendering when a parent passes
// new ones, and — the subtle part — keeping their own hook state across prop
// changes (because the reconciler matches by component identity, not props).
class PropsSpec extends DomSuite:

  private case class CardProps(title: String, count: Int)

  private val Card = component[CardProps]("Card") { p =>
    div(cls := "card", span(cls := "title", p.title), span(cls := "count", p.count))
  }

  test("a child component receives its props"):
    val c = container()
    render(Card(CardProps("hello", 3)), c)
    assert(c.querySelector("span.title").textContent == "hello")
    assert(c.querySelector("span.count").textContent == "3")

  test("a child re-renders when the parent passes new props"):
    val c = container()
    val Parent = view("Parent") {
      val (n, _, update) = useState(0)
      div(
        Card(CardProps("n", n)),
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
