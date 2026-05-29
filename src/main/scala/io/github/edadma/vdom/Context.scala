package io.github.edadma.vdom

// A typed context handle. Create one with `createContext(default)`, wrap a
// subtree with `ctx.provide(value, child)`, and read the nearest provided value
// (or `default`) with `useContext(ctx)`.
//
//   val Theme = createContext("light")
//   Theme.provide("dark", App())          // inside App, useContext(Theme) == "dark"
final class Context[T](val default: T):
  // Components that read this context via useContext. When a provider's value
  // changes, these are re-rendered directly — necessary so consumers sitting
  // below a memoized (bailed-out) ancestor still see the new value.
  private[vdom] val subscribers = scala.collection.mutable.HashSet.empty[ComponentInstance[?]]

  def provide(value: T, child: VNode): VNode = VProvider(this, value, child)

def createContext[T](default: T): Context[T] = new Context[T](default)
