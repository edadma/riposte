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
// writable atoms stays writable. Like Jotai's `atomFamily`.
//
//   val itemAtom = atomFamily((id: Int) => atom(id * 10))
//   itemAtom(1) eq itemAtom(1)   // same atom
def atomFamily[P, T <: Atom[?]](make: P => T): P => T =
  val cache = mutable.Map.empty[P, T]
  p => cache.getOrElseUpdate(p, make(p))

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
