---
title: "State with Atoms"
weight: 3
---

`useState` is enough for state owned by one component. When state needs to be **shared**
across components that aren't in a simple parent-child line — and you'd rather not thread
props through every layer between them — reach for **riposte-atoms**: a Jotai-inspired
model of small, independent, identity-based units of state.

Add the dependency (it pulls in the core transitively):

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-atoms" % "0.0.1"

import io.github.edadma.riposte.atoms.*
```

## Atoms

An atom is a *handle* to a unit of state — a key, not a container. The value lives in a
`Store`; the atom just identifies it. A **primitive** atom is seeded with a value:

```scala
val countAtom = atom(0)   // WritableAtom[Int]
```

A **derived** atom computes its value from the atoms it reads, and recomputes
automatically whenever any of those change. The `get` it receives both reads a dependency
and records it:

```scala
val doubledAtom = atom(get => get(countAtom) * 2)        // read-only
val sumAtom     = atom(get => get(aAtom) + get(bAtom))   // tracks both
```

Because dependencies are recorded per evaluation, an atom that reads different atoms on
different runs is tracked correctly each time.

## Using atoms in components

Three hooks connect atoms to components, mirroring `useState`:

- `useAtom(a)` → `(value, set)` — read **and** write.
- `useAtomValue(a)` → `value` — read only.
- `useSetAtom(a)` → `set` — write only (doesn't subscribe, so the component doesn't
  re-render when the atom changes).

```scala
val Counter = view {
  val (count, setCount) = useAtom(countAtom)
  val doubled           = useAtomValue(doubledAtom)

  div(
    p(s"count: $count, doubled: $doubled"),
    button(onClick := (_ => setCount(count + 1)), "Increment"),
  )
}
```

Reading an atom subscribes the component to *just that atom*: it re-renders when that
atom's value changes and not otherwise. So `Counter` above re-renders when `countAtom`
changes; `doubledAtom` recomputes and its readers re-render too; a component that reads
neither is untouched. This fine-grained subscription is automatic — no selectors required
for the common case.

## Stores and scoping

Every atom hook resolves its store from the nearest `StoreProvider` ancestor, falling back
to the process-wide `Store.default`. For most apps the default is all you need, and the
examples above work with no setup.

To give a subtree its **own** isolated state — for tests, for resetting a region, or for
rendering the same UI against different data — create a store with `new Store` and wrap the
subtree in a `StoreProvider`. The same atoms then hold independent values under each
provider:

```scala
val App = view {
  StoreProvider(new Store) {
    Counter()
  }
}
```

## More atom kinds

Beyond primitive and read-only derived atoms, the module provides:

**Writable-derived atoms** — a derived value that also knows how to be written, by fanning
the write out to the atoms that back it:

```scala
val celsiusAtom = atom(0.0)
val fahrenheitAtom = atom(
  read  = get => get(celsiusAtom) * 9 / 5 + 32,
  write = (get, set, f: Double) => set(celsiusAtom, (f - 32) * 5 / 9),
)
```

**Action atoms** — a write-only "command" that carries no readable value but mutates
several atoms at once when dispatched:

```scala
val resetAll = action((get, set, _: Unit) => {
  set(countAtom, 0)
  set(celsiusAtom, 0.0)
})
// useSetAtom(resetAll) gives you a dispatcher
```

**`selectAtom(a, f)`** — derive a narrowed slice of a larger atom that only notifies when
*that slice* changes, so readers don't re-render on unrelated parts of the source.

**`atomFamily(make)`** — a function from a parameter to an atom, memoized so the same
parameter always yields the same atom. Use it for per-id state (one atom per row, per
user, …).

**`atomWithStorage(key, default)`** — a primitive atom that persists to `localStorage`
under `key` and re-hydrates on load. A typed overload takes `encode`/`decode` for
non-string values.

**`atomLoadable(future)`** — wraps an async computation as an atom whose value is a
`Loadable` with loading / data / error states, so a component can render each phase
without manual effect plumbing.

Next: [Routing](/guide/routing/).
