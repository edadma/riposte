package io.github.edadma.vdom

import scala.collection.mutable.ArrayBuffer

// An effect's optional teardown, run before the effect re-runs and on unmount.
// Return `noCleanup` when there is nothing to undo.
type Cleanup = () => Unit

val noCleanup: Cleanup = () => ()

// Hook entry points. Each resolves the ambient `Hooks` — supplied implicitly by
// the component's `Hooks ?=> VNode` render context — and delegates to it, so a
// component body calls `useState(0)` rather than naming a `hooks` parameter.

def useState[T](initial: T)(using h: Hooks): (T, T => Unit, (T => T) => Unit) =
  h.useState(initial)

def useEffect(body: () => Cleanup, deps: Array[Any] | Null)(using h: Hooks): Unit =
  h.useEffect(body, deps)

def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null)(using h: Hooks): Unit =
  h.useLayoutEffect(body, deps)

// Per-component hook state. Each mounted function component owns one Hooks
// instance whose cells persist for the life of the component. Within a render,
// hook calls bind positionally to cells in call order — so, as in React, hooks
// must not be called conditionally or in loops.
final class Hooks private[vdom] ():

  // Back-reference to the owning component instance, set at mount. Hooks use
  // it to mark the component dirty when state changes.
  private[vdom] var instance: ComponentInstance[?] | Null = null

  private val cells          = ArrayBuffer.empty[Any]
  private var index          = 0

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
  def useState[T](initial: T): (T, T => Unit, (T => T) => Unit) =
    val slot = index
    if slot >= cells.length then cells += initial
    index = slot + 1
    val current = cells(slot).asInstanceOf[T]
    val set:    T => Unit       = v  => cellSet(slot, v)
    val update: (T => T) => Unit = f  => cellSet(slot, f(cellGet[T](slot)))
    (current, set, update)

  private[vdom] def cellGet[T](slot: Int): T = cells(slot).asInstanceOf[T]

  private[vdom] def cellSet[T](slot: Int, next: T): Unit =
    val prev = cells(slot)
    if prev != next then
      cells(slot) = next
      val inst = instance
      if inst != null then Scheduler.enqueueUpdate(inst)

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

  // Like `useEffect`, but runs synchronously after the DOM is committed and
  // before paint — for effects that must read or adjust layout without the user
  // seeing an intermediate frame.
  def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null): Unit =
    scheduleEffect(body, deps, layout = true)

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

// A mutable cell whose writes do NOT trigger re-render (the future useRef).
final class Ref[T](var current: T)
