package io.github.edadma.riposte.atoms

import scala.collection.mutable

// The reactive graph backing a set of atoms. For each atom it tracks the cached
// value, the atoms it depends on, the atoms that depend on it, and the listeners
// watching it. Reads are lazy and memoised; a write to a primitive recomputes
// exactly the dependent atoms whose value can change and notifies only the
// watchers whose atom's value actually changed.
//
// Atoms are compared by identity (they define no `equals`), so the maps below are
// effectively identity-keyed.
final class Store:

  private final class State(
      var value:       Any,
      var valid:       Boolean,
      var deps:        mutable.Set[Atom[?]],
      val dependents:  mutable.Set[Atom[?]],
      val listeners:   mutable.Set[() => Unit],
  )

  private val states = mutable.Map.empty[Atom[?], State]

  private def stateOf(a: Atom[?]): State =
    states.getOrElseUpdate(
      a,
      a match
        case p: PrimitiveAtom[?] =>
          new State(p.initial, valid = true, mutable.Set.empty, mutable.Set.empty, mutable.Set.empty)
        case _: Computed[?] =>
          new State(null, valid = false, mutable.Set.empty, mutable.Set.empty, mutable.Set.empty),
    )

  // The current value of `a`. A computed atom (read-only or writable-derived) is
  // computed on demand and cached, and its dependencies are (re)recorded as a side
  // effect of computing it.
  def get[A](a: Atom[A]): A =
    val st = stateOf(a)
    a match
      case _: PrimitiveAtom[?] => ()
      case d: Computed[?]      => if !st.valid then recompute(d, st)
    st.value.asInstanceOf[A]

  private def recompute(d: Computed[?], st: State): Unit =
    // Drop the stale reverse edges before re-recording fresh ones.
    st.deps.foreach(dep => states.get(dep).foreach(_.dependents -= d))
    val newDeps = mutable.Set.empty[Atom[?]]
    val getter = new Get:
      def apply[B](dep: Atom[B]): B =
        newDeps += dep
        stateOf(dep).dependents += d
        get(dep)
    st.value = d.asInstanceOf[Computed[Any]].compute(getter)
    st.deps  = newDeps
    st.valid = true

  // Write any writable atom. A primitive is written directly; a writable-derived
  // (or action) atom runs its `write`, which reads and sets the atoms backing it.
  // The casts dodge GADT refinement of the existential `?` types — sound for this
  // internal call, since the matched runtime class fixes the type parameters.
  def set[A, W](a: WritableAtom[A, W], arg: W): Unit =
    a match
      case p: PrimitiveAtom[?] =>
        setPrimitive(p.asInstanceOf[PrimitiveAtom[Any]], arg)
      case w: WritableDerivedAtom[?, ?] =>
        // The getter reads without recording dependencies — a write is not an
        // evaluation of `w`, so it must not perturb the dependency graph.
        val getter = new Get:
          def apply[B](x: Atom[B]): B = get(x)
        val setter = new SetFn:
          def apply[B, X](t: WritableAtom[B, X], v: X): Unit = set(t, v)
        w.asInstanceOf[WritableDerivedAtom[Any, Any]].write(getter, setter, arg)

  // Write a primitive atom and propagate. Transitive dependents are invalidated;
  // then each atom that has a watcher is recomputed and its watchers fired only
  // if its value actually changed — so a derived atom whose value is unaffected
  // (e.g. `g(n) % 2` when `n` goes 2→4) does not wake its consumers.
  private def setPrimitive[A](a: PrimitiveAtom[A], value: A): Unit =
    val st = stateOf(a)
    if st.value == value then return

    val affected = mutable.LinkedHashSet.empty[Atom[?]]
    collectDependents(a, affected)

    val olds = mutable.Map.empty[Atom[?], Any]
    olds(a) = st.value
    affected.foreach(x => olds(x) = states(x).value)

    st.value = value
    affected.foreach(x => states(x).valid = false)

    val toNotify = mutable.LinkedHashSet.empty[() => Unit]
    st.listeners.foreach(toNotify += _) // the primitive itself changed by definition
    affected.foreach { x =>
      val xs = states(x)
      if xs.listeners.nonEmpty then
        if get(x) != olds(x) then xs.listeners.foreach(toNotify += _)
    }
    toNotify.foreach(_())

  private def collectDependents(a: Atom[?], acc: mutable.LinkedHashSet[Atom[?]]): Unit =
    states.get(a).foreach(_.dependents.foreach { d =>
      if !acc.contains(d) then
        acc += d
        collectDependents(d, acc)
    })

  // Watch `a` for value changes. Computing it first ensures its dependency edges
  // exist, so a later write to an underlying primitive knows to reach it. Returns
  // an unsubscribe.
  def sub(a: Atom[?], listener: () => Unit): () => Unit =
    val st = stateOf(a)
    if !st.valid then get(a.asInstanceOf[Atom[Any]])
    st.listeners += listener
    () => st.listeners -= listener

// The process-wide default store, used by the hooks unless a scoped store is
// introduced later.
object Store:
  val default: Store = new Store
