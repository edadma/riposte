package io.github.edadma.vdom

// The entry point. Mount a VNode tree into a host container and get back a `Root`
// handle. Calling `render` again on the same root reconciles the new tree against
// the live one; `unmount` tears it down. The container is an opaque host node; the
// host library exposes a typed wrapper (riposte narrows it to `dom.Element`).
final class Root private[vdom] (container: AnyRef):
  private var instance: Instance | Null = null

  def render(vnode: VNode): Unit =
    instance = instance match
      case null => Reconciler.mount(vnode, container, null, null)
      case inst => Reconciler.patch(inst, vnode)

  def unmount(): Unit =
    instance match
      case null => ()
      case inst =>
        Reconciler.unmount(inst, removeDom = true)
        instance = null

def createRoot(container: AnyRef): Root = new Root(container)

// Convenience: create a root and render in one call.
def render(vnode: VNode, container: AnyRef): Root =
  val root = new Root(container)
  root.render(vnode)
  root
