package io.github.edadma.riposte

import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor
import scala.scalajs.js
import io.github.edadma.vdom.{Host, HostConfig, Scheduler, Transition, Timers}

// riposte's binding of the host-agnostic `vdom` core to the browser DOM. The
// reconciler treats every host node as an opaque `AnyRef`; here each call casts
// that back to the concrete `dom.Node` / `dom.Element` it really is and performs
// the real DOM mutation. This is the one place DOM types meet the core — keeping
// it isolated is what lets the same reconciler also drive a native renderer or a
// headless test host.
object DomHostConfig extends HostConfig:

  private val SvgNs = "http://www.w3.org/2000/svg"

  def createElement(tag: String, namespace: String | Null): AnyRef =
    if namespace == null then dom.document.createElement(tag)
    else dom.document.createElementNS(namespace, tag)

  def createText(text: String): AnyRef    = dom.document.createTextNode(text)
  def createAnchor(label: String): AnyRef = dom.document.createComment(label)

  def parentNode(node: AnyRef): AnyRef | Null  = node.asInstanceOf[dom.Node].parentNode
  def nextSibling(node: AnyRef): AnyRef | Null = node.asInstanceOf[dom.Node].nextSibling

  def insertBefore(parent: AnyRef, node: AnyRef, before: AnyRef | Null): Unit =
    parent.asInstanceOf[dom.Node].insertBefore(node.asInstanceOf[dom.Node], before.asInstanceOf[dom.Node])

  def removeNode(node: AnyRef): Unit =
    val n = node.asInstanceOf[dom.Node]
    val p = n.parentNode
    if p != null then p.removeChild(n)

  // The reconciler only asks for the namespace of an element it is about to mount a
  // child under, and those are always elements — so the unconditional cast matches
  // the original direct `parentDom.asInstanceOf[dom.Element].namespaceURI`.
  def namespaceURI(node: AnyRef): String | Null = node.asInstanceOf[dom.Element].namespaceURI

  def setText(node: AnyRef, text: String): Unit = node.asInstanceOf[dom.Text].data = text

  def setAttribute(node: AnyRef, name: String, value: String): Unit =
    node.asInstanceOf[dom.Element].setAttribute(name, value)

  def removeAttribute(node: AnyRef, name: String): Unit =
    node.asInstanceOf[dom.Element].removeAttribute(name)

  def setProperty(node: AnyRef, name: String, value: Any): Unit =
    node.asInstanceOf[js.Dynamic].updateDynamic(name)(value.asInstanceOf[js.Any])

  def setStyle(node: AnyRef, decls: Map[String, String]): Unit =
    val styleObj = node.asInstanceOf[dom.html.Element].style
    styleObj.cssText = ""
    decls.foreach((k, v) => styleObj.setProperty(k, v))

  def clearStyle(node: AnyRef): Unit =
    node.asInstanceOf[dom.html.Element].style.cssText = ""

  def setInnerHtml(node: AnyRef, html: String): Unit =
    node.asInstanceOf[dom.Element].innerHTML = html

  def addListener(
      node:    AnyRef,
      event:   String,
      capture: Boolean,
      once:    Boolean,
      passive: Boolean,
      fn:      Any => Unit,
  ): AnyRef =
    val wrapped: js.Function1[dom.Event, Unit] = (e: dom.Event) => fn(e)
    // scalajs-dom 2.x doesn't surface AddEventListenerOptions, so pass the plain
    // `{capture, once, passive}` literal the browser API takes via js.Dynamic.
    node.asInstanceOf[js.Dynamic].addEventListener(event, wrapped, js.Dynamic.literal(capture = capture, once = once, passive = passive))
    wrapped

  def removeListener(node: AnyRef, event: String, capture: Boolean, handle: AnyRef): Unit =
    node.asInstanceOf[js.Dynamic].removeEventListener(event, handle.asInstanceOf[js.Function1[dom.Event, Unit]], capture)

// Installs the DOM host and the browser scheduling/timing seams into the vdom core.
// Idempotent: the first call wires everything, later calls are no-ops, so it is safe
// to trigger eagerly (the riposte package object does, on first touch) and again from
// every `createRoot`.
object RiposteDom:
  private var installed = false

  def install(): Unit =
    if !installed then
      installed = true
      Host.config = DomHostConfig
      // Render batching on the microtask queue; passive effects on a macrotask.
      Scheduler.scheduleMicrotask = fn => dom.window.queueMicrotask(() => fn())
      Scheduler.scheduleMacrotask = fn => MacrotaskExecutor.execute(() => fn())
      // Transition timing: the browser's high-resolution clock and frame loop.
      Transition.now          = () => dom.window.performance.now()
      Transition.requestFrame = cb => dom.window.requestAnimationFrame((_: Double) => cb())
      Transition.cancelFrame  = id => dom.window.cancelAnimationFrame(id)
      // Debounce/throttle/presence timers via setTimeout/clearTimeout.
      Timers.schedule = (fn, delayMs) =>
        val id = dom.window.setTimeout(() => fn(), delayMs.toDouble)
        () => dom.window.clearTimeout(id)
