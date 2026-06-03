package io.github.edadma.riposte.atoms

import org.scalajs.dom
import scala.collection.mutable

// Derived-atom utilities and lifecycle helpers layered on the core primitives.

// A read-only view of part of another atom. Because a derived atom only notifies
// when its computed value actually changes (`!=`), `selectAtom` bails out for free
// when the selected slice is unchanged — the basis for "subscribe to one field".
//
//   val user = atom(User("ada", 36))
//   val name = selectAtom(user, _.name)   // re-notifies only when the name changes
def selectAtom[A, B](source: Atom[A], f: A => B): Atom[B] =
  atom(g => f(g(source)))

// A parameterised family of atoms: `make` builds an atom for a parameter, and the
// family memoises by parameter so the same `p` always yields the same atom (and
// thus the same shared state). The atom's static type is preserved, so a family of
// writable atoms stays writable. It is a `P => T`, so it is still usable anywhere a
// plain function of the parameter is expected. Like Jotai's `atomFamily`, plus an
// eviction surface (`remove`/`clear`/`keys`) a cache needs to garbage-collect
// entries — `remove` only drops the family's memo, so pair it with `Store.forget`
// to also discard the atom's value from a store.
//
//   val itemAtom = atomFamily((id: Int) => atom(id * 10))
//   itemAtom(1) eq itemAtom(1)   // same atom
//   itemAtom.remove(1)           // a later itemAtom(1) builds a fresh atom
final class AtomFamily[P, T <: Atom[?]] private[atoms] (make: P => T) extends (P => T):
  private val cache = mutable.Map.empty[P, T]

  // The atom for `p`, created once and memoised so the same `p` always yields the
  // same shared state.
  def apply(p: P): T = cache.getOrElseUpdate(p, make(p))

  // Whether an atom has already been created for `p`.
  def contains(p: P): Boolean = cache.contains(p)

  // The parameters that currently have an atom, as a snapshot so the caller may
  // evict while iterating over them.
  def keys: Vector[P] = cache.keys.toVector

  // Forget the atom for `p` so a later `apply(p)` builds a fresh one. This drops
  // only the family's memo; evict the atom's value from a store with `Store.forget`.
  def remove(p: P): Unit = cache -= p

  // Forget every parameter.
  def clear(): Unit = cache.clear()

def atomFamily[P, T <: Atom[?]](make: P => T): AtomFamily[P, T] = new AtomFamily(make)

// Attach a lifecycle hook to a writable atom. The hook runs the first time the
// atom gains a listener (a component starts reading it), and is handed a `setSelf`
// that writes the atom. It may return a cleanup that runs when the last listener
// goes away. Returns the atom, so it chains. Mirrors Jotai's `atom.onMount`.
//
//   onMount(clock) { setSelf =>
//     val id = dom.window.setInterval(() => setSelf(now()), 1000)
//     Some(() => dom.window.clearInterval(id))
//   }
def onMount[A, W](a: WritableAtom[A, W])(hook: (W => Unit) => Option[() => Unit]): WritableAtom[A, W] =
  a.mountHook = Some(hook.asInstanceOf[(Any => Unit) => Option[() => Unit]])
  a

// An atom persisted to `localStorage` under `key`. Writes are mirrored to storage;
// the stored value (if any) is loaded the first time the atom is observed, via the
// `onMount` lifecycle, so an unused atom touches neither storage nor the DOM. The
// codec maps the value to and from the stored string.
def atomWithStorage[A](key: String, default: A, encode: A => String, decode: String => A): WritableAtom[A, A] =
  val base = atom(default)
  val wrapped = atom[A, A](
    read = g => g(base),
    write = (_, set, v) =>
      set(base, v)
      dom.window.localStorage.setItem(key, encode(v)),
  )
  onMount(wrapped) { setSelf =>
    val raw = dom.window.localStorage.getItem(key)
    if raw != null then setSelf(decode(raw))
    None
  }
  wrapped

// A string-valued storage atom, the common case — no codec needed.
def atomWithStorage(key: String, default: String): WritableAtom[String, String] =
  atomWithStorage(key, default, identity, identity)
