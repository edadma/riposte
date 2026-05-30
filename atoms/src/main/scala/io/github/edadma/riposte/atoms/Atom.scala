package io.github.edadma.riposte.atoms

// A unit of shared state, identified by reference — like Jotai's atom. An atom
// holds no value itself; values live in a `Store`, so the same atom read from two
// components is the same piece of state. Create one with `atom(value)` for a
// writable primitive, `atom(get => …)` for a read-only derived value, or
// `atom(read, write)` for a derived value that also knows how to be written.
sealed trait Atom[A]:
  // An optional lifecycle hook (see `onMount`). The Store invokes it the first
  // time the atom gains a listener, passing a `setSelf` that writes the atom in
  // that store; the hook may return a cleanup run when the last listener leaves.
  // Stored erased (setSelf and the write type are existential here) and cast back
  // at the call site; only writable atoms can carry one, so `setSelf` is sound.
  private[atoms] var mountHook: Option[(Any => Unit) => Option[() => Unit]] = None

// An atom whose value is computed from the atoms its `compute` reads. Both the
// read-only and writable-derived kinds compute the same way, so the Store treats
// them uniformly through this trait.
sealed trait Computed[A] extends Atom[A]:
  private[atoms] def compute: Get => A

// An atom that can be written. Reading yields an `A`; writing takes a `W` (the
// same type for a primitive, but a derived atom may accept a different write
// argument than it reads). A primitive is the simplest case: read and write are
// the same value.
sealed trait WritableAtom[A, W] extends Atom[A]

// The read accessor handed to a derived atom's compute function. `get(dep)`
// returns dep's current value AND records dep as a dependency, so the derived
// atom recomputes whenever dep changes. Dependencies are tracked per evaluation,
// so a derived atom that reads different atoms on different runs is handled too.
trait Get:
  def apply[B](a: Atom[B]): B

// The write accessor handed to a writable-derived atom's `write` function. It
// reads (without tracking) and writes other atoms, so a derived write can fan out
// to the primitives that actually back it.
trait SetFn:
  def apply[B, X](a: WritableAtom[B, X], value: X): Unit

// A writable atom holding a value directly. It is writable with its own value
// type, hence `WritableAtom[A, A]`.
final class PrimitiveAtom[A] private[atoms] (private[atoms] val initial: A) extends WritableAtom[A, A]

// A read-only atom whose value is computed from the atoms its `compute` reads.
final class DerivedAtom[A] private[atoms] (val compute: Get => A) extends Computed[A]

// A derived atom that also knows how to be written: `compute` derives its value
// from other atoms, and `write` is invoked on a write to push the change back
// down to whatever atoms back it.
final class WritableDerivedAtom[A, W] private[atoms] (
    val compute:                  Get => A,
    private[atoms] val write:     (Get, SetFn, W) => Unit,
) extends Computed[A]
    with WritableAtom[A, W]

// Create a writable primitive atom.
def atom[A](initial: A): PrimitiveAtom[A] = new PrimitiveAtom(initial)

// Create a read-only derived atom. The lambda's `Get` parameter distinguishes
// this overload from the primitive one.
def atom[A](compute: Get => A): DerivedAtom[A] = new DerivedAtom(compute)

// Create a writable-derived atom: it reads as an `A` via `read`, and a write of a
// `W` runs `write`, which can read and set other atoms. The distinct arity (two
// function arguments) sets this overload apart from the one-argument ones.
def atom[A, W](read: Get => A, write: (Get, SetFn, W) => Unit): WritableDerivedAtom[A, W] =
  new WritableDerivedAtom(read, write)

// Create a write-only action atom: it carries no readable value (it reads as
// `Unit`), but dispatching a `W` runs `write`. Useful for "commands" that mutate
// several atoms at once without being a value themselves.
def action[W](write: (Get, SetFn, W) => Unit): WritableAtom[Unit, W] =
  new WritableDerivedAtom[Unit, W](_ => (), write)
