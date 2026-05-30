package io.github.edadma.riposte.atoms

// A unit of shared state, identified by reference — like Jotai's atom. An atom
// holds no value itself; values live in a `Store`, so the same atom read from two
// components is the same piece of state. Create one with `atom(value)` for a
// writable primitive, or `atom(get => …)` for a read-only value derived from
// other atoms.
sealed trait Atom[A]

// The read accessor handed to a derived atom's compute function. `get(dep)`
// returns dep's current value AND records dep as a dependency, so the derived
// atom recomputes whenever dep changes. Dependencies are tracked per evaluation,
// so a derived atom that reads different atoms on different runs is handled too.
trait Get:
  def apply[B](a: Atom[B]): B

// A writable atom holding a value directly.
final class PrimitiveAtom[A] private[atoms] (private[atoms] val initial: A) extends Atom[A]

// A read-only atom whose value is computed from the atoms its `compute` reads.
final class DerivedAtom[A] private[atoms] (private[atoms] val compute: Get => A) extends Atom[A]

// Create a writable primitive atom.
def atom[A](initial: A): PrimitiveAtom[A] = new PrimitiveAtom(initial)

// Create a read-only derived atom. The lambda's `Get` parameter distinguishes
// this overload from the primitive one.
def atom[A](compute: Get => A): DerivedAtom[A] = new DerivedAtom(compute)
