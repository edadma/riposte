package io.github.edadma.vdom

// memo: bailing out of parent-driven re-renders when props are unchanged, while
// still re-rendering on prop changes and own state changes — and still updating
// when a consumed context changes, even from behind a bailed-out memo boundary.
class MemoSpec extends DomSuite:

  test("a memoized child skips re-render when its props are unchanged"):
    val c = host()
    var childRenders = 0
    val Child = memo(component[String] { label =>
      childRenders += 1
      span(cls := "c", label)
    })
    val Parent = view {
      val (n, _, update) = useState(0)
      div(
        Child("fixed"), // props never change
        span(cls := "n", n),
        button(onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    render(Parent(), c)
    assert(childRenders == 1)
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.n").textContent == "1") // parent did re-render
    assert(childRenders == 1)                            // memoized child did not

  test("a memoized multi-arg child bails when its positional args are unchanged"):
    val c = host()
    var childRenders = 0
    val Child = memo(component[String, Int] { (label, value) =>
      childRenders += 1
      span(cls := "c", s"$label$value")
    })
    val Parent = view {
      val (n, _, update) = useState(0)
      div(
        Child("fixed", 7), // args never change
        span(cls := "n", n),
        button(onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    render(Parent(), c)
    assert(childRenders == 1)
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.n").textContent == "1")
    assert(childRenders == 1) // tuple ("fixed", 7) compared equal → bailed

  test("a memoized child re-renders when its props change"):
    val c = host()
    var childRenders = 0
    val Child = memo(component[Int] { v =>
      childRenders += 1
      span(cls := "c", v)
    })
    val Parent = view {
      val (n, _, update) = useState(0)
      div(Child(n), button(onClick := (_ => update(_ + 1)), "bump"))
    }
    render(Parent(), c)
    assert(childRenders == 1)
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.c").textContent == "1")
    assert(childRenders == 2)

  test("a memoized component still re-renders on its own state change"):
    val c = host()
    val Comp = memo(view {
      val (n, _, update) = useState(0)
      button(onClick := (_ => update(_ + 1)), s"$n")
    })
    render(Comp(), c)
    assert(c.querySelector("button").textContent == "0")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("button").textContent == "1")

  test("a consumer below a bailed-out memo still sees context changes"):
    val c = host()
    val Theme = createContext("light")
    var midRenders = 0
    // Leaf consumes the context; Mid is memoized and does NOT consume it.
    val Leaf = view {
      val theme = useContext(Theme)
      span(cls := "t", theme)
    }
    val Mid = memo(view {
      midRenders += 1
      div(cls := "mid", Leaf())
    })
    val root = createRoot(c)
    root.render(Theme.provide("dark", Mid()))
    assert(c.querySelector("span.t").textContent == "dark")
    assert(midRenders == 1)

    // Change only the provided value. Mid's props (Unit) are unchanged, so it
    // bails — but Leaf is subscribed to Theme and updates through that path.
    root.render(Theme.provide("solarized", Mid()))
    Scheduler.flushSync()
    assert(midRenders == 1)                                       // Mid bailed
    assert(c.querySelector("span.t").textContent == "solarized")  // Leaf updated
