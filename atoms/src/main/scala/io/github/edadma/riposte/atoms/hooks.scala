package io.github.edadma.riposte.atoms

import io.github.edadma.riposte.*

// Hooks that connect atoms to components. They are thin wrappers over the core's
// `useSyncExternalStore`: reading an atom subscribes the component to just that
// atom, so it re-renders only when that atom's value changes — fine-grained by
// construction, no selectors required. `subscribe` is stabilised with
// `useCallback` keyed on atom identity (and the resolved store), so a stable atom
// in a stable store never re-subscribes.
//
// The store is resolved from the ambient `StoreContext` (the global
// `Store.default` unless a `StoreProvider` scopes the subtree), so the same atom
// holds independent values under different providers.

// Read an atom's value, subscribing this component to it.
def useAtomValue[A](a: Atom[A])(using Hooks): A =
  val store     = useContext(StoreContext)
  val subscribe = useCallback((cb: () => Unit) => store.sub(a, cb), Array(a, store))
  useSyncExternalStore(subscribe, () => store.get(a))

// A stable dispatcher for any writable atom. Write-only: it does not subscribe,
// so a component that only writes an atom doesn't re-render when the atom changes.
// For a primitive `W` is the value type; for a writable-derived or action atom it
// is whatever the atom's `write` accepts.
def useSetAtom[A, W](a: WritableAtom[A, W])(using Hooks): W => Unit =
  val store = useContext(StoreContext)
  useCallback((arg: W) => store.set(a, arg), Array(a, store))

// Read and write a writable atom — useState's shape, but the state is shared.
def useAtom[A, W](a: WritableAtom[A, W])(using Hooks): (A, W => Unit) =
  (useAtomValue(a), useSetAtom(a))
