package io.github.edadma.riposte

import org.scalajs.dom

// The public entry point. Mount a VNode tree into a container element and get
// back a `Root` handle. Calling `render` again on the same root reconciles the
// new tree against the live one; `unmount` tears it down.
//
//   val root = createRoot(dom.document.getElementById("app"))
//   root.render(App(()))
final class Root private[riposte] (container: dom.Element):
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

def createRoot(container: dom.Element): Root = new Root(container)

// Convenience: create a root and render in one call.
def render(vnode: VNode, container: dom.Element): Root =
  val root = new Root(container)
  root.render(vnode)
  root
