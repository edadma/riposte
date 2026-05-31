package io.github.edadma.riposte

import org.scalajs.dom
import org.scalatest.BeforeAndAfter
import scala.collection.mutable

// usePresence keeps an element mounted through its exit animation. These specs pin the
// lifecycle: the Enter→Open frame bump on appearance, staying mounted during Exit and
// unmounting after the delay, the re-open-mid-exit cancellation, and the first-mount
// branches. The enter frame and the exit delay are swapped for deterministic fakes via
// the shared Transition / Timers seams, installed per-test and restored afterward.
class PresenceSpec extends DomSuite with BeforeAndAfter:

  private val frames = mutable.Queue.empty[() => Unit]
  private val timers = mutable.Queue.empty[() => Unit]

  private var savedReq:      (() => Unit) => Int                  = null
  private var savedCancel:   Int => Unit                         = null
  private var savedSchedule: (() => Unit, Int) => (() => Unit)   = null

  before {
    savedReq      = Transition.requestFrame
    savedCancel   = Transition.cancelFrame
    savedSchedule = Timers.schedule
    frames.clear()
    timers.clear()
    Transition.requestFrame = cb => { frames += cb; frames.length }
    Transition.cancelFrame  = _ => ()
    Timers.schedule         = (fn, _) => { timers += fn; () => { timers -= fn; () } }
  }

  after {
    Transition.requestFrame = savedReq
    Transition.cancelFrame  = savedCancel
    Timers.schedule         = savedSchedule
  }

  private def state(c: dom.Element): String =
    val el = c.querySelector("[data-state]")
    if el == null then "" else el.getAttribute("data-state")

  test("starts unmounted and renders nothing when initially closed"):
    val c = host()
    val App = view {
      val (open, _, _) = useState(false)
      val p            = usePresence(open, 200)
      if p.mounted then div(data("state") := p.phase.token, "body") else VEmpty
    }
    render(App(), c)
    Scheduler.flushSync()
    assert(c.querySelector("[data-state]") == null)

  test("opening mounts at enter, then advances to open on the next frame"):
    val c = host()
    var setOpen: Boolean => Unit = null
    val App = view {
      val (open, set, _) = useState(false)
      setOpen = set
      val p = usePresence(open, 200)
      if p.mounted then div(data("state") := p.phase.token, "body") else VEmpty
    }
    render(App(), c)
    Scheduler.flushSync()
    setOpen(true)
    Scheduler.flushSync()
    assert(state(c) == "enter") // mounted, but open styles not yet applied
    assert(frames.length == 1)  // a frame was requested to advance to open
    frames.dequeue()()          // fire it
    Scheduler.flushSync()
    assert(state(c) == "open")

  test("closing keeps the element mounted in the exit phase until the delay elapses"):
    val c = host()
    var setOpen: Boolean => Unit = null
    val App = view {
      val (open, set, _) = useState(true) // starts open
      setOpen = set
      val p = usePresence(open, 200)
      if p.mounted then div(data("state") := p.phase.token, "body") else VEmpty
    }
    render(App(), c)
    Scheduler.flushSync()
    frames.dequeue()() // settle the initial enter→open
    Scheduler.flushSync()
    assert(state(c) == "open")

    setOpen(false)
    Scheduler.flushSync()
    assert(state(c) == "exit") // still mounted, now leaving
    assert(c.querySelector("[data-state]") != null)
    assert(timers.length == 1) // an unmount timer is pending

    timers.dequeue()() // exit delay elapses
    Scheduler.flushSync()
    assert(c.querySelector("[data-state]") == null) // now unmounted

  test("re-opening during the exit cancels the pending unmount and re-enters"):
    val c = host()
    var setOpen: Boolean => Unit = null
    val App = view {
      val (open, set, _) = useState(true)
      setOpen = set
      val p = usePresence(open, 200)
      if p.mounted then div(data("state") := p.phase.token, "body") else VEmpty
    }
    render(App(), c)
    Scheduler.flushSync()
    frames.dequeue()()
    Scheduler.flushSync()

    setOpen(false)
    Scheduler.flushSync()
    assert(state(c) == "exit")
    assert(timers.length == 1)

    setOpen(true) // re-open before the timer fires
    Scheduler.flushSync()
    assert(state(c) == "enter") // back to entering, still mounted
    assert(c.querySelector("[data-state]") != null)
    // The stale unmount timer was cancelled (its cancel removed it from the queue).
    assert(timers.isEmpty)
    frames.dequeue()()
    Scheduler.flushSync()
    assert(state(c) == "open")

  test("an element open from first mount animates in via the enter frame"):
    val c = host()
    val App = view {
      val (open, _, _) = useState(true)
      val p            = usePresence(open, 200)
      if p.mounted then div(data("state") := p.phase.token, "body") else VEmpty
    }
    render(App(), c)
    Scheduler.flushSync()
    assert(state(c) == "enter") // mounted immediately, entering
    assert(frames.length == 1)
    frames.dequeue()()
    Scheduler.flushSync()
    assert(state(c) == "open")
