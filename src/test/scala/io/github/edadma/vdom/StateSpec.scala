package io.github.edadma.vdom

// Hooks and the scheduler: useState driving re-renders through events, the
// three-value (state, set, update) shape, and microtask batching collapsing
// several updates in one tick into a single commit.
class StateSpec extends DomSuite:

  test("useState drives DOM updates through events"):
    val c = host()
    val Counter = view {
      val (n, _, update) = useState(0)
      button(onClick := (_ => update(_ + 1)), s"n=$n")
    }
    render(Counter(), c)
    val btn = c.querySelector("button")
    assert(btn.textContent == "n=0")
    fireClick(btn)
    assert(btn.textContent == "n=1")
    fireClick(btn)
    assert(btn.textContent == "n=2")

  test("the setter replaces and the updater transforms"):
    val c = host()
    val Both = view {
      val (n, set, update) = useState(10)
      div(
        span(cls := "n", n),
        button(cls := "set", onClick := (_ => set(0)), "set"),
        button(cls := "inc", onClick := (_ => update(_ + 1)), "inc"),
      )
    }
    render(Both(), c)
    assert(c.querySelector("span.n").textContent == "10")
    fireClick(c.querySelector("button.inc"))
    assert(c.querySelector("span.n").textContent == "11")
    fireClick(c.querySelector("button.set"))
    assert(c.querySelector("span.n").textContent == "0")

  test("controlled input round-trips through useState"):
    val c = host()
    val Field = view {
      val (v, set, _) = useState("")
      div(
        input(value := v, onInput := (e => set(targetValue(e)))),
        span(cls := "echo", v),
      )
    }
    render(Field(), c)
    typeInto(c.querySelector("input"), "hello")
    assert(c.querySelector("span.echo").textContent == "hello")

  test("multiple updates in one tick collapse into a single commit"):
    val c = host()
    var renders = 0
    val Multi = view {
      val (n, _, update) = useState(0)
      renders += 1
      button(onClick := (_ => { update(_ + 1); update(_ + 1); update(_ + 1) }), s"n=$n")
    }
    render(Multi(), c)
    val rendersAfterMount = renders
    fireClick(c.querySelector("button"))
    assert(c.querySelector("button").textContent == "n=3")
    // Three updater calls, one re-render — not three.
    assert(renders == rendersAfterMount + 1)
