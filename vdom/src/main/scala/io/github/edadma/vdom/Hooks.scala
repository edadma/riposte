package io.github.edadma.vdom

import scala.collection.mutable.ArrayBuffer

// An effect's optional teardown, run before the effect re-runs and on unmount.
// Return `noCleanup` when there is nothing to undo.
type Cleanup = () => Unit

val noCleanup: Cleanup = () => ()

// Hook entry points. Each resolves the ambient `Hooks` — supplied implicitly by
// the component's `Hooks ?=> VNode` render context — and delegates to it, so a
// component body calls `useState(0)` rather than naming a `hooks` parameter.

// `initial` is by-name, so it is evaluated only on the first render (when the state
// cell is created) and never again. Pass a plain value as usual — `useState(0)` — or an
// allocating expression you don't want re-run each render — `useState(new Buffer)`
// builds the Buffer once, not on every render (the lazy-initializer form, folded into
// the one signature rather than a separate `useState(() => …)`).
def useState[T](initial: => T)(using h: Hooks): (T, T => Unit, (T => T) => Unit) =
  h.useState(initial)

def useEffect(body: () => Cleanup, deps: Array[Any] | Null)(using h: Hooks): Unit =
  h.useEffect(body, deps)

def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null)(using h: Hooks): Unit =
  h.useLayoutEffect(body, deps)

def useRef[T](initial: T)(using h: Hooks): Ref[T] =
  h.useRef(initial)

def useMemo[T](compute: () => T, deps: Array[Any])(using h: Hooks): T =
  h.useMemo(compute, deps)

def useCallback[F](fn: F, deps: Array[Any])(using h: Hooks): F =
  h.useCallback(fn, deps)

def useReducer[S, A](reducer: (S, A) => S, initial: S)(using h: Hooks): (S, A => Unit) =
  h.useReducer(reducer, initial)

def useId()(using h: Hooks): String =
  h.useId()

def useTransition(target: Double, durationMs: Int)(using h: Hooks): Double =
  h.useTransition(target, durationMs)

def useContext[T](ctx: Context[T])(using h: Hooks): T =
  h.useContext(ctx)

def useSyncExternalStore[T](
    subscribe:   (() => Unit) => (() => Unit),
    getSnapshot: () => T,
)(using h: Hooks): T =
  h.useSyncExternalStore(subscribe, getSnapshot)

// Expose an imperative handle to the component's parent. The parent owns a ref
// (`useRef[Handle | Null](null)`) and passes it down as a prop; the child fills it
// here with a value the parent can then call into — the analogue of React's
// `useImperativeHandle` (and the "forward a ref" pattern is simply passing a ref
// prop and binding it to an element with `ref := …`). The handle is (re)built when
// `deps` change, in a layout effect so it is set before paint — and cleared on
// unmount, hence the `| Null` handle type.
def useImperativeHandle[T](ref: Ref[T], factory: () => T, deps: Array[Any] | Null)(using Hooks): Unit =
  useLayoutEffect(
    () =>
      ref.current = factory()
      () => ref.current = null.asInstanceOf[T],
    deps,
  )

// A copy of `value` that lags one commit behind: a render that changes `value`
// returns the previous deferred value, then a passive effect updates it — so an
// urgent part of the UI can paint immediately while an expensive consumer of the
// deferred value catches up just after. The synchronous-reconciler analogue of
// React's useDeferredValue.
def useDeferredValue[T](value: T)(using Hooks): T =
  val (deferred, setDeferred, _) = useState(value)
  useEffect(() => { setDeferred(value); noCleanup }, Array(value))
  deferred

// `value` once it has stopped changing for `delayMs`. Each change restarts the
// timer, so only the final value of a burst is returned — the classic debounce,
// for search-as-you-type and similar. The pending timer is cancelled on unmount
// and whenever `value`/`delayMs` change (the effect's cleanup is the canceller).
def useDebouncedValue[T](value: T, delayMs: Int)(using Hooks): T =
  val (debounced, setDebounced, _) = useState(value)
  useEffect(() => Timers.schedule(() => setDebounced(value), delayMs), Array(value, delayMs))
  debounced

