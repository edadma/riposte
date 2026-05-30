package io.github.edadma.riposte.atoms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Jotai-inspired atoms on top of riposte. Two layers: the Store (the reactive
// dependency graph — primitive/derived reads, precise propagation, change-gated
// notification) and the hooks (fine-grained re-renders, shared state, derived
// values updating in the DOM).
class AtomSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  private def fireClick(el: dom.Element): Unit =
    el.dispatchEvent(new dom.Event("click"))
    Scheduler.flushSync()

  // --- Store: the reactive graph -------------------------------------------

  test("a primitive atom reads its initial value and reflects writes"):
    val s = new Store
    val a = atom(1)
    assert(s.get(a) == 1)
    s.set(a, 2)
    assert(s.get(a) == 2)

  test("a derived atom recomputes from its dependencies"):
    val s   = new Store
    val a   = atom(1)
    val b   = atom(2)
    val sum = atom(g => g(a) + g(b))
    assert(s.get(sum) == 3)
    s.set(a, 10)
    assert(s.get(sum) == 12)
    s.set(b, 20)
    assert(s.get(sum) == 30)

  test("a watcher fires only when the watched value actually changes"):
    val s      = new Store
    val a      = atom(0)
    val parity = atom(g => g(a) % 2)
    var hits   = 0
    s.sub(parity, () => hits += 1)
    s.set(a, 2) // parity 0 → 0: unchanged
    assert(hits == 0)
    s.set(a, 3) // parity 0 → 1: changed
    assert(hits == 1)

  test("diamond dependencies compute correct values"):
    val s = new Store
    val a = atom(1)
    val d = atom(g => g(a) + 1)
    val e = atom(g => g(a) * 10)
    val f = atom(g => g(d) + g(e))
    assert(s.get(f) == (1 + 1) + (1 * 10)) // 12
    s.set(a, 2)
    assert(s.get(f) == (2 + 1) + (2 * 10)) // 23

  test("unsubscribing stops notifications"):
    val s    = new Store
    val a    = atom(0)
    var hits = 0
    val unsub = s.sub(a, () => hits += 1)
    s.set(a, 1)
    assert(hits == 1)
    unsub()
    s.set(a, 2)
    assert(hits == 1)

  // --- Hooks: components and atoms -----------------------------------------

  test("useAtom shares state across components"):
    val c     = host()
    val count = atom(0)
    val Show  = view {
      val v = useAtomValue(count)
      span(cls := "s", v)
    }
    val Inc = view {
      val (cur, set) = useAtom(count)
      button(onClick := (_ => set(cur + 1)), "inc")
    }
    render(div(Show(), Inc()), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.s").textContent == "0")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.s").textContent == "1")

  test("a component re-renders only for the atom it reads"):
    val c = host()
    val a = atom(0)
    val b = atom(0)
    var aRenders = 0
    val AReader = view {
      val v = useAtomValue(a)
      aRenders += 1
      span(cls := "a", v)
    }
    render(AReader(), c)
    Scheduler.flushSync()
    assert(aRenders == 1)
    // An unrelated atom changes — the component must not re-render.
    Store.default.set(b, 5)
    Scheduler.flushSync()
    assert(aRenders == 1)
    // Its own atom changes — now it re-renders.
    Store.default.set(a, 7)
    Scheduler.flushSync()
    assert(aRenders == 2)
    assert(c.querySelector("span.a").textContent == "7")

  test("a derived atom updates a component when its dependency changes"):
    val c       = host()
    val n       = atom(2)
    val doubled = atom(g => g(n) * 2)
    val Show = view {
      val v = useAtomValue(doubled)
      span(cls := "d", v)
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.d").textContent == "4")
    Store.default.set(n, 5)
    Scheduler.flushSync()
    assert(c.querySelector("span.d").textContent == "10")

  test("a write that leaves a derived value unchanged does not re-render its reader"):
    val c       = host()
    val n       = atom(0)
    val isEven  = atom(g => g(n) % 2 == 0)
    var renders = 0
    val Show = view {
      val e = useAtomValue(isEven)
      renders += 1
      span(cls := "e", e.toString)
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(renders == 1)
    assert(c.querySelector("span.e").textContent == "true")
    // 0 → 2: still even, derived value unchanged → no re-render.
    Store.default.set(n, 2)
    Scheduler.flushSync()
    assert(renders == 1)
    // 2 → 3: now odd → re-render.
    Store.default.set(n, 3)
    Scheduler.flushSync()
    assert(renders == 2)
    assert(c.querySelector("span.e").textContent == "false")

  // --- Writable-derived and action atoms -----------------------------------

  test("a writable-derived atom reads derived and writes through to a primitive"):
    val s    = new Store
    val cel  = atom(20)
    val fahr = atom[Int, Int](
      read = g => g(cel) * 9 / 5 + 32,
      write = (_, set, f) => set(cel, (f - 32) * 5 / 9),
    )
    assert(s.get(fahr) == 68)
    s.set(fahr, 212) // writes back to celsius
    assert(s.get(cel) == 100)
    assert(s.get(fahr) == 212)

  test("an action atom dispatches a write that mutates other atoms"):
    val s   = new Store
    val n   = atom(0)
    val inc = action[Int]((g, set, by) => set(n, g(n) + by))
    s.set(inc, 5)
    assert(s.get(n) == 5)
    s.set(inc, 3)
    assert(s.get(n) == 8)

  test("useAtom on a writable-derived atom dispatches the write and the reader updates"):
    val c    = host()
    val cel  = atom(0)
    val fahr = atom[Int, Int](
      read = g => g(cel) * 9 / 5 + 32,
      write = (_, set, f) => set(cel, (f - 32) * 5 / 9),
    )
    val Show = view {
      val (f, setF) = useAtom(fahr)
      div(
        span(cls := "f", f),
        button(onClick := (_ => setF(212)), "boil"),
      )
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.f").textContent == "32")
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.f").textContent == "212")
