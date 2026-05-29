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

final case class VText(text: String) extends VNode

final case class VElement(
    tag:      String,
    props:    Map[String, Prop],
    children: Vector[VNode],
    key:      Option[String],
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
