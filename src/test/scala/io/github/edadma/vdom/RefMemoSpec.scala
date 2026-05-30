package io.github.edadma.vdom

import scala.collection.mutable.ArrayBuffer

// useRef, useMemo / useCallback, useReducer, and useId.
class RefMemoSpec extends DomSuite:

  test("useRef persists across renders and writes don't re-render"):
    val c = container()
    val Comp = view {
      val ref            = useRef(0)
      val (_, _, update) = useState(0)
      div(
        span(cls := "ref", ref.current),
        button(cls := "bump", onClick := (_ => ref.current += 1), "bump"),
        button(cls := "rerender", onClick := (_ => update(_ + 1)), "rerender"),
      )
    }
    render(Comp(), c)
    assert(c.querySelector("span.ref").textContent == "0")
    // Mutating the ref does not schedule a render, so the DOM stays put…
    fireClick(c.querySelector("button.bump"))
    assert(c.querySelector("span.ref").textContent == "0")
    // …until an actual state change re-renders and reads the persisted ref.
    fireClick(c.querySelector("button.rerender"))
    assert(c.querySelector("span.ref").textContent == "1")

  test("useMemo recomputes only when its deps change"):
    val c = container()
    var computes = 0
    val Comp = view {
      val (a, _, updateA) = useState(0)
      val (_, _, updateB) = useState(0)
      val doubled = useMemo(() => { computes += 1; a * 2 }, Array(a))
      div(
        span(cls := "d", doubled),
        button(cls := "a", onClick := (_ => updateA(_ + 1)), "a"),
        button(cls := "b", onClick := (_ => updateB(_ + 1)), "b"),
      )
    }
    render(Comp(), c)
    assert(computes == 1)
    assert(c.querySelector("span.d").textContent == "0")
    // Bumping b re-renders but leaves dep `a` unchanged — no recompute.
    fireClick(c.querySelector("button.b"))
    assert(computes == 1)
    // Bumping a changes the dep — recompute.
    fireClick(c.querySelector("button.a"))
    assert(computes == 2)
    assert(c.querySelector("span.d").textContent == "2")

  test("useReducer dispatches actions through the reducer"):
    val c = container()
    val reducer: (Int, String) => Int = (s, a) =>
      a match
        case "inc" => s + 1
        case "dec" => s - 1
        case _     => s
    val Counter = view {
      val (n, dispatch) = useReducer(reducer, 0)
      div(
        span(cls := "n", n),
        button(cls := "inc", onClick := (_ => dispatch("inc")), "+"),
        button(cls := "dec", onClick := (_ => dispatch("dec")), "−"),
      )
    }
    render(Counter(), c)
    assert(c.querySelector("span.n").textContent == "0")
    fireClick(c.querySelector("button.inc"))
    fireClick(c.querySelector("button.inc"))
    assert(c.querySelector("span.n").textContent == "2")
    fireClick(c.querySelector("button.dec"))
    assert(c.querySelector("span.n").textContent == "1")

  test("useId is stable across renders"):
    val c        = container()
    val captured = ArrayBuffer.empty[String]
    val Comp = view {
      val (n, _, update) = useState(0)
      captured += useId()
      button(onClick := (_ => update(_ + 1)), s"$n")
    }
    render(Comp(), c)
    fireClick(c.querySelector("button"))
    assert(captured.length >= 2)
    assert(captured.distinct.length == 1)

  test("useId is unique across component instances"):
    val c   = container()
    val ids = ArrayBuffer.empty[String]
    val Comp = view {
      ids += useId()
      div()
    }
    render(div(Comp(), Comp()), c)
    assert(ids.length == 2)
    assert(ids.distinct.length == 2)
