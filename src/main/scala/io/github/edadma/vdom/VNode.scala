package io.github.edadma.vdom

import org.scalajs.dom

// Immutable descriptions of UI. The application builds a VNode tree from its
// current state; the reconciler diffs the new tree against the live Instance
// tree (which mirrors the real DOM) and mutates the DOM to match.
//
//   VText     — a text node
//   VElement  — a DOM element with props and children
//   VFragment — a transparent group of siblings (no wrapper element)
//   VComponent— a function-component instance: a stable component + its props
//   VEmpty    — renders nothing (a positional placeholder)
sealed trait VNode

// A bare String or Int used where a VNode is expected becomes a text node — so
// a string can be passed straight to a component's children (`Card("hello")`)
// or to `when(cond)("text")`, mirroring how strings already work as element
// children. (Element children go through `Mod`'s own String/Int conversions; an
// implicit conversion is never chained, so these don't collide with those.)
object VNode:
  given Conversion[String, VNode] = VText(_)
  given Conversion[Int, VNode]    = i => VText(i.toString)

final case class VText(text: String) extends VNode

final case class VElement(
    tag:      String,
    props:    Map[String, Prop],
    children: Vector[VNode],
    key:      Option[String],
    ref:      ElementRef | Null = null,
) extends VNode

final case class VFragment(
    children: Vector[VNode],
    key:      Option[String] = None,
) extends VNode

final case class VComponent[P](
    component: Component[P],
    props:     P,
    key:       Option[String],
) extends VNode

// Supplies a context value to its subtree. `useContext(ctx)` anywhere inside
// `child` reads this value (the nearest enclosing provider wins); outside any
// provider it reads the context's default. Built via `ctx.provide(value, …)`.
final case class VProvider[T](ctx: Context[T], value: T, child: VNode) extends VNode

case object VEmpty extends VNode

// A property attached to a VElement. The reconciler decides how each kind
// reaches the DOM: most attributes go through setAttribute, a few well-known
// names (value, checked) are set as live properties, handlers become event
// listeners, and styles are written onto element.style.
sealed trait Prop

final case class Attr(value: String)              extends Prop
final case class BoolAttr(value: Boolean)         extends Prop
final case class Handler(fn: dom.Event => Unit)   extends Prop
final case class StyleProp(decls: Map[String, String]) extends Prop

// Binds a VElement to its live DOM node. `attach` runs once the element is
// created (on mount) with the real node; `detach` runs on unmount, and before a
// re-attach when an element's ref identity changes across a patch. The node is
// the same object for the life of an ElementInstance — a same-tag patch reuses
// it — so a stable ref sees a stable node.
sealed trait ElementRef:
  private[vdom] def attach(node: dom.Element): Unit
  private[vdom] def detach(): Unit

// A ref backed by a `useRef` box: the live node is written into `.current` on
// mount and cleared to null on unmount. Declare the box's type to include null,
// e.g. `useRef[dom.html.Input | Null](null)`, so `current` can hold both. These
// are case classes so that wrapping the same box (or function) compares equal:
// a patch re-binds only when the underlying handle actually changes, so a stable
// `useRef` box never churns, while an inline callback — a fresh function each
// render — re-runs, matching React.
private[vdom] final case class BoxRef[T](box: Ref[T]) extends ElementRef:
  def attach(node: dom.Element): Unit = box.current = node.asInstanceOf[T]
  def detach(): Unit                  = box.current = null.asInstanceOf[T]

// A callback ref: invoked with the node on mount and with null on unmount —
// for running code (focus, measure, observers) as the element comes and goes.
private[vdom] final case class FnRef(fn: (dom.Element | Null) => Unit) extends ElementRef:
  def attach(node: dom.Element): Unit = fn(node)
  def detach(): Unit                  = fn(null)
