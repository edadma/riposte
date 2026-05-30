package io.github.edadma.riposte

import org.scalajs.dom

// Binding an element to its live DOM node through `ref`: object refs (a useRef
// box), callback refs, clearing on unmount, surviving unrelated re-renders, and
// re-pointing when the bound handle changes or the element is removed.
class RefSpec extends DomSuite:

  test("an object ref points at the live node after mount"):
    val c = host()
    val r = useRefTop[dom.html.Input | Null](null)
    val Comp = view {
      // the same box each render, supplied by the enclosing test
      input(ref := r)
    }
    // Drive through a real component so the box is read like application code.
    render(Comp(), c)
    assert(r.current != null)
    assert(r.current == c.querySelector("input"))

  test("an object ref is cleared to null on unmount"):
    val c = host()
    val r = useRefTop[dom.html.Input | Null](null)
    val Comp = view(input(ref := r))
    val root = createRoot(c)
    root.render(Comp())
    assert(r.current != null)
    root.unmount()
    assert(r.current == null)

  test("a callback ref fires with the node on mount and null on unmount"):
    val c = host()
    var attached: dom.Element | Null = null
    var detachedCalls                = 0
    val cb: (dom.Element | Null) => Unit = n =>
      if n != null then attached = n else detachedCalls += 1
    val Comp = view(div(cls := "boxed", input(ref := cb)))
    val root = createRoot(c)
    root.render(Comp())
    assert(attached != null)
    assert(attached == c.querySelector("input"))
    assert(detachedCalls == 0)
    root.unmount()
    assert(detachedCalls == 1)

  test("a stable ref is not re-bound across an unrelated re-render"):
    val c = host()
    var binds = 0
    // A hoisted callback — the SAME function reference every render — so the
    // structural equality check sees no change and skips re-binding.
    val cb: (dom.Element | Null) => Unit = n => if n != null then binds += 1
    val Comp = view {
      val (n, _, update) = useState(0)
      div(
        input(ref := cb),
        span(cls := "n", n),
        button(onClick := (_ => update(_ + 1)), "bump"),
      )
    }
    render(Comp(), c)
    assert(binds == 1)
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.n").textContent == "1") // re-rendered
    assert(binds == 1)                                    // ref untouched

  test("the handle moves when the bound ref changes across a patch"):
    val c = host()
    val r1 = useRefTop[dom.html.Element | Null](null)
    val r2 = useRefTop[dom.html.Element | Null](null)
    val Comp = view {
      val (flag, _, update) = useState(false)
      div(
        input(ref := (if flag then r2 else r1)),
        button(onClick := (_ => update(_ => true)), "swap"),
      )
    }
    render(Comp(), c)
    val node = c.querySelector("input")
    assert(r1.current == node)
    assert(r2.current == null)
    fireClick(c.querySelector("button"))
    // Same element (same tag → same node), but the handle has moved.
    assert(c.querySelector("input") == node)
    assert(r1.current == null)
    assert(r2.current == node)

  test("a ref is cleared when its element is conditionally removed"):
    val c = host()
    val r = useRefTop[dom.html.Input | Null](null)
    val Comp = view {
      val (show, _, update) = useState(true)
      div(
        if show then input(ref := r) else empty,
        button(onClick := (_ => update(s => !s)), "toggle"),
      )
    }
    render(Comp(), c)
    assert(r.current != null)
    // Toggle the element out: it is unmounted, so the ref clears.
    fireClick(c.querySelector("button"))
    assert(c.querySelector("input") == null)
    assert(r.current == null)
    // Toggle it back: a fresh node is bound.
    fireClick(c.querySelector("button"))
    assert(r.current != null)
    assert(r.current == c.querySelector("input"))

  // A standalone Ref box, mirroring what `useRef` hands a component, but usable
  // from test scope where there is no ambient Hooks. It is the same `Ref[T]`
  // type, so `ref := _` binds it exactly as a real component's ref would.
  private def useRefTop[T](initial: T): Ref[T] = new Ref[T](initial)
