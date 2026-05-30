package io.github.edadma.riposte

import org.scalajs.dom
import scala.scalajs.js
import scala.collection.mutable
import scala.util.control.NonFatal

// The diff engine. Three primitives:
//
//   mount(vnode, parentDom, before, parent) — create DOM and Instance, insert
//   patch(inst, newVnode)                   — update in place, or replace
//   unmount(inst, removeDom)                — run cleanups and detach DOM
//
// Reconciliation is "same type → update, different type → replace". Two VNodes
// have the same type when they are the same VNode variant and (for elements)
// the same tag and (for components) the same Component identity.
object Reconciler:

  // The component currently being rendered, so hooks know who they belong to.
  // JavaScript is single-threaded, so a plain var is a correct "current fiber".
  private[riposte] var current: ComponentInstance[?] | Null = null

  private val document = dom.document

  private val SvgNs = "http://www.w3.org/2000/svg"

  // --- mount ---------------------------------------------------------------

  def mount(vnode: VNode, parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val inst = vnode match
      case VText(t)      => mountText(t)
      case e: VElement   => mountElement(e, parentDom, parent)
      case f: VFragment  => mountFragment(f, parentDom, before, parent)
      case c: VComponent[?] => mountComponent(c, parentDom, before, parent)
      case pr: VProvider[?] => mountProvider(pr, parentDom, before, parent)
      case p: VPortal    => mountPortal(p, parentDom, before, parent)
      case eb: VErrorBoundary => mountErrorBoundary(eb, parentDom, before, parent)
      case VEmpty        => mountEmpty()
    // Element / Text / Empty create a detached node above and insert here;
    // Fragment / Component insert their own pieces during construction.
    inst match
      case t: TextInstance    => parentDom.insertBefore(t.node, before)
      case e: ElementInstance => parentDom.insertBefore(e.node, before)
      case e: EmptyInstance   => parentDom.insertBefore(e.node, before)
      case _                  => ()
    link(inst, parent)
    inst

  private def link(inst: Instance, parent: Instance | Null): Unit =
    inst.parent = parent
    inst.depth  = if parent == null then 0 else parent.depth + 1

  private def mountText(t: String): Instance =
    new TextInstance(VText(t), document.createTextNode(t))

  private def mountEmpty(): Instance =
    new EmptyInstance(VEmpty, document.createComment("empty"))

  // An element enters the SVG namespace at an `<svg>` tag, or when its DOM parent
  // is already in that namespace — so every descendant of an `<svg>` is created
  // with `createElementNS` and renders. The parent's namespace is the source of
  // truth (not a threaded flag), so children mounted later during a patch get it
  // right too. (HTML re-entry via `<foreignObject>` is not handled in v1: its
  // children would be created in the SVG namespace.)
  private def mountElement(e: VElement, parentDom: dom.Node, parent: Instance | Null): Instance =
    val svg  = e.tag == "svg" || parentDom.asInstanceOf[dom.Element].namespaceURI == SvgNs
    val el   = if svg then document.createElementNS(SvgNs, e.tag) else document.createElement(e.tag)
    val inst = new ElementInstance(e, el, Vector.empty, Map.empty)
    link(inst, parent)
    inst.listeners = applyProps(el, Map.empty, e.props, Map.empty)
    inst.children  = e.children.map(c => mount(c, el, null, inst))
    if e.ref != null then e.ref.attach(el)
    inst

  private def mountFragment(f: VFragment, parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val anchor = document.createComment("fragment")
    parentDom.insertBefore(anchor, before)
    val inst = new FragmentInstance(f, Vector.empty, anchor)
    link(inst, parent)
    inst.children = f.children.map(c => mount(c, parentDom, anchor, inst))
    inst

  private def mountComponent[P](c: VComponent[P], parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val hooks = new Hooks
    val inst  = new ComponentInstance[P](c, c.component, hooks, null, c.props)
    hooks.instance = inst
    link(inst, parent)
    val rendered = renderComponent(inst)
    inst.rendered = mount(rendered, parentDom, before, inst)
    inst

  private def mountProvider(p: VProvider[?], parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val inst = new ProviderInstance(p, p.ctx, p.value, null)
    link(inst, parent)
    inst.child = mount(p.child, parentDom, before, inst)
    inst

  // Insert the anchor at the normal position in the main tree, then mount the
  // child into the portal's foreign target (appended — a portal does not position
  // among the target's existing content). The child's parent is this instance, so
  // it participates in the component tree normally.
  private def mountPortal(p: VPortal, parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val anchor = document.createComment("portal")
    parentDom.insertBefore(anchor, before)
    val inst = new PortalInstance(p, anchor, null)
    link(inst, parent)
    inst.child = mount(p.child, p.target, null, inst)
    inst

  // Mount the real child, catching a render throw and showing the fallback
  // instead. (A child that throws partway through mounting could orphan the DOM
  // it had already inserted; the components this guards against throw from their
  // render before inserting anything, so the common case is clean.)
  private def mountErrorBoundary(eb: VErrorBoundary, parentDom: dom.Node, before: dom.Node | Null, parent: Instance | Null): Instance =
    val inst = new ErrorBoundaryInstance(eb, null, errored = false)
    link(inst, parent)
    try inst.child = mount(eb.child, parentDom, before, inst)
    catch
      case NonFatal(e) =>
        inst.errored = true
        inst.child   = mount(eb.fallback(e), parentDom, before, inst)
    inst

  // Run a component's render function against its hook state.
  private def renderComponent[P](inst: ComponentInstance[P]): VNode =
    val prev = current
    current = inst
    inst.hooks.beginRender()
    val out = inst.component.render(inst.props)(using inst.hooks)
    current = prev
    out

  // --- patch ---------------------------------------------------------------

  def patch(inst: Instance, next: VNode): Instance =
    if sameType(inst, next) then
      inst match
        case t: TextInstance      => patchText(t, next.asInstanceOf[VText]); t
        case e: ElementInstance   => patchElement(e, next.asInstanceOf[VElement]); e
        case f: FragmentInstance  => patchFragment(f, next.asInstanceOf[VFragment]); f
        case c: ComponentInstance[?] => patchComponent(c, next); c
        case pr: ProviderInstance => patchProvider(pr, next.asInstanceOf[VProvider[?]]); pr
        case pt: PortalInstance   => patchPortal(pt, next.asInstanceOf[VPortal]); pt
        case eb: ErrorBoundaryInstance => patchErrorBoundary(eb, next.asInstanceOf[VErrorBoundary]); eb
        case e: EmptyInstance     => e
    else replace(inst, next)

  private def sameType(inst: Instance, next: VNode): Boolean = (inst, next) match
    case (_: TextInstance, _: VText)         => true
    case (e: ElementInstance, v: VElement)   => e.vnode.asInstanceOf[VElement].tag == v.tag
    case (_: FragmentInstance, _: VFragment) => true
    case (c: ComponentInstance[?], v: VComponent[?]) => c.component eq v.component
    case (p: ProviderInstance, v: VProvider[?]) => p.ctx eq v.ctx
    // A portal whose target changed is a different type: replace, so the child is
    // torn out of the old container and remounted under the new one.
    case (p: PortalInstance, v: VPortal)     => p.vnode.asInstanceOf[VPortal].target eq v.target
    case (_: ErrorBoundaryInstance, _: VErrorBoundary) => true
    case (_: EmptyInstance, VEmpty)          => true
    case _                                   => false

  private def replace(inst: Instance, next: VNode): Instance =
    val parentDom = inst.firstDomNode.parentNode
    val before    = inst.firstDomNode
    val fresh     = mount(next, parentDom, before, inst.parent)
    unmount(inst, removeDom = true)
    fresh

  private def patchText(t: TextInstance, next: VText): Unit =
    val old = t.vnode.asInstanceOf[VText]
    if old.text != next.text then t.node.data = next.text
    t.vnode = next

  private def patchElement(e: ElementInstance, next: VElement): Unit =
    val old = e.vnode.asInstanceOf[VElement]
    e.listeners = applyProps(e.node, old.props, next.props, e.listeners)
    e.children  = diffChildren(e, e.children, next.children, e.node, null)
    // The node is reused across a same-tag patch, so a stable ref needs no
    // action. Only a change of ref identity re-points the handle: clear the old,
    // bind the new to this same node. Equality is structural, so the same
    // `useRef` box (or hoisted callback) re-binds nothing.
    if (old.ref: ElementRef | Null) != (next.ref: ElementRef | Null) then
      if old.ref != null then old.ref.detach()
      if next.ref != null then next.ref.attach(e.node)
    e.vnode = next

  private def patchFragment(f: FragmentInstance, next: VFragment): Unit =
    val parentDom = f.anchor.parentNode
    f.children = diffChildren(f, f.children, next.children, parentDom, f.anchor)
    f.vnode    = next

  // Update the provided value and reconcile the child. Patching the child
  // re-renders the subtree top-down, which covers ordinary consumers — but a
  // memoized ancestor may bail and skip consumers below it, so when the value
  // actually changes we also walk the subtree and wake every component that
  // reads this context.
  private def patchProvider(pr: ProviderInstance, next: VProvider[?]): Unit =
    val changed = pr.value != next.value
    pr.value = next.value
    pr.vnode = next
    pr.child = patch(pr.child.asInstanceOf[Instance], next.child)
    if changed then invalidateContextConsumers(pr.child.asInstanceOf[Instance], pr.ctx)

  // Same target (sameType already checked): reconcile the child in place. The
  // child's own DOM node lives in `target`, and the child diff resolves its
  // container from that node, so no target needs to be threaded here.
  private def patchPortal(pt: PortalInstance, next: VPortal): Unit =
    pt.child = patch(pt.child.asInstanceOf[Instance], next.child)
    pt.vnode = next

  // Reconcile a boundary. While healthy, patch the real child and catch a throw,
  // swapping in the fallback. While showing the fallback, a fresh patch retries
  // the real child — so fixing the cause recovers — and falls back again if it
  // still throws. `eb.vnode` is set first so the fallback/swap helpers read the
  // new fallback function.
  private def patchErrorBoundary(eb: ErrorBoundaryInstance, next: VErrorBoundary): Unit =
    eb.vnode = next
    if eb.errored then
      try
        swapChild(eb, mountNextTo(eb.child.asInstanceOf[Instance], next.child, eb))
        eb.errored = false
      catch case NonFatal(e) => showFallback(eb, e)
    else
      try eb.child = patch(eb.child.asInstanceOf[Instance], next.child)
      catch case NonFatal(e) => showFallback(eb, e)

  // Mount `vnode` immediately before `sibling`'s position, returning the fresh
  // instance without removing the old one (the caller decides when to swap).
  private def mountNextTo(sibling: Instance, vnode: VNode, parent: Instance): Instance =
    mount(vnode, sibling.firstDomNode.parentNode, sibling.firstDomNode, parent)

  // Replace the boundary's current child with an already-mounted fresh one,
  // unmounting (and removing the DOM of) the old.
  private def swapChild(eb: ErrorBoundaryInstance, fresh: Instance): Unit =
    val old = eb.child.asInstanceOf[Instance]
    eb.child = fresh
    unmount(old, removeDom = true)

  // Tear down whatever the boundary currently shows and mount the fallback for
  // `e`. Used by both the patch path and the scheduler path (a state update deep
  // in the subtree whose re-render throws).
  private[riposte] def showFallback(eb: ErrorBoundaryInstance, e: Throwable): Unit =
    val fallback = eb.vnode.asInstanceOf[VErrorBoundary].fallback(e)
    swapChild(eb, mountNextTo(eb.child.asInstanceOf[Instance], fallback, eb))
    eb.errored = true

  // A render threw during a scheduler-driven re-render of `start` (a state update
  // in the subtree, not a parent-driven patch). Walk up to the nearest enclosing
  // boundary and show its fallback; with no boundary the error propagates, since
  // there is nothing to contain it.
  private[riposte] def handleRenderError(start: Instance, e: Throwable): Unit =
    var cur: Instance | Null = start
    while cur != null do
      cur match
        case eb: ErrorBoundaryInstance => showFallback(eb, e); return
        case _                         => cur = cur.asInstanceOf[Instance].parent
    throw e

  // Mark every still-mounted component in this subtree that reads `ctx` for
  // re-render. Recursion stops at a nested provider for the same context: that
  // sub-subtree resolves to the inner provider's (unchanged) value, so it is
  // shielded and must not be woken — which is why a global subscriber set would
  // over-invalidate here.
  private def invalidateContextConsumers(inst: Instance, ctx: Context[?]): Unit =
    inst match
      case c: ComponentInstance[?] =>
        if c.mounted && c.hooks.subscribedContexts.contains(ctx) then Scheduler.enqueueUpdate(c)
        invalidateContextConsumers(c.rendered.asInstanceOf[Instance], ctx)
      case e: ElementInstance  => e.children.foreach(invalidateContextConsumers(_, ctx))
      case f: FragmentInstance => f.children.foreach(invalidateContextConsumers(_, ctx))
      case p: ProviderInstance =>
        if !(p.ctx eq ctx) then invalidateContextConsumers(p.child.asInstanceOf[Instance], ctx)
      case _ => () // text / empty — no children, never a consumer

  private def patchComponent(c: ComponentInstance[?], next: VNode): Unit =
    val v  = next.asInstanceOf[VComponent[Any]]
    val ci = c.asInstanceOf[ComponentInstance[Any]]
    val prevProps = ci.props
    ci.props = v.props
    ci.vnode = next
    // memo bailout: a memoized component skips this parent-driven re-render when
    // its props are unchanged and it has no pending state update of its own.
    // (Context changes reach it through the subscriber path, not this cascade.)
    if c.component.memoized && !c.dirty && prevProps == v.props then ()
    else rerender(c)

  // Re-render a single component and reconcile its output. Used both by the
  // parent-driven patch above and by the scheduler for local state updates.
  private[riposte] def rerender(c: ComponentInstance[?]): Unit =
    c.dirty = false
    val rendered = renderComponent(c.asInstanceOf[ComponentInstance[Any]])
    c.rendered = patch(c.rendered.asInstanceOf[Instance], rendered)

  // --- children diff -------------------------------------------------------

  // Reconcile a list of child VNodes against existing child Instances within
  // `parentDom`. `tailBefore` is the node the children sit before (null for an
  // element, since its children are its only content; the anchor comment for a
  // fragment). If any new child carries a key, the keyed algorithm runs;
  // otherwise children are matched positionally.
  private def diffChildren(
      parent:     Instance,
      oldKids:    Vector[Instance],
      newKids:    Vector[VNode],
      parentDom:  dom.Node,
      tailBefore: dom.Node | Null,
  ): Vector[Instance] =
    if newKids.exists(keyOf(_).isDefined) then
      diffKeyed(parent, oldKids, newKids, parentDom, tailBefore)
    else
      diffByIndex(parent, oldKids, newKids, parentDom, tailBefore)

  private def keyOf(v: VNode): Option[String] = v match
    case e: VElement      => e.key
    case f: VFragment     => f.key
    case c: VComponent[?] => c.key
    case _                => None

  // Positional matching: patch the overlap, mount the surplus at the tail,
  // unmount the deficit. No DOM moves happen, so focus and cursor survive.
  private def diffByIndex(
      parent:     Instance,
      oldKids:    Vector[Instance],
      newKids:    Vector[VNode],
      parentDom:  dom.Node,
      tailBefore: dom.Node | Null,
  ): Vector[Instance] =
    val common = math.min(oldKids.length, newKids.length)
    val result = Vector.newBuilder[Instance]
    var i = 0
    while i < common do
      result += patch(oldKids(i), newKids(i))
      i += 1
    while i < newKids.length do
      result += mount(newKids(i), parentDom, tailBefore, parent)
      i += 1
    var j = newKids.length
    while j < oldKids.length do
      unmount(oldKids(j), removeDom = true)
      j += 1
    result.result()

  // Keyed matching: reuse instances whose key persists, mount new keys, unmount
  // dropped keys, then position the result list right-to-left, only moving DOM
  // blocks that are actually out of place.
  private def diffKeyed(
      parent:     Instance,
      oldKids:    Vector[Instance],
      newKids:    Vector[VNode],
      parentDom:  dom.Node,
      tailBefore: dom.Node | Null,
  ): Vector[Instance] =
    val oldByKey = mutable.LinkedHashMap.empty[String, Instance]
    val oldUnkeyed = mutable.Queue.empty[Instance]
    oldKids.foreach { inst =>
      keyOf(inst.vnode) match
        case Some(k) => oldByKey(k) = inst
        case None    => oldUnkeyed += inst
    }

    val reused = mutable.HashSet.empty[Instance]
    val result = newKids.map { v =>
      val matched = keyOf(v) match
        case Some(k) => oldByKey.get(k)
        case None    => if oldUnkeyed.nonEmpty then Some(oldUnkeyed.dequeue()) else None
      matched match
        case Some(inst) if sameType(inst, v) =>
          reused += inst
          patch(inst, v)
        case _ =>
          mount(v, parentDom, tailBefore, parent)
    }

    oldKids.foreach { inst =>
      if !reused.contains(inst) then unmount(inst, removeDom = true)
    }

    // Position pass: walk right-to-left, ensuring each block ends immediately
    // before the running anchor. Skip blocks already in place.
    var anchor: dom.Node | Null = tailBefore
    var i = result.length - 1
    while i >= 0 do
      val inst = result(i)
      if inst.lastDomNode.nextSibling != anchor then
        inst.domNodes.foreach(parentDom.insertBefore(_, anchor))
      anchor = inst.firstDomNode
      i -= 1
    result

  // --- unmount -------------------------------------------------------------

  // Detach an instance, running any cleanups bottom-up. `removeDom` is true at
  // the top of a removed subtree; nested element children pass false because
  // removing the ancestor element takes their DOM with it. Fragment children
  // are real siblings, so they inherit the caller's `removeDom`.
  def unmount(inst: Instance, removeDom: Boolean): Unit =
    inst.mounted = false
    inst match
      case t: TextInstance =>
        if removeDom then removeNode(t.node)
      case e: EmptyInstance =>
        if removeDom then removeNode(e.node)
      case e: ElementInstance =>
        e.children.foreach(unmount(_, removeDom = false))
        val r = e.vnode.asInstanceOf[VElement].ref
        if r != null then r.detach()
        if removeDom then removeNode(e.node)
      case f: FragmentInstance =>
        f.children.foreach(unmount(_, removeDom))
        if removeDom then removeNode(f.anchor)
      case c: ComponentInstance[?] =>
        // Children first, then this component's own effect cleanups (bottom-up).
        unmount(c.rendered.asInstanceOf[Instance], removeDom)
        c.hooks.runUnmountCleanups()
      case pr: ProviderInstance =>
        unmount(pr.child.asInstanceOf[Instance], removeDom)
      case pt: PortalInstance =>
        // The child lives in a foreign target, so removing an ancestor element in
        // the main tree does not take it with it — force its DOM removal. The
        // anchor sits in the main tree and follows the caller's removeDom.
        unmount(pt.child.asInstanceOf[Instance], removeDom = true)
        if removeDom then removeNode(pt.anchor)
      case eb: ErrorBoundaryInstance =>
        unmount(eb.child.asInstanceOf[Instance], removeDom)

  private def removeNode(n: dom.Node): Unit =
    val p = n.parentNode
    if p != null then p.removeChild(n)

  // --- props ---------------------------------------------------------------

  // Apply the difference between two prop maps to a live element and return the
  // element's current event-listener set. Removes props gone from `next`, sets
  // changed/added props, and swaps event listeners as handlers change.
  private def applyProps(
      el:           dom.Element,
      oldProps:     Map[String, Prop],
      newProps:     Map[String, Prop],
      oldListeners: Map[String, js.Function1[dom.Event, Unit]],
  ): Map[String, js.Function1[dom.Event, Unit]] =
    var listeners = oldListeners

    oldProps.foreach { (k, _) =>
      if !newProps.contains(k) then
        if k.startsWith("on:") then
          val evt = k.drop(3)
          listeners.get(k).foreach(el.removeEventListener(evt, _))
          listeners = listeners.removed(k)
        else removeStatic(el, k)
    }

    newProps.foreach { (k, prop) =>
      if k.startsWith("on:") then
        prop match
          case Handler(fn) =>
            val evt = k.drop(3)
            listeners.get(k).foreach(el.removeEventListener(evt, _))
            val wrapped: js.Function1[dom.Event, Unit] = (e: dom.Event) => fn(e)
            el.addEventListener(evt, wrapped)
            listeners = listeners.updated(k, wrapped)
          case _ => ()
      else if !oldProps.get(k).contains(prop) then setStatic(el, k, prop)
    }

    listeners

  // A few attributes must be set as live DOM properties for the element to
  // behave (a re-rendered controlled input only reflects `value` as a property,
  // not an attribute).
  private def isProperty(name: String): Boolean = name == "value" || name == "checked"

  private def setStatic(el: dom.Element, name: String, prop: Prop): Unit = prop match
    case Attr(v) =>
      if isProperty(name) then el.asInstanceOf[js.Dynamic].updateDynamic(name)(v)
      else el.setAttribute(name, v)
    case BoolAttr(v) =>
      if isProperty(name) then el.asInstanceOf[js.Dynamic].updateDynamic(name)(v)
      else if v then el.setAttribute(name, "")
      else el.removeAttribute(name)
    case StyleProp(decls) =>
      val styleObj = el.asInstanceOf[dom.html.Element].style
      styleObj.cssText = ""
      decls.foreach((k, v) => styleObj.setProperty(k, v))
    case Handler(_) => ()

  private def removeStatic(el: dom.Element, name: String): Unit =
    if isProperty(name) then el.asInstanceOf[js.Dynamic].updateDynamic(name)("")
    else if name == "style" then el.asInstanceOf[dom.html.Element].style.cssText = ""
    else el.removeAttribute(name)
