package io.github.edadma.riposte

// Components that accept children (slots): a propless `container`, a `container`
// with props, children that update across re-renders, a container that wraps its
// children in its own state, and a container's own hook state surviving while the
// children passed to it change.
class ContainerSpec extends DomSuite:

  test("a container places the children passed to it"):
    val c = host()
    val Card = container { children =>
      div(cls := "card", children)
    }
    render(Card(h2(cls := "t", "Title"), p(cls := "b", "Body")), c)
    val card = c.querySelector("div.card")
    assert(card != null)
    assert(card.querySelector("h2.t").textContent == "Title")
    assert(card.querySelector("p.b").textContent == "Body")

  test("a container with no children renders just its wrapper"):
    val c = host()
    val Box = container(children => div(cls := "box", children))
    render(Box(), c)
    val box = c.querySelector("div.box")
    assert(box != null)
    assert(box.childElementCount == 0)

  test("a string passed as a child becomes a text node"):
    val c = host()
    val Note = container(children => p(cls := "note", children))
    render(Note("just text"), c)
    assert(c.querySelector("p.note").textContent == "just text")

  test("children update when the parent re-renders"):
    val c = host()
    val Card = container(children => div(cls := "card", children))
    val Parent = view {
      val (n, _, update) = useState(0)
      div(
        Card(span(cls := "n", n)),
        button(onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    render(Parent(), c)
    assert(c.querySelector("span.n").textContent == "0")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.n").textContent == "1")

  test("a container can wrap its children in its own state"):
    val c = host()
    // A collapsible that shows its children only when open — children flow in,
    // the container owns the open/closed state.
    val Collapsible = container { children =>
      val (open, _, update) = useState(false)
      div(
        button(cls := "toggle", onClick := (_ => update(o => !o)), "toggle"),
        when(open)(div(cls := "panel", children)),
      )
    }
    render(Collapsible(span(cls := "inner", "secret")), c)
    assert(c.querySelector("div.panel") == null) // closed → children hidden
    fireClick(c.querySelector("button.toggle"))
    assert(c.querySelector("div.panel") != null)
    assert(c.querySelector("span.inner").textContent == "secret")

  test("a container keeps its own hook state across child changes"):
    val c = host()
    val Counter = container { children =>
      val (n, _, update) = useState(0)
      div(
        span(cls := "count", n),
        div(cls := "slot", children),
        button(cls := "bump", onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    val Parent = view {
      val (label, _, update) = useState("a")
      div(
        Counter(span(cls := "child", label)),
        button(cls := "relabel", onClick := (_ => update(_ => "b")), "relabel"),
      )
    }
    render(Parent(), c)
    // Drive the container's own state up.
    fireClick(c.querySelector("button.bump"))
    assert(c.querySelector("span.count").textContent == "1")
    // Change the child passed in; the container re-renders with new children but
    // keeps its state (same component identity).
    fireClick(c.querySelector("button.relabel"))
    assert(c.querySelector("span.child").textContent == "b")
    assert(c.querySelector("span.count").textContent == "1")

  test("a container with props renders both the props and the children"):
    val c = host()
    val Panel = container[(title: String)] { (p, children) =>
      section(h2(cls := "title", p.title), div(cls := "body", children))
    }
    render(Panel((title = "Settings"))(span(cls := "a", "x"), span(cls := "b", "y")), c)
    assert(c.querySelector("h2.title").textContent == "Settings")
    val body = c.querySelector("div.body")
    assert(body.querySelector("span.a").textContent == "x")
    assert(body.querySelector("span.b").textContent == "y")
