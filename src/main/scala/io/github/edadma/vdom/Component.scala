package io.github.edadma.vdom

// A function component. The render body is a context function `Hooks ?=> VNode`:
// the Hooks context is supplied implicitly by the reconciler, so the body calls
// the top-level `useState` / `useEffect` / … functions directly without naming
// or threading a `hooks` parameter.
//
// A `Component` is a stable value created once (e.g.
// `val Counter = view("Counter") { … }`); the reconciler matches mounted
// instances by component identity plus key, so the SAME `Component` value across
// renders means "update in place", a different one means "unmount and remount".
//
// Props travel as plain data on each `VComponent`; hook state lives on the
// mounted instance and survives re-renders.
final class Component[P](
    val name:     String,
    val render:   P => (Hooks ?=> VNode),
    val memoized: Boolean = false,
):
  def apply(props: P):              VNode = VComponent(this, props, None)
  def apply(props: P, key: String): VNode = VComponent(this, props, Some(key))

// Build a component that takes props.
//
//   val Greeting = component[String]("Greeting") { who =>
//     div(s"Hello, $who")          // hooks usable here too, implicitly
//   }
//   Greeting("world")
def component[P](name: String)(render: P => (Hooks ?=> VNode)): Component[P] =
  new Component(name, render)

// A no-props component. Call it with `Counter()`.
//
//   val Counter = view("Counter") {
//     val (n, _, update) = useState(0)
//     button(onClick := (_ => update(_ + 1)), s"clicked $n")
//   }
def view(name: String)(render: Hooks ?=> VNode): Component[Unit] =
  new Component(name, _ => render)

extension (c: Component[Unit]) def apply(): VNode = c.apply(())

// Wrap a component so it bails out of a parent-driven re-render when its new
// props are equal (`==`) to the previous ones — React.memo. The component still
// re-renders on its own state changes and when a context it consumes changes.
//
// Define the memoized component once as a stable value; don't call `memo`
// inline in a render, or each render produces a new identity and remounts.
//
//   val Row = memo(component[RowProps]("Row") { props => … })
//
// Note: props that contain freshly-allocated closures compare unequal each
// render and defeat the bailout — stabilize handlers with `useCallback`.
def memo[P](c: Component[P]): Component[P] =
  new Component(c.name, c.render, memoized = true)
