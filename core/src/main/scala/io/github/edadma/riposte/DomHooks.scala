package io.github.edadma.riposte

import org.scalajs.dom
import scala.scalajs.js

// Generic, DOM-oriented hooks built on the core primitives (useState/useEffect/useRef).
// They are not specific to any component or app, so they live in the core library where
// every riposte consumer can reach them — rather than being re-implemented per project.

/** Call `handler` when a pointer press lands outside the element held by `ref`, but only
  * while `active` is true (typically an open popup/menu). This is the robust way to
  * dismiss overlays — a real document `pointerdown` listener that checks containment,
  * rather than a focus-`blur` race with a `setTimeout`. The listener is installed only
  * while active and torn down when `active` flips or the component unmounts.
  */
def useClickOutside(ref: Ref[dom.Element | Null], active: Boolean, handler: () => Unit)(using Hooks): Unit =
  useEffect(
    () =>
      if !active then noCleanup
      else
        val listener: dom.Event => Unit = e =>
          val el     = ref.current
          val target = e.target
          if el != null && target != null &&
            !el.asInstanceOf[dom.Node].contains(target.asInstanceOf[dom.Node])
          then handler()
        dom.document.addEventListener("pointerdown", listener)
        () => dom.document.removeEventListener("pointerdown", listener)
    ,
    Array(active),
  )

/** Report whether the element held by `ref` is in (or near) the viewport, for lazy
  * loading and infinite scroll. Returns a `Boolean` that flips to `true` when the
  * element intersects the viewport, expanded by `rootMargin` (so work can start just
  * *before* the element scrolls in). With `once = true` (the default) it latches at
  * `true` and disconnects the observer — right for lazy-loading an image, which never
  * needs to unload. With `once = false` it tracks visibility both ways, for
  * pause-when-offscreen behaviour. `threshold` is the fraction of the element that must
  * be visible to count as intersecting. Where IntersectionObserver is unavailable, it
  * returns `true` so dependent content still loads.
  */
def useIntersectionObserver(
    ref:        Ref[dom.Element | Null],
    rootMargin: String  = "200px",
    threshold:  Double  = 0.0,
    once:       Boolean = true,
)(using Hooks): Boolean =
  val (inView, setInView, _) = useState(false)
  // A layout effect (not a passive one) so that the "already visible" catch-up — when
  // the element starts in view, or where IntersectionObserver is unavailable — commits
  // in the same flush, before paint, with no flash of the not-yet-loaded state.
  useLayoutEffect(
    () =>
      val el = ref.current
      if el == null then noCleanup
      else
        // Some environments (notably jsdom) don't implement IntersectionObserver —
        // constructing one throws. There we can't observe visibility, so we treat the
        // content as already visible: degraded but functional.
        try
          val opts = new dom.IntersectionObserverInit {}
          opts.rootMargin = rootMargin
          opts.threshold = js.Array(threshold)
          val obs = new dom.IntersectionObserver(
            (entries, observer) =>
              var i = 0
              while i < entries.length do
                val entry = entries(i)
                if entry.isIntersecting then
                  setInView(true)
                  if once then observer.disconnect()
                else if !once then setInView(false)
                i += 1
            ,
            opts,
          )
          obs.observe(el)
          () => obs.disconnect()
        catch
          case _: Throwable =>
            setInView(true)
            noCleanup
    ,
    Array(once, rootMargin, threshold),
  )
  inView

/** Subscribe to a CSS media query and re-render when it starts or stops matching.
  * Returns the current match state (`false` until the first effect runs, and `false`
  * always where `matchMedia` is unavailable, e.g. jsdom). The query is live: changing
  * the viewport, orientation, or `prefers-color-scheme` updates the value.
  */
def useMediaQuery(query: String)(using Hooks): Boolean =
  val (matches, setMatches, _) = useState(false)
  useLayoutEffect(
    () =>
      // matchMedia is absent in jsdom and very old browsers; treat that as "no match".
      if js.isUndefined(dom.window.asInstanceOf[js.Dynamic].matchMedia) then noCleanup
      else
        val mql = dom.window.matchMedia(query)
        setMatches(mql.matches)
        val listener: js.Function1[dom.Event, Unit] = (_: dom.Event) => setMatches(mql.matches)
        mql.addEventListener("change", listener)
        () => mql.removeEventListener("change", listener)
    ,
    Array(query),
  )
  matches

