package io.github.edadma.vdom

// The live tree that mirrors the real host tree. Every VNode that gets mounted
// produces an Instance; the reconciler keeps Instances across renders and
// mutates them (and their backing host nodes) in place.
//
// A key invariant makes positioning simple: EVERY instance always owns at
// least one real host node. Empty renders an anchor, and a fragment carries a
// trailing anchor, so `firstDomNode` / `lastDomNode` are never null and a
// fragment's children always have a stable node to be inserted before.
//
// Host nodes are opaque `AnyRef`s; only the installed `HostConfig` interprets
// them. The field/method names keep the "DOM" wording because that is the
// canonical host, but nothing here depends on a DOM.
sealed abstract class Instance:
  var vnode:   VNode
  var parent:  Instance | Null = null
  var depth:   Int             = 0
  var mounted: Boolean         = true

  // The first and last top-level host nodes this instance contributes, in
  // document order. For multi-node instances (fragments, and components that
  // render fragments) these bracket the whole block.
  def firstDomNode: AnyRef
  def lastDomNode:  AnyRef

  // Append, in order, every top-level host node this instance contributes.
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit

  final def domNodes: List[AnyRef] =
    val b = List.newBuilder[AnyRef]
    collectDomNodes(b)
    b.result()

final class TextInstance(var vnode: VNode, val node: AnyRef) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit = buf += node

final class EmptyInstance(var vnode: VNode, val node: AnyRef) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit = buf += node

final class ElementInstance(
    var vnode:     VNode,
    val node:      AnyRef,
    var children:  Vector[Instance],
    var listeners: Map[String, AnyRef],
) extends Instance:
  def firstDomNode = node
  def lastDomNode  = node
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit = buf += node

final class FragmentInstance(
    var vnode:    VNode,
    var children: Vector[Instance],
    val anchor:   AnyRef,
) extends Instance:
  def firstDomNode = if children.nonEmpty then children.head.firstDomNode else anchor
  def lastDomNode  = anchor
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit =
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
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit =
    child.asInstanceOf[Instance].collectDomNodes(buf)

// A portal: its `child` is mounted under a foreign `target` container, but an
// `anchor` holds this instance's slot in the main tree. Crucially,
// `collectDomNodes` reports ONLY the anchor — the child's nodes are not here, so
// a parent diff positioning this slot must never try to move them.
final class PortalInstance(
    var vnode:  VNode,
    val anchor: AnyRef,
    var child:  Instance | Null,
) extends Instance:
  def firstDomNode = anchor
  def lastDomNode  = anchor
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit = buf += anchor

// An error boundary: its single child is either the real subtree or, once a
// render in that subtree has thrown, the fallback. `errored` records which is
// currently shown, so a re-render knows whether to retry the real child.
final class ErrorBoundaryInstance(
    var vnode:   VNode,
    var child:   Instance | Null,
    var errored: Boolean,
) extends Instance:
  def firstDomNode = child.asInstanceOf[Instance].firstDomNode
  def lastDomNode  = child.asInstanceOf[Instance].lastDomNode
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit =
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
  def collectDomNodes(buf: scala.collection.mutable.Builder[AnyRef, ?]): Unit =
    rendered.asInstanceOf[Instance].collectDomNodes(buf)
