package io.github.edadma.riposte.atoms

import io.github.edadma.riposte.*

// A scoped store, layered on the core's context. By default the atom hooks read
// `Store.default` (a single process-wide graph). Wrapping a subtree in a
// `StoreProvider` gives that subtree its own `Store`, so the same atoms hold
// independent values per provider — useful for isolation in tests, for resetting
// a region of state, or for rendering the same UI against distinct data.
val StoreContext: Context[Store] = createContext(Store.default)

// Provide a store to a subtree. Atom hooks rendered under `child` resolve their
// store from here instead of the global default.
def StoreProvider(store: Store)(child: VNode): VNode = StoreContext.provide(store, child)