// `value` emitted at most once per `intervalMs`: the first value is reflected
// immediately (leading edge), further changes within a window are coalesced and
// the latest is emitted when the window closes (trailing edge). For high-rate
// sources like scroll, resize, or pointer move.
def useThrottledValue[T](value: T, intervalMs: Int)(using Hooks): T =
  val (throttled, setThrottled, _) = useState(value)
  val started = useRef(false)
  val cooling = useRef(false)
  val pending = useRef[Option[T]](None)
  val cancel  = useRef[() => Unit](() => ())

  def openWindow(): Unit =
    cancel.current = Timers.schedule(
      () =>
        pending.current match
          case Some(v) =>
            pending.current = None
            setThrottled(v)
            openWindow() // a fresh window so a later change is still rate-limited
          case None =>
            cooling.current = false,
      intervalMs,
    )

  // Layout effect so the leading edge commits in the same flush as the change,
  // rather than a tick later — the change reflects at once, as throttling expects.
  useLayoutEffect(
    () =>
      if !started.current then started.current = true // mount: the initial value is already shown
      else if cooling.current then pending.current = Some(value)
      else
        setThrottled(value) // leading edge — reflect the change at once
        cooling.current = true
        openWindow()
      noCleanup,
    Array(value),
  )
  // Cancel a live window when the component goes away.
  useEffect(() => () => cancel.current(), Array())
  throttled

// Per-component hook state. Each mounted function component owns one Hooks
// instance whose cells persist for the life of the component. Within a render,
// hook calls bind positionally to cells in call order — so, as in React, hooks
// must not be called conditionally or in loops.
object Hooks:
  // Process-wide counter behind useId. IDs need only be unique within a session,
  // and the reconciler runs single-threaded, so a plain counter suffices.
  private var idSeq: Long = 0
  private[vdom] def nextId(): String =
    idSeq += 1
    s"riposte-$idSeq"

  // The eased value of an in-flight transition at time `now`, using easeOutCubic.
  // Before the start it is `startValue`; at or past `startMs + durationMs` it is
  // exactly `target` (so a transition settles cleanly). A non-positive duration
  // jumps straight to the target.
  def transitionCurrent(cell: TransitionCell, now: Double): Double =
    if cell.durationMs <= 0 then cell.target
    else
      val raw = (now - cell.startMs) / cell.durationMs.toDouble
      if raw >= 1.0 then cell.target
      else
        val t = if raw < 0.0 then 0.0 else raw
        val u = 1.0 - t
        val k = 1.0 - u * u * u // easeOutCubic
        cell.startValue + (cell.target - cell.startValue) * k

// Timing for useTransition / usePresence, indirected so a host can install real
// frame timing and tests a deterministic clock + frame pump. The defaults are
// inert (a zero clock, a no-op frame loop): the host installs the browser's
// `performance.now()` and `requestAnimationFrame` / `cancelAnimationFrame`.
object Transition:
  var now:          () => Double         = () => 0.0
  var requestFrame: (() => Unit) => Int   = _ => 0
  var cancelFrame:  Int => Unit           = _ => ()

// Timer indirection behind the debounce/throttle/presence hooks:
// `schedule(fn, delayMs)` runs `fn` after the delay and returns a cancel function.
// The default is inert; the host installs setTimeout/clearTimeout, and tests
// install a manual version that fires pending timers on demand.
object Timers:
  var schedule: (() => Unit, Int) => (() => Unit) = (_, _) => () => ()

