package io.github.edadma.riposte

import scala.collection.mutable

// useTransition: the easeOutCubic math, and the frame-driven animation loop —
// easing toward a new target across frames, settling exactly on arrival, and
// cancelling a pending frame on unmount. The animation clock and frame scheduler
// are swapped for deterministic fakes via the `Transition` seam.
class TransitionSpec extends DomSuite:

  test("transitionCurrent eases from start to target with easeOutCubic"):
    val cell = new TransitionCell(startValue = 0.0, target = 10.0, startMs = 0.0, durationMs = 100, rafId = -1)
    assert(Hooks.transitionCurrent(cell, 0.0) == 0.0)   // raw 0 → start
    assert(Hooks.transitionCurrent(cell, 100.0) == 10.0) // raw 1 → target
    assert(Hooks.transitionCurrent(cell, 250.0) == 10.0) // past the end clamps to target
    // easeOutCubic(0.5) = 1 - (1-0.5)^3 = 0.875 → 8.75
    assert(math.abs(Hooks.transitionCurrent(cell, 50.0) - 8.75) < 1e-9)
    // A non-positive duration jumps straight to the target.
    val instant = new TransitionCell(0.0, 5.0, 0.0, 0, -1)
    assert(Hooks.transitionCurrent(instant, 0.0) == 5.0)

  test("useTransition eases toward a new target across frames, then settles"):
    val c = host()
    withFakeFrames { (setClock, frames) =>
      var observed = -1.0
      val Comp = view {
        val (tgt, setTgt, _) = useState(0.0)
        val v = useTransition(tgt, 100)
        observed = v
        button(onClick := (_ => setTgt(100.0)), "go")
      }
      render(Comp(), c)
      Scheduler.flushSync()
      assert(observed == 0.0)    // initial target — no animation yet
      assert(frames.isEmpty)

      fireClick(c.querySelector("button")) // target 0 → 100, transition starts
      assert(observed == 0.0)    // at clock 0 the eased value is still the start
      assert(frames.length == 1) // one frame requested

      setClock(50.0)
      frames.dequeue()()         // fire the frame → schedules a re-render
      Scheduler.flushSync()
      assert(math.abs(observed - 87.5) < 1e-9) // easeOutCubic(0.5) → 87.5
      assert(frames.length == 1)               // still animating → another frame

      setClock(100.0)
      frames.dequeue()()
      Scheduler.flushSync()
      assert(observed == 100.0)  // reached the target
      assert(frames.isEmpty)     // settled → no further frames requested
    }

  test("useTransition cancels a pending frame on unmount"):
    val c = host()
    var cancelled = -1
    val savedNow    = Transition.now
    val savedReq    = Transition.requestFrame
    val savedCancel = Transition.cancelFrame
    try
      var clock = 0.0
      val frames = mutable.Queue.empty[() => Unit]
      Transition.now          = () => clock
      Transition.requestFrame = cb => { frames += cb; 99 } // fixed handle
      Transition.cancelFrame  = id => cancelled = id

      val Comp = view {
        val (tgt, setTgt, _) = useState(0.0)
        val _ = useTransition(tgt, 100)
        button(onClick := (_ => setTgt(100.0)), "go")
      }
      val root = createRoot(c)
      root.render(Comp())
      Scheduler.flushSync()
      fireClick(c.querySelector("button")) // starts the transition → frame 99 pending
      assert(frames.length == 1)
      root.unmount()
      assert(cancelled == 99) // the in-flight frame was cancelled
    finally
      Transition.now          = savedNow
      Transition.requestFrame = savedReq
      Transition.cancelFrame  = savedCancel

  // Install a deterministic clock and a manual frame queue for the duration of
  // `body`, restoring the real ones afterwards. `setClock` advances time; pumping
  // a queued thunk simulates an animation frame firing.
  private def withFakeFrames(body: (Double => Unit, mutable.Queue[() => Unit]) => Unit): Unit =
    val savedNow    = Transition.now
    val savedReq    = Transition.requestFrame
    val savedCancel = Transition.cancelFrame
    try
      var clock = 0.0
      val frames = mutable.Queue.empty[() => Unit]
      Transition.now          = () => clock
      Transition.requestFrame = cb => { frames += cb; frames.length }
      Transition.cancelFrame  = _ => ()
      body(v => clock = v, frames)
    finally
      Transition.now          = savedNow
      Transition.requestFrame = savedReq
      Transition.cancelFrame  = savedCancel