/** Attach `handler` for `event` on `target` for the lifetime of the component. The
  * latest `handler` is always used (it is kept in a ref, so a handler that closes over
  * changing state stays current) without re-subscribing on every render — the
  * subscription is rebuilt only when `target` or `event` changes, and removed on
  * unmount. The companion to writing `addEventListener`/`removeEventListener` by hand.
  */
def useEventListener(target: dom.EventTarget, event: String, handler: dom.Event => Unit)(using Hooks): Unit =
  val saved = useRef(handler)
  saved.current = handler
  useEffect(
    () =>
      val listener: js.Function1[dom.Event, Unit] = (e: dom.Event) => saved.current(e)
      target.addEventListener(event, listener)
      () => target.removeEventListener(event, listener)
    ,
    Array(target, event),
  )

/** Call `onResize` whenever the element held by `ref` changes size — the building block
  * for any layout that must recompute when its container resizes (a measured masonry, a
  * canvas, a virtualized list). `ResizeObserver` watches the element itself, so it fires
  * for container resizes that a `window` `resize` listener would miss (a sidebar opening,
  * a flex sibling growing). The latest `onResize` is always used (kept in a ref) without
  * re-subscribing, and the observer is disconnected on unmount. Where `ResizeObserver` is
  * unavailable (notably jsdom), it falls back to a `window` `resize` listener so the
  * callback still fires for viewport changes — degraded but functional.
  */
def useResizeObserver(ref: Ref[dom.Element | Null], onResize: () => Unit)(using Hooks): Unit =
  val saved = useRef(onResize)
  saved.current = onResize
  useLayoutEffect(
    () =>
      val el = ref.current
      if el == null then noCleanup
      else
        // Constructing a ResizeObserver throws where the API is absent; there we observe
        // the viewport instead, which still catches the resizes most layouts care about.
        try
          val ro = new dom.ResizeObserver((_, _) => saved.current())
          ro.observe(el)
          () => ro.disconnect()
        catch
          case _: Throwable =>
            val listener: js.Function1[dom.Event, Unit] = (_: dom.Event) => saved.current()
            dom.window.addEventListener("resize", listener)
            () => dom.window.removeEventListener("resize", listener)
    ,
    Array.empty[Any],
  )

/** The lifecycle phase of a presence-managed element, mirrored to `data-state` so a skin
  * styles each step. `Enter` is the just-mounted initial state (the element exists but its
  * open styles have not been applied); a frame later it advances to `Open`, so a CSS
  * `transition` from the enter state to the open state runs. `Exit` is the leaving state —
  * the element is still mounted so its exit animation can play before it is removed. */
enum PresencePhase:
  case Enter, Open, Exit

  /** The `data-state` token for this phase. */
  def token: String = this match
    case Enter => "enter"
    case Open  => "open"
    case Exit  => "exit"

/** What [[usePresence]] reports each render: whether the element should be in the DOM at
  * all (`mounted`), and which lifecycle [[PresencePhase]] it is in. A plain named tuple,
  * never a case class. */
type Presence = (mounted: Boolean, phase: PresencePhase)

/** Keep an element mounted through its exit animation. riposte unmounts a subtree the
  * instant its `open` condition turns false, which gives no chance to animate a close;
  * `usePresence` bridges that gap. Drive it with the caller's `open` flag and the exit
  * animation's duration (`exitMs`), then render the element only while `mounted` is true
  * and mirror `phase` to `data-state`:
  *
  * {{{
  * val p = usePresence(open, exitMs = 200)
  * if p.mounted then
  *   portal(body, div(data("state") := p.phase.token, ...))
  * else VEmpty
  * }}}
  *
  * The phase sequence on open is `Enter` → (one frame later) `Open`, so a CSS `transition`
  * keyed on `[data-state]` animates the element in; on close it becomes `Exit` and stays
  * mounted for `exitMs` before `mounted` flips to false. A re-open mid-exit cancels the
  * pending unmount and re-enters. The frame and the delay are taken from the same seams as
  * [[useTransition]] / the value hooks, so tests drive both deterministically. This is the
  * shared foundation for Modal, Drawer, Toast, Tooltip and any other dismissible overlay.
  */
