package io.github.edadma.riposte.atoms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

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

  test("forget discards an atom's state, resetting it on the next read"):
    val s = new Store
    val a = atom(1)
    s.set(a, 5)
    assert(s.get(a) == 5)
    s.forget(a)
    assert(s.get(a) == 1) // rebuilt at its initial value

  test("forget is a no-op for an atom that was never read"):
    val s = new Store
    val a = atom(0)
    s.forget(a) // no exception
    assert(s.get(a) == 0)

  test("forget runs a mounted atom's cleanup"):
    val s        = new Store
    var cleanups = 0
    val a        = atom(0)
    onMount(a)(_ => Some(() => cleanups += 1))
    s.sub(a, () => ()) // mounts
    s.forget(a)
    assert(cleanups == 1)

  test("forget detaches the atom from its dependencies' dependents"):
    val s   = new Store
    val a   = atom(1)
    val dbl = atom(g => g(a) * 2)
    assert(s.get(dbl) == 2) // records the a -> dbl reverse edge
    s.forget(dbl)
    s.set(a, 7)             // a no longer knows about dbl
    assert(s.get(dbl) == 14) // dbl, rebuilt fresh, recomputes from the current a

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

  // --- Provider-scoped stores ----------------------------------------------

  test("the same atom holds independent values under two StoreProviders"):
    val c     = host()
    val s1    = new Store
    val s2    = new Store
    val count = atom(0)
    val Inc = view {
      val (n, set) = useAtom(count)
      button(onClick := (_ => set(n + 1)), n)
    }
    render(
      div(
        div(cls := "a", StoreProvider(s1)(Inc())),
        div(cls := "b", StoreProvider(s2)(Inc())),
      ),
      c,
    )
    Scheduler.flushSync()
    val a = c.querySelector("div.a button")
    val b = c.querySelector("div.b button")
    assert(a.textContent == "0")
    assert(b.textContent == "0")
    fireClick(a) // increments only s1's count
    assert(a.textContent == "1")
    assert(b.textContent == "0")
    assert(s1.get(count) == 1)
    assert(s2.get(count) == 0)

  test("without a provider the hooks fall back to Store.default"):
    val c     = host()
    val count = atom(0)
    val Show  = view {
      val v = useAtomValue(count)
      span(cls := "g", v)
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.g").textContent == "0")
    Store.default.set(count, 9)
    Scheduler.flushSync()
    assert(c.querySelector("span.g").textContent == "9")

  // --- utilities -----------------------------------------------------------

  test("selectAtom exposes a slice and notifies only when it changes"):
    val s     = new Store
    val pair  = atom((1, 2))
    val first = selectAtom(pair, _._1)
    var hits  = 0
    s.sub(first, () => hits += 1)
    assert(s.get(first) == 1)
    s.set(pair, (1, 9)) // first component unchanged → no notify
    assert(hits == 0)
    s.set(pair, (5, 9)) // first component changed → notify
    assert(hits == 1)
    assert(s.get(first) == 5)

  test("atomFamily memoises atoms by parameter"):
    val s        = new Store
    val itemAtom = atomFamily((id: Int) => atom(id * 10))
    assert(itemAtom(1) eq itemAtom(1))
    assert(!(itemAtom(1) eq itemAtom(2)))
    assert(s.get(itemAtom(1)) == 10)
    assert(s.get(itemAtom(2)) == 20)
    s.set(itemAtom(1), 99)
    assert(s.get(itemAtom(1)) == 99)
    assert(s.get(itemAtom(2)) == 20) // independent state per parameter

  test("an atomFamily is still a plain function of its parameter"):
    val fam: Int => Atom[Int] = atomFamily((id: Int) => atom(id))
    assert(fam(2) eq fam(2))

  test("atomFamily remove drops the memo so a fresh atom is built"):
    val fam = atomFamily((id: Int) => atom(id * 10))
    val a1  = fam(1)
    assert(fam.contains(1))
    fam.remove(1)
    assert(!fam.contains(1))
    assert(!(fam(1) eq a1)) // rebuilt, new identity

  test("atomFamily keys and clear track and drop the live set"):
    val fam = atomFamily((id: Int) => atom(id))
    fam(1); fam(2); fam(3)
    assert(fam.keys.toSet == Set(1, 2, 3))
    fam.clear()
    assert(fam.keys.isEmpty)

  test("onMount runs on the first listener and cleans up after the last"):
    val s        = new Store
    var mounts   = 0
    var cleanups = 0
    val a        = atom(0)
    onMount(a) { setSelf =>
      mounts += 1
      setSelf(42) // the hook may seed the atom
      Some(() => cleanups += 1)
    }
    assert(mounts == 0)
    val u1 = s.sub(a, () => ())
    assert(mounts == 1)
    assert(s.get(a) == 42) // setSelf wrote through
    val u2 = s.sub(a, () => ())
    assert(mounts == 1)   // a second listener does not re-mount
    u1()
    assert(cleanups == 0) // still observed
    u2()
    assert(cleanups == 1) // last listener gone → cleanup

  test("atomWithStorage loads from and writes back to localStorage"):
    val c = host()
    dom.window.localStorage.setItem("riposte-test-pref", "stored")
    val pref = atomWithStorage("riposte-test-pref", "default")
    val Show = view {
      val (v, setV) = useAtom(pref)
      div(span(cls := "v", v), button(onClick := (_ => setV("changed")), "set"))
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "stored") // onMount loaded it
    fireClick(c.querySelector("button"))
    assert(c.querySelector("span.v").textContent == "changed")
    assert(dom.window.localStorage.getItem("riposte-test-pref") == "changed") // persisted

  // --- async / loadable atoms ----------------------------------------------
  //
  // The parasitic EC runs a future's completion callback synchronously on the
  // thread that completes it, so these cases settle deterministically within a
  // flushSync — no real async waiting.

  private def loadableText(l: Loadable[Int]): String = l match
    case Loadable.Loading      => "loading"
    case Loadable.Data(n)      => s"data:$n"
    case Loadable.Errored(e)   => s"err:${e.getMessage}"

  test("a loadable atom resolves to Data when its future succeeds"):
    given ExecutionContext = ExecutionContext.parasitic
    val c    = host()
    val data = atomLoadable(Future.successful(42))
    val Show = view {
      val v = useAtomValue(data)
      span(cls := "v", loadableText(v))
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "data:42")

  test("a loadable atom stays Loading until its future completes"):
    given ExecutionContext = ExecutionContext.parasitic
    val c    = host()
    val p    = Promise[Int]()
    val data = atomLoadable(p.future)
    val Show = view {
      val v = useAtomValue(data)
      span(cls := "v", loadableText(v))
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "loading")
    p.success(7) // parasitic runs the completion callback now
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "data:7")

  test("a loadable atom resolves to Errored when its future fails"):
    given ExecutionContext = ExecutionContext.parasitic
    val c    = host()
    val data = atomLoadable(Future.failed[Int](new RuntimeException("boom")))
    val Show = view {
      val v = useAtomValue(data)
      span(cls := "v", loadableText(v))
    }
    render(Show(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.v").textContent == "err:boom")
