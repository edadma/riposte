---
title: "State with Atoms"
weight: 3
---

# State with Atoms

`useState` is enough for state that lives in one component. When state needs to be shared
across components without threading props through every layer, reach for **riposte-atoms**
— a Jotai-inspired model of small, identity-based units of state.

Add the dependency (it pulls in the core transitively):

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-atoms" % "0.0.1"
```

## Atoms

An atom is a handle to a unit of state. A primitive atom is seeded with a value; a
derived atom computes from the atoms it reads and recomputes when any of them changes:

```scala
import io.github.edadma.riposte.atoms.*

val countAtom    = atom(0)                      // WritableAtom[Int]
val doubledAtom  = atom(get => get(countAtom) * 2)  // derived, read-only
```

Atoms carry no state themselves — they are keys. The actual values and the dependency
graph live in a `Store`.

## Using atoms in components

The atom hooks mirror `useState`. `useAtom` returns the current value and a setter;
`useAtomValue` reads only; `useSetAtom` writes only. They need a `Store` in scope,
provided by `StoreProvider` near the root of your app:

```scala
def Counter(using Hooks, Store): VNode =
  val (count, setCount) = useAtom(countAtom)
  val doubled           = useAtomValue(doubledAtom)

  div(
    p(s"count: $count, doubled: $doubled"),
    button(onClick := (_ => setCount(count + 1)), "Increment"),
  )

def App(using Hooks): VNode =
  StoreProvider(
    Counter,
  )
```

Any component reading `countAtom` re-renders when it changes; `doubledAtom` recomputes
and its readers re-render too. Components that read neither are untouched.

## More atom kinds

The module also provides writable-derived atoms (custom read and write), `selectAtom`
for narrowing a slice with bail-out, `atomFamily` for parameterized atoms, `onMount`
hooks, `atomWithStorage` for persistence to `localStorage`, and `atomLoadable` for
async values that expose a `Loadable` (loading / data / error).

Next: [Routing](/guide/routing/).
