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

  // -- useResizeObserver ----------------------------------------------------

  test("useResizeObserver calls onResize when the observer reports a resize"):
    // Install a fake ResizeObserver on globalThis (same technique as the IO test: a bare
    // `new ResizeObserver` only resolves against a real global binding). The fake records
    // its instances so the test can drive the callback.
    js.eval(
      """globalThis.__savedRO = globalThis.ResizeObserver;
         globalThis.__roInstances = [];
         globalThis.ResizeObserver = function (cb) {
           this._cb = cb;
           globalThis.__roInstances.push(this);
         };
         globalThis.ResizeObserver.prototype.observe = function () {};
         globalThis.ResizeObserver.prototype.disconnect = function () {};""",
    )
    try
      var resizes = 0
      val Comp = view {
        val r = useRef[dom.Element | Null](null)
        useResizeObserver(r, () => resizes += 1)
        div(ref := r, "box")
      }
      val c = host()
      render(Comp(), c)
      Scheduler.flushSync()
      assert(resizes == 0) // observer hasn't fired yet

      val instances = js.eval("globalThis.__roInstances").asInstanceOf[js.Array[js.Dynamic]]
      assert(instances.length == 1)
      val inst = instances(0)
      inst._cb(js.Array[js.Dynamic](), inst) // drive a resize callback
      Scheduler.flushSync()
      assert(resizes == 1)
    finally
      js.eval(
        """globalThis.ResizeObserver = globalThis.__savedRO;
           delete globalThis.__savedRO;
           delete globalThis.__roInstances;""",
      )

  test("useResizeObserver falls back to a window resize listener when ResizeObserver is absent"):
    // jsdom has no ResizeObserver, so the hook installs a window 'resize' listener instead.
    var resizes = 0
    val Comp = view {
      val r = useRef[dom.Element | Null](null)
      useResizeObserver(r, () => resizes += 1)
      div(ref := r, "box")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    dom.window.dispatchEvent(new dom.Event("resize"))
    Scheduler.flushSync()
    assert(resizes == 1)

  test("useResizeObserver always calls the latest onResize (fallback path)"):
    var observed          = -1
    var bump: Int => Unit = null
    val Comp = view {
      val (n, set, _) = useState(0)
      bump = set
      val r = useRef[dom.Element | Null](null)
      useResizeObserver(r, () => observed = n)
      div(ref := r, s"$n")
    }
    val c = host()
    render(Comp(), c)
    Scheduler.flushSync()
    bump(7) // re-render; the handler now closes over n = 7
    Scheduler.flushSync()
    dom.window.dispatchEvent(new dom.Event("resize"))
    Scheduler.flushSync()
    assert(observed == 7)

  // -- useFocusTrap ---------------------------------------------------------

  private def tabKey(target: dom.EventTarget, shift: Boolean): Unit =
    target.dispatchEvent(
      new dom.KeyboardEvent(
        "keydown",
        new dom.KeyboardEventInit { key = "Tab"; shiftKey = shift; bubbles = true; cancelable = true },
      ),
    )
    Scheduler.flushSync()

  // Each focus-trap test installs a *document* keydown listener and moves focus, so they
  // must run in isolation: clear the body first (drop leftover nodes) and unmount at the end
  // (drop the listener), or a prior test's still-attached trap would fight this one's.
  private def clearBody(): Unit = dom.document.body.innerHTML = ""

  test("useFocusTrap moves focus into the container when active"):
    clearBody()
    val Comp = view {
      val trap = useFocusTrap(true)
      div(ref := trap, tabIndex := "-1", id := "trap", button(id := "f", typ := "button", "f"))
    }
    val root = createRoot(host())
    root.render(Comp())
    Scheduler.flushSync()
    assert(dom.document.activeElement eq dom.document.getElementById("trap"))
    root.unmount()
    Scheduler.flushSync()

  test("useFocusTrap wraps Tab at the last focusable back to the first"):
    clearBody()
    val Comp = view {
      val trap = useFocusTrap(true)
      div(
        ref      := trap,
        tabIndex := "-1",
        id       := "trap",
        button(id := "first", typ := "button", "first"),
        button(id := "last", typ := "button", "last"),
      )
    }
    val root = createRoot(host())
    root.render(Comp())
    Scheduler.flushSync()
    val first = dom.document.getElementById("first").asInstanceOf[dom.html.Element]
    val last  = dom.document.getElementById("last").asInstanceOf[dom.html.Element]
    last.focus()
    tabKey(last, shift = false) // at the last element, forward Tab wraps to first
    assert(dom.document.activeElement eq first)
    first.focus()
    tabKey(first, shift = true) // at the first element, Shift+Tab wraps to last
    assert(dom.document.activeElement eq last)
    root.unmount()
    Scheduler.flushSync()

  test("useFocusTrap pulls focus back when it has escaped the container"):
    clearBody()
    val Comp = view {
      val trap = useFocusTrap(true)
      div(ref := trap, tabIndex := "-1", id := "trap", button(id := "first", typ := "button", "first"))
    }
    val root = createRoot(host())
    root.render(Comp())
    Scheduler.flushSync()
    val outside = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(outside)
    outside.focus()
    assert(dom.document.activeElement eq outside)
    tabKey(outside, shift = false)
    assert(dom.document.activeElement eq dom.document.getElementById("first"))
    root.unmount()
    Scheduler.flushSync()

  test("useFocusTrap does not trap or move focus while inactive"):
    clearBody()
    val Comp = view {
      val trap = useFocusTrap(false)
      div(ref := trap, tabIndex := "-1", id := "trap", button(id := "first", typ := "button", "first"))
    }
    val opener = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    opener.focus()
    val root = createRoot(host())
    root.render(Comp())
    Scheduler.flushSync()
    assert(dom.document.activeElement eq opener) // focus was not moved into the container
    val first = dom.document.getElementById("first").asInstanceOf[dom.html.Element]
    first.focus()
    tabKey(first, shift = false) // no trap installed, so the wrap does not fire
    assert(dom.document.activeElement eq first)
    root.unmount()
    Scheduler.flushSync()

  test("useFocusTrap restores focus to the opener when it closes"):
    clearBody()
    var active            = true
    var bump: Int => Unit = null
    val Comp = view {
      val (_, set, _) = useState(0)
      bump = set
      val trap = useFocusTrap(active)
      div(ref := trap, tabIndex := "-1", id := "trap", button(id := "first", typ := "button", "first"))
    }
    val opener = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    opener.focus()
    val root = createRoot(host())
    root.render(Comp())
    Scheduler.flushSync()
    assert(dom.document.activeElement eq dom.document.getElementById("trap"))
    active = false
    bump(1) // re-render with active = false → the layout-effect cleanup restores focus
    Scheduler.flushSync()
    assert(dom.document.activeElement eq opener)
    root.unmount()
    Scheduler.flushSync()
