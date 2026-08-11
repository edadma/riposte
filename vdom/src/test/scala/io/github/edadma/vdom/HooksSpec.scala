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

  // --- context reaching past a memo bailout ---------------------------------
  //
  // A parent-driven patch re-renders consumers top-down, so these only prove
  // anything with a memoized component in the way: its bailout cuts the cascade,
  // and the consumer below can be woken only by the provider's subscriber walk.

  test("a context change reaches a consumer inside a portal behind a memoized ancestor"):
    val c      = container()
    val target = container()
    val Theme  = createContext("light")
    val Consumer = view {
      val theme = useContext(Theme)
      el("span")(theme)
    }
    // Props are Unit, so they are always equal and this always bails out.
    val Shell = memo(view(el("div")(VPortal(target, Consumer()))))
    val App = view {
      val (t, setT, _) = useState("dark")
      el("div", onClick(_ => setT("solar")))(Theme.provide(t, Shell()))
    }
    createRoot(c).render(App())
    Scheduler.flushSync()
    assert(host.textOf(target) == "dark")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(target) == "solar")

  test("a context change reaches a consumer inside an error boundary behind a memoized ancestor"):
    val c     = container()
    val Theme = createContext("light")
    val Consumer = view {
      val theme = useContext(Theme)
      el("span")(theme)
    }
    val Shell = memo(view(el("div")(VErrorBoundary(e => el("p")("failed"), Consumer()))))
    val App = view {
      val (t, setT, _) = useState("dark")
      el("div", onClick(_ => setT("solar")))(Theme.provide(t, Shell()))
    }
    createRoot(c).render(App())
    Scheduler.flushSync()
    assert(host.textOf(c) == "dark")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "solar")

  test("a provider value change renders each consumer exactly once"):
    val c       = container()
    val Theme   = createContext("light")
    var renders = 0
    val Consumer = view {
      renders += 1
      val theme = useContext(Theme)
      el("span")(theme)
    }
    val App = view {
      val (t, setT, _) = useState("dark")
      el("div", onClick(_ => setT("solar")))(Theme.provide(t, Consumer()))
    }
    createRoot(c).render(App())
    Scheduler.flushSync()
    val before = renders
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "solar")
    // The top-down patch renders it; the subscriber walk must not then enqueue a
    // second, output-identical render on the next flush.
    assert(renders == before + 1)

  test("a nested provider shields its subtree from the outer provider's change"):
    val c       = container()
    val Theme   = createContext("light")
    var renders = 0
    val Consumer = view {
      renders += 1
      val theme = useContext(Theme)
      el("span")(theme)
    }
    // Memoized, so the only way the inner consumer could re-render is the
    // subscriber walk — which must stop at the shadowing provider.
    val Shell = memo(view(Theme.provide("fixed", Consumer())))
    val App = view {
      val (t, setT, _) = useState("dark")
      el("div", onClick(_ => setT("solar")))(Theme.provide(t, Shell()))
    }
    createRoot(c).render(App())
    Scheduler.flushSync()
    assert(host.textOf(c) == "fixed")
    val before = renders
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "fixed")
    assert(renders == before)

  // --- useId ------------------------------------------------------------------

  test("useId is stable across renders and unique between components"):
    val c    = container()
    val seen = mutable.ArrayBuffer.empty[String]
    val Comp = view {
      val (n, _, update) = useState(0)
      val id             = useId()
      seen += id
      el("button", onClick(_ => update(_ + 1)))(s"$id-$n")
    }
    createRoot(c).render(el("div")(Comp(), Comp()))
    val firstTwo = seen.toList
    assert(firstTwo.length == 2)
    assert(firstTwo(0) != firstTwo(1))                 // two instances, two ids
    assert(firstTwo.forall(_.startsWith("vdom-")))     // no host's branding in the core
    host.fire(c.children.head.asInstanceOf[TestElement].children.head, "click")
    Scheduler.flushSync()
    assert(seen.toList == firstTwo :+ firstTwo(0))     // the same id on re-render

  // --- effect cleanup ordering -------------------------------------------------

  test("an effect's cleanup runs before the effect re-runs"):
    val c   = container()
    val log = mutable.ArrayBuffer.empty[String]
    val Comp = view {
      val (n, _, update) = useState(0)
      useEffect(() => { log += s"run$n"; () => log += s"cleanup$n" }, Array(n))
      el("button", onClick(_ => update(_ + 1)))(s"$n")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(log.toList == List("run0"))
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(log.toList == List("run0", "cleanup0", "run1"))
    root.unmount()
    assert(log.toList == List("run0", "cleanup0", "run1", "cleanup1"))

  test("useSyncExternalStore unsubscribes when the component unmounts"):
    val c    = container()
    val subs = mutable.ArrayBuffer.empty[() => Unit]
    val subscribe: (() => Unit) => (() => Unit) = cb =>
      subs += cb
      () => subs -= cb
    val Comp = view {
      val v = useSyncExternalStore(subscribe, () => 0)
      el("span")(s"$v")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(subs.length == 1)
    root.unmount()
    assert(subs.isEmpty)

  // --- deferred / debounced / throttled ---------------------------------------

  test("useDeferredValue lags one commit behind and then catches up"):
    val c    = container()
    val Comp = view {
      val (n, _, update) = useState(0)
      val slow           = useDeferredValue(n)
      el("button", onClick(_ => update(_ + 1)))(s"$n:$slow")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "0:0")
    // The urgent value commits at once; the deferred copy is only written by a
    // passive effect, whose own re-render lands in the following flush — so one
    // commit shows the new value beside the old deferred one.
    host.fire(c.children.head, "click")
    Scheduler.flushSync()
    assert(host.textOf(c) == "1:0")
    Scheduler.flushSync()
    assert(host.textOf(c) == "1:1")

  test("useDebouncedValue emits only the last value of a burst"):
    val c      = container()
    val timers = manualTimers()
    val Comp = view {
      val (n, _, update) = useState(0)
      val settled        = useDebouncedValue(n, 200)
      el("button", onClick(_ => update(_ + 1)))(s"$n:$settled")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(timers.delays == List(200))
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "2:0")  // still the initial value while the burst runs
    assert(timers.count == 1)        // each change cancelled the previous timer
    timers.fireAll()
    Scheduler.flushSync()
    assert(host.textOf(c) == "2:2")  // the final value of the burst, not the ones between

  test("useDebouncedValue cancels its pending timer on unmount"):
    val c      = container()
    val timers = manualTimers()
    val Comp = view {
      val settled = useDebouncedValue(1, 200)
      el("span")(s"$settled")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    assert(timers.count == 1)
    root.unmount()
    assert(timers.count == 0)

  test("useThrottledValue reflects the leading edge at once and coalesces the rest"):
    val c      = container()
    val timers = manualTimers()
    val Comp = view {
      val (n, _, update) = useState(0)
      val throttled      = useThrottledValue(n, 100)
      el("button", onClick(_ => update(_ + 1)))(s"$n:$throttled")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "0:0")
    assert(timers.count == 0) // the initial value is already shown; no window opened
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "1:1") // leading edge, in the same flush
    assert(timers.count == 1)       // the cooling window is open
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "3:1") // coalesced while cooling
    timers.fireAll(); Scheduler.flushSync()
    assert(host.textOf(c) == "3:3") // trailing edge emits the latest
    assert(timers.count == 1)       // and opens a fresh window
    timers.fireAll(); Scheduler.flushSync()
    assert(timers.count == 0)       // an idle window closes instead of respawning

  // --- transition ---------------------------------------------------------------

  test("useTransition eases toward the target across frames and stops when settled"):
    val c      = container()
    val frames = manualFrames()
    val Comp = view {
      val v = useTransition(100.0, 100)
      el("span")(f"$v%.1f")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "100.0") // first render establishes the cell at the target
    assert(frames.count == 0)         // nothing in flight, so no frame is held open

  test("a transition restarted toward a new target requests frames until it settles"):
    val c      = container()
    val frames = manualFrames()
    val Comp = view {
      val (target, setTarget, _) = useState(0.0)
      val v                      = useTransition(target, 100)
      el("button", onClick(_ => setTarget(10.0)))(f"$v%.2f")
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "0.00")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(host.textOf(c) == "0.00") // t=0 into the new transition
    assert(frames.count == 1)        // in flight, so a frame is pending
    frames.now = 50.0
    frames.fireAll(); Scheduler.flushSync()
    assert(host.textOf(c) == "8.75") // easeOutCubic at the halfway point
    assert(frames.count == 1)
    frames.now = 100.0
    frames.fireAll(); Scheduler.flushSync()
    assert(host.textOf(c) == "10.00")
    assert(frames.count == 0)        // settled: the frame loop is not held open

  test("a transition still in flight at unmount cancels its pending frame"):
    val c      = container()
    val frames = manualFrames()
    val Comp = view {
      val (target, setTarget, _) = useState(0.0)
      val v                      = useTransition(target, 100)
      el("button", onClick(_ => setTarget(10.0)))(f"$v%.2f")
    }
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(frames.count == 1)
    root.unmount()
    assert(frames.count == 0)

  // --- presence -------------------------------------------------------------------

  test("usePresence runs Enter, Open, Exit and only then unmounts"):
    val c      = container()
    val frames = manualFrames()
    val timers = manualTimers()
    val Comp = view {
      val (open, setOpen, _) = useState(true)
      val p                  = usePresence(open, exitMs = 200)
      el("div", onClick(_ => setOpen(false)))(
        if p.mounted then el("span", attrib("data-state", p.phase.token))("body") else VEmpty,
      )
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    def state = c.children.head.asInstanceOf[TestElement].children.head match
      case e: TestElement => e.attributes.getOrElse("data-state", "-")
      case _              => "-"
    assert(state == "enter")
    frames.fireAll(); Scheduler.flushSync()
    assert(state == "open")
    host.fire(c.children.head, "click"); Scheduler.flushSync()
    assert(state == "exit")            // still mounted, so the close animation can play
    assert(timers.delays == List(200))
    timers.fireAll(); Scheduler.flushSync()
    assert(host.textOf(c) == "")       // and only now is it gone

  test("re-opening mid-exit cancels the pending unmount and re-enters"):
    val c      = container()
    val frames = manualFrames()
    val timers = manualTimers()
    val Comp = view {
      val (open, setOpen, _) = useState(true)
      val p                  = usePresence(open, exitMs = 200)
      el("div", onClick(_ => setOpen(!open)))(
        if p.mounted then el("span", attrib("data-state", p.phase.token))("body") else VEmpty,
      )
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    frames.fireAll(); Scheduler.flushSync()
    host.fire(c.children.head, "click"); Scheduler.flushSync() // close
    assert(timers.count == 1)
    host.fire(c.children.head, "click"); Scheduler.flushSync() // re-open before the delay elapses
    assert(timers.count == 0)          // the unmount timer really was cancelled
    def state = c.children.head.asInstanceOf[TestElement].children.head.asInstanceOf[TestElement]
      .attributes.getOrElse("data-state", "-")
    assert(state == "enter")
    frames.fireAll(); Scheduler.flushSync()
    assert(state == "open")

  test("usePresence starting closed mounts nothing and schedules nothing"):
    val c      = container()
    val frames = manualFrames()
    val timers = manualTimers()
    val Comp = view {
      val p = usePresence(open = false, exitMs = 200)
      if p.mounted then el("span")("body") else VEmpty
    }
    createRoot(c).render(Comp())
    Scheduler.flushSync()
    assert(host.textOf(c) == "")
    assert(timers.count == 0 && frames.count == 0)

  // --- imperative handle ------------------------------------------------------------

  test("useImperativeHandle fills the parent's ref and clears it on unmount"):
    val c    = container()
    val box  = new Ref[(() => Int) | Null](null)
    val Child = component[Ref[(() => Int) | Null]] { ref =>
      useImperativeHandle(ref, () => () => 42, Array())
      el("span")("child")
    }
    val root = createRoot(c)
    root.render(Child(box))
    Scheduler.flushSync()
    assert(box.current != null)
    assert(box.current.asInstanceOf[() => Int]() == 42)
    root.unmount()
    assert(box.current == null)

  test("transitionCurrent eases with easeOutCubic and clamps to the target"):
    val cell = new TransitionCell(startValue = 0.0, target = 10.0, startMs = 0.0, durationMs = 100, rafId = -1)
    assert(Hooks.transitionCurrent(cell, 0.0) == 0.0)
    assert(Hooks.transitionCurrent(cell, 100.0) == 10.0)
    assert(Hooks.transitionCurrent(cell, 250.0) == 10.0)
    assert(math.abs(Hooks.transitionCurrent(cell, 50.0) - 8.75) < 1e-9)
    assert(Hooks.transitionCurrent(new TransitionCell(0.0, 5.0, 0.0, 0, -1), 0.0) == 5.0)
