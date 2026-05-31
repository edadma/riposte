package io.github.edadma.riposte

import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.scalajs.js

// The generic DOM hooks: useClickOutside, useEventListener, useMediaQuery, and
// useIntersectionObserver. jsdom implements neither IntersectionObserver nor
// matchMedia, so those tests install controllable fakes (restored afterward) to drive
// the real code path, and also cover the graceful fallback when they are absent.
class DomHooksSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def pointerDown(target: dom.EventTarget): Unit =
    target.dispatchEvent(new dom.Event("pointerdown", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()

  // -- useClickOutside ------------------------------------------------------

  test("useClickOutside fires for a press outside, not one inside, while active"):
    var hits                            = 0
    var boxRef: Ref[dom.Element | Null] = null
    val Comp = view {
      val r = useRef[dom.Element | Null](null)
      boxRef = r
      useClickOutside(r, true, () => hits += 1)
      div(ref := r, "box")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    val box = boxRef.current.asInstanceOf[dom.EventTarget]
    pointerDown(box) // inside the tracked element
    assert(hits == 0)
    pointerDown(dom.document.body) // outside it
    assert(hits == 1)

  test("useClickOutside does nothing while inactive"):
    var hits = 0
    val Comp = view {
      val r = useRef[dom.Element | Null](null)
      useClickOutside(r, false, () => hits += 1)
      div(ref := r, "box")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    pointerDown(dom.document.body)
    assert(hits == 0)

  // -- useEventListener -----------------------------------------------------

  test("useEventListener attaches a handler that fires on the event"):
    var count = 0
    val Comp = view {
      useEventListener(dom.document, "salle-test-evt", _ => count += 1)
      div("x")
    }
    render(Comp(), host())
    Scheduler.flushSync()
    dom.document.dispatchEvent(new dom.Event("salle-test-evt"))
    assert(count == 1)

  test("useEventListener removes the handler on unmount"):
    var count = 0
    val Comp = view {
      useEventListener(dom.document, "salle-test-evt2", _ => count += 1)
      div("x")
    }
    val c    = host()
    val root = createRoot(c)
    root.render(Comp())
    Scheduler.flushSync()
    root.unmount() // tears down the component and runs its effect cleanups
    Scheduler.flushSync()
    dom.document.dispatchEvent(new dom.Event("salle-test-evt2"))
    assert(count == 0)

  test("useEventListener always calls the latest handler"):
    var observed          = -1
    var bump: Int => Unit = null
    val Comp = view {
      val (n, set, _) = useState(0)
      bump = set
      useEventListener(dom.document, "salle-test-evt3", _ => observed = n)
      div(s"$n")
    }
    render(Comp(), host())
    Scheduler.flushSync()
    bump(5) // re-render; the handler now closes over n = 5
    Scheduler.flushSync()
    dom.document.dispatchEvent(new dom.Event("salle-test-evt3"))
    assert(observed == 5)

  // -- useMediaQuery --------------------------------------------------------

  test("useMediaQuery returns false when matchMedia is unavailable"):
    // jsdom has no matchMedia, so the hook reports no match.
    val Comp = view {
      val m = useMediaQuery("(min-width: 600px)")
      div(if m then "match" else "no-match")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    assert(c.textContent.contains("no-match"))

  test("useMediaQuery reflects the query and updates on change"):
    val w        = dom.window.asInstanceOf[js.Dynamic]
    val saved    = w.matchMedia
    val handlers = scala.collection.mutable.ArrayBuffer.empty[js.Function1[dom.Event, Unit]]
    val mql      = js.Dynamic.literal()
    mql.matches = true
    mql.addEventListener =
      ((_: String, l: js.Function1[dom.Event, Unit]) => { handlers += l; () }): js.Function2[String, js.Function1[
        dom.Event,
        Unit,
      ], Unit]
    mql.removeEventListener =
      ((_: String, l: js.Function1[dom.Event, Unit]) => { handlers -= l; () }): js.Function2[String, js.Function1[
        dom.Event,
        Unit,
      ], Unit]
    w.matchMedia = ((_: String) => mql): js.Function1[String, js.Any]
    try
      val Comp = view {
        val m = useMediaQuery("(prefers-color-scheme: dark)")
        div(if m then "match" else "no-match")
      }
      val c = host()
      render(Comp(), c)
      Scheduler.flushSync()
      assert(c.textContent.contains("match"))
      mql.matches = false
      handlers.foreach(_(new dom.Event("change")))
      Scheduler.flushSync()
      assert(c.textContent.contains("no-match"))
    finally w.matchMedia = saved

  // -- useIntersectionObserver ----------------------------------------------

  test("useIntersectionObserver reports visible when IntersectionObserver is unavailable"):
    // jsdom has no IntersectionObserver; the hook falls back to "visible" so dependent
    // content still loads.
    val Comp = view {
      val r = useRef[dom.Element | Null](null)
      val v = useIntersectionObserver(r)
      div(ref := r, if v then "in" else "out")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    assert(c.textContent.contains("in"))

  test("useIntersectionObserver flips to visible when the observer reports intersection"):
    // `new dom.IntersectionObserver` compiles to a bare `new IntersectionObserver`, which
    // only resolves against a real global binding — so install the fake on `globalThis`
    // via eval (assigning through js.Dynamic.global does NOT create that binding in Node).
    // The fake records its instances on globalThis so the test can drive the callback.
    js.eval(
      """globalThis.__savedIO = globalThis.IntersectionObserver;
         globalThis.__ioInstances = [];
         globalThis.IntersectionObserver = function (cb) {
           this._cb = cb;
           globalThis.__ioInstances.push(this);
         };
         globalThis.IntersectionObserver.prototype.observe = function () {};
         globalThis.IntersectionObserver.prototype.disconnect = function () {};""",
    )
    try
      val Comp = view {
        val r = useRef[dom.Element | Null](null)
        val v = useIntersectionObserver(r)
        div(ref := r, if v then "in" else "out")
      }
      val c = host()
      render(Comp(), c)
      Scheduler.flushSync()
      assert(c.textContent.contains("out")) // observer hasn't fired yet

      val instances = js.eval("globalThis.__ioInstances").asInstanceOf[js.Array[js.Dynamic]]
      assert(instances.length == 1)
      val inst  = instances(0)
      val entry = js.Dynamic.literal(isIntersecting = true)
      inst._cb(js.Array[js.Dynamic](entry), inst) // drive an intersection
      Scheduler.flushSync()
      assert(c.textContent.contains("in"))
    finally
      js.eval(
        """globalThis.IntersectionObserver = globalThis.__savedIO;
           delete globalThis.__savedIO;
           delete globalThis.__ioInstances;""",
      )