def usePresence(open: Boolean, exitMs: Int)(using Hooks): Presence =
  val (mounted, setMounted, _) = useState(open)
  val (phase, setPhase, _)     = useState(if open then PresencePhase.Enter else PresencePhase.Exit)

  useLayoutEffect(
    () =>
      if open then
        // Appearing (or re-appearing mid-exit): ensure it is mounted, start at Enter, then
        // advance to Open on the next frame so the enter→open transition has two distinct
        // committed states to animate between. The pending frame is cancelled if `open`
        // flips again before it fires.
        setMounted(true)
        setPhase(PresencePhase.Enter)
        val frame = Transition.requestFrame(() => setPhase(PresencePhase.Open))
        () => Transition.cancelFrame(frame)
      else if mounted then
        // Leaving: stay mounted and switch to Exit so the close animation can play, then
        // unmount after `exitMs`. The timer is cancelled if `open` turns true again first.
        setPhase(PresencePhase.Exit)
        Timers.schedule(() => setMounted(false), exitMs)
      else
        // Already absent and still closed (e.g. first mount with open = false): nothing to do.
        noCleanup
    ,
    Array(open),
  )

  (mounted = mounted, phase = phase)

// The elements Tab should cycle through inside a trap: anything focusable by default, minus
// what is explicitly removed from the tab order (`tabindex="-1"`).
private val FocusTrapSelector =
  "a[href], area[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), " +
    "textarea:not([disabled]), [tabindex]:not([tabindex='-1'])"

/** Confine keyboard focus to one container while `active`. Returns a ref to attach to the
  * container element — give it `tabindex="-1"` so it can hold focus itself. While active: focus
  * moves into the container when it opens, `Tab`/`Shift+Tab` wrap within it (and any stray
  * focus outside is pulled back), and focus is restored to the previously-focused element when
  * `active` flips off or the component unmounts. This is the shared foundation for modal
  * overlays (Modal, Drawer) — the focus half of "modal", separate from Escape-to-close, which
  * each component still owns.
  */
def useFocusTrap(active: Boolean)(using Hooks): Ref[dom.Element | Null] =
  val container = useRef[dom.Element | Null](null)

  // Move focus into the container on open and restore it on close/unmount. A layout effect so
  // focus lands before paint; the cleanup captures the previously-focused element and returns
  // focus to it.
  useLayoutEffect(
    () =>
      if !active then noCleanup
      else
        val previous = dom.document.activeElement
        val c        = container.current
        if c != null then c.asInstanceOf[dom.html.Element].focus()
        () =>
          if previous != null then
            try previous.asInstanceOf[dom.html.Element].focus()
            catch case _: Throwable => ()
    ,
    Array(active),
  )

  // Trap Tab within the container via a document listener while active: wrap from the last
  // focusable back to the first (and the reverse for Shift+Tab), and pull focus back in if it
  // has escaped the container entirely. A document listener (rather than an element handler)
  // keeps the trap working no matter where focus currently sits.
  useEffect(
    () =>
      if !active then noCleanup
      else
        val listener: js.Function1[dom.KeyboardEvent, Unit] = (e: dom.KeyboardEvent) =>
          if e.key == "Tab" then
            val c = container.current
            if c != null then
              val focusables = c.querySelectorAll(FocusTrapSelector)
              if focusables.length > 0 then
                val first    = focusables(0).asInstanceOf[dom.html.Element]
                val last     = focusables(focusables.length - 1).asInstanceOf[dom.html.Element]
                val activeEl = dom.document.activeElement
                if e.shiftKey && (activeEl eq first) then
                  e.preventDefault(); last.focus()
                else if !e.shiftKey && (activeEl eq last) then
                  e.preventDefault(); first.focus()
                else if activeEl == null || !c.contains(activeEl) then
                  e.preventDefault(); first.focus()
        dom.document.addEventListener("keydown", listener)
        () => dom.document.removeEventListener("keydown", listener)
    ,
    Array(active),
  )

  container