final class Hooks private[vdom] ():

  // Back-reference to the owning component instance, set at mount. Hooks use
  // it to mark the component dirty when state changes.
  private[vdom] var instance: ComponentInstance[?] | Null = null

  private val cells = ArrayBuffer.empty[Any]
  private var index = 0

  // Contexts this component reads. The reconciler consults this when a provider
  // value changes, to wake the component even if a memoized ancestor bailed.
  private[vdom] val subscribedContexts = scala.collection.mutable.HashSet.empty[Context[?]]

  private[vdom] def beginRender(): Unit = index = 0

  // -- useState -------------------------------------------------------------

  // Returns three values: the current state, a setter, and an updater. Both
  // mutators schedule a re-render only when the value actually changes. The
  // updater reads the live cell each call, so it composes correctly even when
  // several updates fire within one tick. Discard whichever you don't need:
  //
  //   val (count, setCount, _)           = hooks.useState(0)   // setter only
  //   val (count, _, updateCount)        = hooks.useState(0)   // updater only
  //   val (count, setCount, updateCount) = hooks.useState(0)   // both
  //
  //   setCount(5)
  //   updateCount(_ + 1)
  // `initial` is by-name: it is evaluated only here, the once, when the cell is first
  // created — on every later render the stored value is reused and `initial` is never
  // touched. That gives lazy initialization for free (an allocating initial expression
  // isn't re-run each render) through the same signature a plain value uses.
  def useState[T](initial: => T): (T, T => Unit, (T => T) => Unit) =
    val slot = index
    if slot >= cells.length then cells += initial
    index = slot + 1
    val current = cells(slot).asInstanceOf[T]
    val set:    T => Unit        = v => cellSet(slot, v)
    val update: (T => T) => Unit = f => cellSet(slot, f(cellGet[T](slot)))
    (current, set, update)

  private[vdom] def cellGet[T](slot: Int): T = cells(slot).asInstanceOf[T]

  private[vdom] def cellSet[T](slot: Int, next: T): Unit =
    val prev = cells(slot)
    if prev != next then
      cells(slot) = next
      val inst = instance
      if inst != null then Scheduler.enqueueUpdate(inst)

  // -- useReducer -----------------------------------------------------------

  // State managed by a reducer. Returns the current state and a `dispatch` that
  // feeds an action through `reducer` to produce the next state.
  def useReducer[S, A](reducer: (S, A) => S, initial: S): (S, A => Unit) =
    val (state, _, update) = useState(initial)
    val dispatch: A => Unit = a => update(s => reducer(s, a))
    (state, dispatch)

  // -- useRef ---------------------------------------------------------------

  // A mutable box that persists across renders. Writing `ref.current` does NOT
  // trigger a re-render — use it for host handles, timers, or any value that
  // should survive renders without driving them.
  def useRef[T](initial: T): Ref[T] =
    val slot = index
    if slot >= cells.length then cells += new Ref[T](initial)
    index = slot + 1
    cells(slot).asInstanceOf[Ref[T]]

  // -- useMemo / useCallback ------------------------------------------------

  // Memoize a computed value, recomputing only when `deps` change between
  // renders. Use to avoid expensive recomputation or to keep a stable reference.
  def useMemo[T](compute: () => T, deps: Array[Any]): T =
    val slot = index
    index = slot + 1
    if slot >= cells.length then
      val cell = new MemoCell(deps, compute())
      cells += cell
      cell.value.asInstanceOf[T]
    else
      val cell = cells(slot).asInstanceOf[MemoCell]
      if !sameDeps(cell.deps, deps) then
        cell.deps  = deps
        cell.value = compute()
      cell.value.asInstanceOf[T]

  // Memoize a callback — a stable function reference while `deps` are unchanged.
  def useCallback[F](fn: F, deps: Array[Any]): F = useMemo(() => fn, deps)

  // -- useId ----------------------------------------------------------------

  // A stable unique id, generated once per slot and returned unchanged on every
  // subsequent render — handy for label/input pairing or any unique-string need.
  def useId(): String =
    val slot = index
    if slot >= cells.length then cells += Hooks.nextId()
    index = slot + 1
    cells(slot).asInstanceOf[String]

  // -- useTransition --------------------------------------------------------

  // Animate a value toward `target` over `durationMs`, returning the eased
  // current value on every render. When `target` changes the transition restarts
  // from wherever the value was at that moment. While in flight, the hook drives
  // its own re-renders by requesting animation frames; reaching the target
  // settles it and stops the frames. The frame still pending at unmount is
  // cancelled by `runUnmountCleanups`.
  def useTransition(target: Double, durationMs: Int): Double =
    val slot = index
    index = slot + 1
    val now = Transition.now()
    if slot >= cells.length then
      cells += new TransitionCell(target, target, now, durationMs, -1)
      return target
    val cell = cells(slot).asInstanceOf[TransitionCell]
    if cell.target != target then
      cell.startValue = Hooks.transitionCurrent(cell, now)
      cell.target     = target
      cell.startMs    = now
      cell.durationMs = durationMs
    val current = Hooks.transitionCurrent(cell, now)
    // Keep a frame pending while the value hasn't settled; cancel any pending one
    // the moment it has, so a stable transition isn't holding the frame loop open.
    if current != cell.target then ensureTransitionFrame(cell)
    else cancelTransitionFrame(cell)
    current

  private def ensureTransitionFrame(cell: TransitionCell): Unit =
    if cell.rafId < 0 then
      cell.rafId = Transition.requestFrame { () =>
        cell.rafId = -1
        val inst = instance
        if inst != null then Scheduler.enqueueUpdate(inst)
      }

  private def cancelTransitionFrame(cell: TransitionCell): Unit =
    if cell.rafId >= 0 then
      Transition.cancelFrame(cell.rafId)
      cell.rafId = -1

  // -- useContext -----------------------------------------------------------

  // Read the value of the nearest enclosing provider for `ctx`, or the context's
  // default if there is none. Resolves by walking up the live instance tree from
  // this component, so a re-rendered consumer always sees the current value.
  //
  // Reading also records the context here, so when a provider value changes the
  // reconciler can find and wake this component — even behind a memoized
  // ancestor — by walking the provider's subtree.
  def useContext[T](ctx: Context[T]): T =
    val self = instance
    if self != null then subscribedContexts += ctx
    var cur: Instance | Null = self
    while cur != null do
      cur match
        case p: ProviderInstance if p.ctx eq ctx => return p.value.asInstanceOf[T]
        case _                                    => ()
      cur = cur.parent
    ctx.default

  // -- useEffect / useLayoutEffect ------------------------------------------

  // Run a side-effect after the commit. The body returns a `Cleanup` that runs
  // before the next run of this effect and when the component unmounts (return
  // `noCleanup` if there is nothing to tear down). `deps` controls re-runs:
  //   • `Array()`  — run once, on mount
  //   • `Array(a)` — run whenever `a` changes between renders
  //   • `null`     — run after every render
  //
  //   hooks.useEffect(() => {
  //     val id = dom.window.setInterval(() => tick(), 1000)
  //     () => dom.window.clearInterval(id)
  //   }, Array())
  //
  // Passive: runs after the browser paints.
  def useEffect(body: () => Cleanup, deps: Array[Any] | Null): Unit =
    scheduleEffect(body, deps, layout = false)

  // Like `useEffect`, but runs synchronously after the host is committed and
  // before paint — for effects that must read or adjust layout without the user
  // seeing an intermediate frame.
  def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null): Unit =
    scheduleEffect(body, deps, layout = true)

  // -- useSyncExternalStore -------------------------------------------------

  // Subscribe to a store that lives outside the component tree and re-render when
  // its snapshot changes — the bridge for app-state libraries and signals.
  // `subscribe` registers a callback the store invokes on every change and
  // returns an unsubscribe; `getSnapshot` reads the current value. Pass a STABLE
  // `subscribe` (a val/method on the store, not a fresh lambda each render) so the
  // subscription isn't torn down and rebuilt every render.
  //
  // The component re-renders only when the snapshot actually changes (`!=`), so a
  // `getSnapshot` that selects a slice bails out when that slice is unchanged even
  // if the store fired. A fresh snapshot is read every render, and re-checked once
  // when the subscription is established, so a change landing between render and
  // subscribe is caught rather than lost. Subscribing in a layout effect means
  // that catch-up re-render commits in the same flush, before paint.
  def useSyncExternalStore[T](subscribe: (() => Unit) => (() => Unit), getSnapshot: () => T): T =
    val value             = getSnapshot()
    val (_, _, forceTick) = useState(0)
    val ref               = useRef[(T, () => T)]((value, getSnapshot))
    ref.current           = (value, getSnapshot)
    useLayoutEffect(
      () => {
        // Read the latest snapshot and last-rendered value through the ref, so a
        // notification always compares against what is currently on screen.
        val onChange: () => Unit = () => {
          val (last, snap) = ref.current
          if snap() != last then forceTick(_ + 1)
        }
        onChange()
        val unsubscribe = subscribe(onChange)
        () => unsubscribe()
      },
      Array(subscribe),
    )
    value

  private def scheduleEffect(body: () => Cleanup, deps: Array[Any] | Null, layout: Boolean): Unit =
    val slot = index
    index = slot + 1
    val inst = instance
    if inst == null then return
    if slot >= cells.length then
      val cell = new EffectCell(deps, null, body, layout, inst)
      cells += cell
      Scheduler.scheduleEffect(cell)
    else
      val cell = cells(slot).asInstanceOf[EffectCell]
      if deps == null || !sameDeps(cell.deps, deps) then
        cell.deps        = deps
        cell.pendingBody = body
        Scheduler.scheduleEffect(cell)

  // Run every live effect cleanup. Called by the reconciler when the owning
  // component unmounts.
  private[vdom] def runUnmountCleanups(): Unit =
    var i = 0
    while i < cells.length do
      cells(i) match
        case e: EffectCell =>
          val c = e.cleanup
          if c != null then
            e.cleanup = null
            c()
        case t: TransitionCell =>
          // Stop the frame loop so a half-finished transition doesn't keep
          // requesting frames for a component that no longer exists.
          if t.rafId >= 0 then
            Transition.cancelFrame(t.rafId)
            t.rafId = -1
        case _ => ()
      i += 1

  private def sameDeps(a: Array[Any] | Null, b: Array[Any] | Null): Boolean =
    if a == null || b == null then false
    else if a.length != b.length then false
    else
      var i  = 0
      var eq = true
      while i < a.length && eq do
        if a(i) != b(i) then eq = false
        i += 1
      eq

// One useEffect / useLayoutEffect cell. `pendingBody` is the latest body to
// run; `cleanup` is the teardown returned by the previous run. `queued` guards
// against the same cell being enqueued twice before a flush. `owner` lets the
// scheduler order effects by depth and skip unmounted components.
private[vdom] final class EffectCell(
    var deps:        Array[Any] | Null,
    var cleanup:     Cleanup | Null,
    var pendingBody: () => Cleanup,
    val layout:      Boolean,
    val owner:       ComponentInstance[?],
):
  var queued: Boolean = false

// One useMemo / useCallback cell: the deps it was last computed for and the
// cached value.
private final class MemoCell(var deps: Array[Any] | Null, var value: Any)

// One useTransition cell. The value eases from `startValue` to `target` over
// `durationMs`, starting at `startMs` (on the transition clock). `rafId` is the
// pending animation-frame handle, or -1 when no frame is in flight — which
// doubles as the "settled" marker.
final class TransitionCell(
    var startValue: Double,
    var target:     Double,
    var startMs:    Double,
    var durationMs: Int,
    var rafId:      Int,
)

// A mutable cell whose writes do NOT trigger re-render. Returned by useRef and
// used internally; assign through `current`.
final class Ref[T](var current: T)
