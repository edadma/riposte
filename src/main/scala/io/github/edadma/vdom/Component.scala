package io.github.edadma.vdom

// A function component. The render body is a context function `Hooks ?=> VNode`:
// the Hooks context is supplied implicitly by the reconciler, so the body calls
// the top-level `useState` / `useEffect` / … functions directly without naming
// or threading a `hooks` parameter.
//
// A `Component` is a stable value created once (e.g. `val Counter = view { … }`);
// the reconciler matches mounted instances by component identity (reference
// equality) plus key, so the SAME `Component` value across renders means
// "update in place", a different one means "unmount and remount".
//
// Props travel as plain data on each `VComponent`; hook state lives on the
// mounted instance and survives re-renders.
final class Component[P](
    val render:   P => (Hooks ?=> VNode),
    val memoized: Boolean = false,
):
  def apply(props: P):              VNode = VComponent(this, props, None)
  def apply(props: P, key: String): VNode = VComponent(this, props, Some(key))

// Build a component that takes props.
//
//   val Greeting = component[String] { who =>
//     div(s"Hello, $who")          // hooks usable here too, implicitly
//   }
//   Greeting("world")
def component[P](render: P => (Hooks ?=> VNode)): Component[P] =
  new Component(render)

// A no-props component. Call it with `Counter()`.
//
//   val Counter = view {
//     val (n, _, update) = useState(0)
//     button(onClick := (_ => update(_ + 1)), s"clicked $n")
//   }
def view(render: Hooks ?=> VNode): Component[Unit] =
  new Component(_ => render)

extension (c: Component[Unit]) def apply(): VNode = c.apply(())

// Wrap a component so it bails out of a parent-driven re-render when its new
// props are equal (`==`) to the previous ones — React.memo. The component still
// re-renders on its own state changes and when a context it consumes changes.
//
// Define the memoized component once as a stable value; don't call `memo`
// inline in a render, or each render produces a new identity and remounts.
//
//   val Row = memo(component[RowProps] { props => … })
//
// Note: props that contain freshly-allocated closures compare unequal each
// render and defeat the bailout — stabilize handlers with `useCallback`.
def memo[P](c: Component[P]): Component[P] =
  new Component(c.render, memoized = true)

// --- multi-argument components ---------------------------------------------
//
// For a component with several props, these arities let you pass positional
// arguments — `Stat("Clicks", n)` — instead of bundling them into one value.
// Under the hood the args are stored as a tuple, so component identity and
// `memo` (structural `==` on the tuple) behave exactly as for one prop.
//
//   val Stat = component[String, Int] { (label, value) =>
//     div(span(label), strong(value))
//   }
//   Stat("Clicks", clicks)
//
// When you'd rather have field names without declaring a case class, pass a
// single named tuple to `component[P]` instead:
//
//   val Card = component[(title: String, count: Int)] { p =>
//     div(span(p.title), strong(p.count))
//   }
//   Card((title = "Hi", count = 3))

final class Component2[A, B] private[vdom] (private[vdom] val underlying: Component[(A, B)]):
  def apply(a: A, b: B): VNode              = underlying((a, b))
  def apply(a: A, b: B, key: String): VNode = underlying((a, b), key)

final class Component3[A, B, C] private[vdom] (private[vdom] val underlying: Component[(A, B, C)]):
  def apply(a: A, b: B, c: C): VNode              = underlying((a, b, c))
  def apply(a: A, b: B, c: C, key: String): VNode = underlying((a, b, c), key)

final class Component4[A, B, C, D] private[vdom] (private[vdom] val underlying: Component[(A, B, C, D)]):
  def apply(a: A, b: B, c: C, d: D): VNode              = underlying((a, b, c, d))
  def apply(a: A, b: B, c: C, d: D, key: String): VNode = underlying((a, b, c, d), key)

def component[A, B](render: (A, B) => (Hooks ?=> VNode)): Component2[A, B] =
  new Component2(new Component[(A, B)](t => render(t._1, t._2)))

def component[A, B, C](render: (A, B, C) => (Hooks ?=> VNode)): Component3[A, B, C] =
  new Component3(new Component[(A, B, C)](t => render(t._1, t._2, t._3)))

def component[A, B, C, D](render: (A, B, C, D) => (Hooks ?=> VNode)): Component4[A, B, C, D] =
  new Component4(new Component[(A, B, C, D)](t => render(t._1, t._2, t._3, t._4)))

def memo[A, B](c: Component2[A, B]): Component2[A, B] =
  new Component2(memo(c.underlying))

def memo[A, B, C](c: Component3[A, B, C]): Component3[A, B, C] =
  new Component3(memo(c.underlying))

def memo[A, B, C, D](c: Component4[A, B, C, D]): Component4[A, B, C, D] =
  new Component4(memo(c.underlying))
