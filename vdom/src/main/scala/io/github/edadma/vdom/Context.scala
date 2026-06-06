package io.github.edadma.vdom

// A typed context handle. Create one with `createContext(default)`, wrap a
// subtree with `ctx.provide(value, child)`, and read the nearest provided value
// (or `default`) with `useContext(ctx)`.
//
//   val Theme = createContext("light")
//   Theme.provide("dark", App())          // inside App, useContext(Theme) == "dark"
final class Context[T](val default: T):
  def provide(value: T, child: VNode): VNode = VProvider(this, value, child)

def createContext[T](default: T): Context[T] = new Context[T](default)
