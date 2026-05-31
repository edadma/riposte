package io.github.edadma.riposte

import scala.collection.mutable

// useDeferredValue / useDebouncedValue / useThrottledValue. The deferred case is
// driven purely by the effect queue; the timer-based two install a manual timer
// (the Timers seam) so "the interval elapsed" is a deterministic call, not a wait.
class UtilityHooksSpec extends DomSuite:

  // Swap in a manual timer for the body; `fire` runs all pending timer callbacks
  // (then flushes), simulating their delays elapsing. Restored afterwards.
  private def withManualTimers(body: (() => Unit) => Unit): Unit =
    val pending = mutable.ArrayBuffer.empty[() => Unit]
    val saved   = Timers.schedule
    Timers.schedule = (fn, _) => { pending += fn; () => { pending -= fn; () } }
    val fire: () => Unit = () =>
      val cbs = pending.toVector
      pending.clear()
      cbs.foreach(_())
      Scheduler.flushSync()
    try body(fire)
    finally Timers.schedule = saved

  test("useDeferredValue lags one commit, then catches up"):
    val c       = host()
    val renders = mutable.ArrayBuffer.empty[(String, String)]
    var setV: String => Unit = _ => ()
    val Probe = view {
      val (v, set, _) = useState("a")
      setV = set
      val d = useDeferredValue(v)
      renders += ((v, d))
      span(cls := "v", s"$v/$d")
    }
    render(Probe(), c)
    Scheduler.flushSync()
    assert(renders.contains(("a", "a")))
    setV("b")
    Scheduler.flushSync()
    assert(renders.contains(("b", "a")))       // urgent render: value moved, deferred lagged
    Scheduler.flushSync()                      // the deferred update lands on the next tick
    assert(renders.last == ("b", "b"))         // then the deferred caught up
    assert(c.querySelector("span.v").textContent == "b/b")

  test("useDebouncedValue updates only after the timer fires"):
    withManualTimers { fire =>
      val c = host()
      var setV: String => Unit = _ => ()
      val Probe = view {
        val (v, set, _) = useState("a")
        setV = set
        span(cls := "v", useDebouncedValue(v, 100))
      }
      render(Probe(), c)
      Scheduler.flushSync()
      def text = c.querySelector("span.v").textContent
      assert(text == "a")
      setV("b")
      Scheduler.flushSync()
      assert(text == "a") // still settling — the debounce timer is pending
      fire()
      assert(text == "b") // timer elapsed → debounced value updated
    }

  test("useThrottledValue emits leading immediately and trailing at the window's end"):
    withManualTimers { fire =>
      val c = host()
      var setV: String => Unit = _ => ()
      val Probe = view {
        val (v, set, _) = useState("a")
        setV = set
        span(cls := "v", useThrottledValue(v, 100))
      }
      render(Probe(), c)
      Scheduler.flushSync()
      def text = c.querySelector("span.v").textContent
      assert(text == "a")
      setV("b")
      Scheduler.flushSync()
      assert(text == "b") // leading edge: the first change shows at once
      setV("c")
      Scheduler.flushSync()
      assert(text == "b") // within the window: coalesced, not shown yet
      fire()
      assert(text == "c") // window closed: latest pending value emitted (trailing)
    }
