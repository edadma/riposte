package io.github.edadma.riposte

import org.scalajs.dom

// The live tree that mirrors the real DOM. Every VNode that gets mounted
// produces an Instance; the reconciler keeps Instances across renders and
// mutates them (and their backing DOM nodes) in place.
//
// A key invariant makes positioning simple: EVERY instance always owns at
// least one real DOM node. Empty renders a comment, and a fragment carries a
// trailing comment "anchor", so `firstDomNode` / `lastDomNode` are never null
// and a fragment's children always have a stable node to be inserted before.
sealed abstract class Instance:
  var vnode:   VNode
  var parent:  Instance | Null = null
  var depth:   Int             = 0
  var mounted: Boolean         = true

  // The first and last top-level DOM nodes this instance contributes, in
  // document order. For multi-node instances (fragments, and components that
  // render fragments) these bracket the whole block.
  def firstDomNode: dom.Node
  def lastDomNode:  dom.Node

  // Append, in order, every top-level DOM node this instance contributes.
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit

  final def domNodes: List[dom.Node] =
    val b = List.newBuilder[dom.Node]
    collectDomNodes(b)
    b.result()

final class TextInstance(var vnode: VNode, val node: dom.Text) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit = buf += node

final class EmptyInstance(var vnode: VNode, val node: dom.Comment) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit = buf += node

final class ElementInstance(
    var vnode:    VNode,
    val node:     dom.Element,
    var children: Vector[Instance],
    var listeners: Map[String, scalajs.js.Function1[dom.Event, Unit]],
) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit = buf += node

final class FragmentInstance(
    var vnode:    VNode,
    var children: Vector[Instance],
    val anchor:   dom.Comment,
) extends Instance:
  def firstDomNode = if children.nonEmpty then children.head.firstDomNode else anchor
  def lastDomNode  = anchor
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit =
    children.foreach(_.collectDomNodes(buf))
    buf += anchor

final class ProviderInstance(
    var vnode: VNode,
    val ctx:   Context[?],
    var value: Any,
    var child: Instance | Null,
) extends Instance:
  def firstDomNode = child.asInstanceOf[Instance].firstDomNode
  def lastDomNode  = child.asInstanceOf[Instance].lastDomNode
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit =
    child.asInstanceOf[Instance].collectDomNodes(buf)

// A portal: its `child` is mounted under a foreign `target` container, but a
// comment `anchor` holds this instance's slot in the main tree. Crucially,
// `collectDomNodes` reports ONLY the anchor — the child's nodes are not here, so
// a parent diff positioning this slot must never try to move them.
final class PortalInstance(
    var vnode:  VNode,
    val anchor: dom.Comment,
    var child:  Instance | Null,
) extends Instance:
  def firstDomNode = anchor
  def lastDomNode  = anchor
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit = buf += anchor

// An error boundary: its single child is either the real subtree or, once a
// render in that subtree has thrown, the fallback. `errored` records which is
// currently shown, so a re-render knows whether to retry the real child.
final class ErrorBoundaryInstance(
    var vnode:    VNode,
    var child:    Instance | Null,
    var errored:  Boolean,
) extends Instance:
  def firstDomNode = child.asInstanceOf[Instance].firstDomNode
  def lastDomNode  = child.asInstanceOf[Instance].lastDomNode
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit =
    child.asInstanceOf[Instance].collectDomNodes(buf)

final class ComponentInstance[P](
    var vnode:     VNode,
    val component: Component[P],
    val hooks:     Hooks,
    var rendered:  Instance | Null,
    var props:     P,
) extends Instance:
  // True while sitting in the scheduler's dirty set, awaiting re-render.
  var dirty: Boolean = false

  def firstDomNode = rendered.asInstanceOf[Instance].firstDomNode
  def lastDomNode  = rendered.asInstanceOf[Instance].lastDomNode
  def collectDomNodes(buf: scala.collection.mutable.Builder[dom.Node, ?]): Unit =
    rendered.asInstanceOf[Instance].collectDomNodes(buf)
