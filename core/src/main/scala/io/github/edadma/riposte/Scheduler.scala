package io.github.edadma.riposte

import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor
import scala.collection.mutable

// Batches state updates and effects, mirroring React's commit timing:
//
//   • State writes mark a component dirty and request a flush on the MICROTASK
//     queue. Several updates within one tick collapse into a single re-render
//     that commits to the DOM before the browser paints — no flicker, tightest
//     coalescing.
//   • Layout effects run synchronously inside that flush, after the DOM is
//     committed but before paint, so they can read/adjust layout invisibly.
//   • Passive effects run later, after paint, on a MACROTASK, so heavy effect
//     work doesn't block the frame.
//
// Dirty components re-render shallowest-first; re-rendering a parent reconciles
// its subtree (clearing descendant dirty flags), so each renders at most once.
// Effects run deepest-first, matching React's child-before-parent ordering.
object Scheduler:

  private val dirty          = mutable.ArrayBuffer.empty[ComponentInstance[?]]
  private val layoutEffects  = mutable.ArrayBuffer.empty[EffectCell]
  private val passiveEffects = mutable.ArrayBuffer.empty[EffectCell]

  private var renderScheduled  = false
  private var passiveScheduled = false

  // Guards against an effect that unconditionally sets state, which would
  // otherwise spin the render/layout loop forever (React calls this "maximum
  // update depth exceeded").
  private val MaxFlushPasses = 100

  def enqueueUpdate(inst: ComponentInstance[?]): Unit =
    if !inst.dirty then
      inst.dirty = true
      dirty += inst
    requestFlush()

  private[riposte] def scheduleEffect(cell: EffectCell): Unit =
    if !cell.queued then
      cell.queued = true
      if cell.layout then layoutEffects += cell else passiveEffects += cell
    requestFlush()

  // Visible for tests: run everything — renders, layout effects, and passive
  // effects — synchronously, so assertions observe the committed result without
  // awaiting the microtask/macrotask queues.
  def flushSync(): Unit =
    drainRenderAndLayout()
    drainPassive()

  private def requestFlush(): Unit =
    if !renderScheduled then
      renderScheduled = true
      dom.window.queueMicrotask(() => flush())

  private def flush(): Unit =
    renderScheduled = false
    drainRenderAndLayout()
    if passiveEffects.nonEmpty then requestPassive()

  private def requestPassive(): Unit =
    if !passiveScheduled then
      passiveScheduled = true
      MacrotaskExecutor.execute(() => drainPassive())

  // Re-render dirty components and run layout effects, repeating while either a
  // layout effect or a re-render produced more work — so a layout effect's own
  // state update commits before paint, in the same flush.
  private def drainRenderAndLayout(): Unit =
    var pass = 0
    while (dirty.nonEmpty || layoutEffects.nonEmpty) && pass < MaxFlushPasses do
      runRenderPass()
      runEffects(layoutEffects)
      pass += 1

  private def runRenderPass(): Unit =
    if dirty.nonEmpty then
      val batch = dirty.toArray
      dirty.clear()
      batch.sortInPlaceBy(_.depth)
      var i = 0
      while i < batch.length do
        val inst = batch(i)
        if inst.mounted && inst.dirty then Reconciler.rerender(inst)
        i += 1

  private def drainPassive(): Unit =
    passiveScheduled = false
    runEffects(passiveEffects)

  // Run a buffer of effects deepest-first. Each cell's previous cleanup runs
  // before its new body; cells whose component has unmounted are skipped (their
  // teardown is handled by the unmount path instead).
  private def runEffects(buf: mutable.ArrayBuffer[EffectCell]): Unit =
    if buf.nonEmpty then
      val cells = buf.toArray
      buf.clear()
      cells.sortInPlaceBy(c => -c.owner.depth)
      var i = 0
      while i < cells.length do
        val cell = cells(i)
        cell.queued = false
        if cell.owner.mounted then
          val old = cell.cleanup
          if old != null then
            cell.cleanup = null
            old()
          cell.cleanup = cell.pendingBody()
        i += 1
