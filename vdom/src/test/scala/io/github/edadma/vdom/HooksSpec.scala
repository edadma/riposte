package io.github.edadma.vdom

import scala.collection.mutable

// The hooks runtime, exercised headlessly. Effects, memoization, reducers,
// context propagation, and external-store subscription are pure scheduling logic —
// the most logic-dense part of the library — so running them on the JVM with a
// deterministic scheduler is exactly where bugs are cheapest to catch.
class HooksSpec extends VdomSuite:

  test("useState evaluates its initializer exactly once"):
    var inits = 0
    val c = container()
    val Comp = view {
      val (n, _, update) = useState({ inits += 1; 0 })
      el("button", onClick(_ => update(_ + 1)))(s"$n")
    }
    createRoot(c).render(Comp())
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "2")
    assert(inits == 1)

  test("useEffect runs after commit and cleans up on unmount"):
    val c   = container()
    val log = mutable.ArrayBuffer.empty[String]
    val Comp = view {
      useEffect(() => { log += "run"; () => log += "cleanup" }, Array())
      el("div")("x")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(log.toList == List("run"))
    root.unmount()
    assert(log.toList == List("run", "cleanup"))

  test("useEffect re-runs only when its deps change"):
    val c    = container()
    val runs = mutable.ArrayBuffer.empty[Int]
    val Comp = view {
      val (n, setN, _) = useState(0)
      val (m, setM, _) = useState(0)
      useEffect(() => { runs += n; noCleanup }, Array(n))
      el("button", onClick(_ => setN(n + 1)), "on:bump" -> Handler(_ => setM(m + 1)))(s"$n-$m")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(runs.toList == List(0))
    host.fire(c.children.head, "bump"); Scheduler.flushSync() // m changes, n does not
    assert(runs.toList == List(0))
    host.fire(c.children.head, "click"); Scheduler.flushSync() // n changes
    assert(runs.toList == List(0, 1))

  test("useMemo recomputes only when its deps change"):
    val c        = container()
    var computes = 0
    val Comp = view {
      val (n, setN, _) = useState(0)
      val (m, setM, _) = useState(0)
      val v            = useMemo(() => { computes += 1; n }, Array(n))
      el("button", onClick(_ => setN(n + 1)), "on:bump" -> Handler(_ => setM(m + 1)))(s"$v")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(computes == 1)
    host.fire(c.children.head, "bump"); Scheduler.flushSync()
    assert(computes == 1)
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(computes == 2)

  test("useReducer dispatches actions through the reducer"):
    val c = container()
    val Comp = view {
      val (count, dispatch) = useReducer[Int, Int]((s, a) => s + a, 0)
      el("button", onClick(_ => dispatch(2)))(s"$count")
    }
    createRoot(c).render(Comp())
    assert(host.textOf(c) == "0")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "4")

  test("multiple updates in one tick collapse into a single commit"):
    val c       = container()
    var renders = 0
    val Comp = view {
      renders += 1
      val (n, _, update) = useState(0)
      el("button", onClick { _ => update(_ + 1); update(_ + 1); update(_ + 1) })(s"$n")
    }
    createRoot(c).render(Comp())
    val before = renders
    host.fire(c.children.head, "click")
    Scheduler.flushSync()
    assert(host.textOf(c) == "3")
    assert(renders == before + 1) // three updates, one re-render

  test("useContext reads the nearest provider and updates when its value changes"):
    val c     = container()
    val Theme = createContext("light")
    val Consumer = view {
      val theme = useContext(Theme)
      el("span")(theme)
    }
    val App = view {
      val (t, setT, _) = useState("dark")
      el("div", onClick(_ => setT("solar")))(Theme.provide(t, Consumer()))
    }
    createRoot(c).render(App())
    Scheduler.flushSync()
    assert(host.textOf(c) == "dark")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "solar")

  test("useSyncExternalStore re-renders when the store notifies a change"):
    val c    = container()
    var value = 0
    val subs  = mutable.ArrayBuffer.empty[() => Unit]
    val subscribe: (() => Unit) => (() => Unit) = cb =>
      subs += cb
      () => subs -= cb
    val Comp = view {
      val v = useSyncExternalStore(subscribe, () => value)
      el("span")(s"$v")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "0")
    value = 5
    subs.foreach(_())
    Scheduler.flushSync()
    assert(host.textOf(c) == "5")

  test("a layout effect that unconditionally sets state raises maximum update depth"):
    val c = container()
    val Comp = view {
      val (n, _, update) = useState(0)
      // deps null → runs after every commit; the unconditional set makes each commit
      // schedule the next, a loop with no fixed point.
      useLayoutEffect(() => { update(_ + 1); noCleanup }, null)
      el("span")(s"$n")
    }
    createRoot(c).render(Comp())
    val ex = intercept[IllegalStateException](Scheduler.flushSync())
    assert(ex.getMessage.contains("Maximum update depth exceeded"))
    // The scheduler is left consistent, not half-drained: a later well-behaved flush is a
    // no-op rather than re-throwing on stranded work.
    Scheduler.flushSync()

  test("transitionCurrent eases with easeOutCubic and clamps to the target"):
    val cell = new TransitionCell(startValue = 0.0, target = 10.0, startMs = 0.0, durationMs = 100, rafId = -1)
    assert(Hooks.transitionCurrent(cell, 0.0) == 0.0)
    assert(Hooks.transitionCurrent(cell, 100.0) == 10.0)
    assert(Hooks.transitionCurrent(cell, 250.0) == 10.0)
    assert(math.abs(Hooks.transitionCurrent(cell, 50.0) - 8.75) < 1e-9)
    assert(Hooks.transitionCurrent(new TransitionCell(0.0, 5.0, 0.0, 0, -1), 0.0) == 5.0)
