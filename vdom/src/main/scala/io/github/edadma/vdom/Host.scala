package io.github.edadma.vdom

// The platform binding the reconciler mutates through. A host node is an opaque
// `AnyRef` — a DOM node in the browser, a retained widget in a native toolkit, a
// recording stub in tests — and only the HostConfig knows its real type and casts
// back to it. Keeping the core generic over `AnyRef`, rather than a concrete DOM
// type, is what lets one reconciler drive a browser, a native renderer, and a JVM
// test host with no change to the diff engine, the hooks, or the VNode model.
//
// Events are likewise untyped here: a handler is `Any => Unit` and the event it
// receives is whatever the host delivers. The host library re-adds compile-time
// event typing in its own DSL (riposte's `EventKey[E <: dom.Event]`), casting to
// this erased shape at the boundary.
trait HostConfig:

  // --- node creation -------------------------------------------------------

  /** Create an element. `namespace` is the XML namespace URI to create it in (the
    * SVG namespace for an `<svg>` subtree) or null for the host's default; hosts
    * with no notion of namespaces ignore it. */
  def createElement(tag: String, namespace: String | Null): AnyRef

  /** Create a text node holding `text`. */
  def createText(text: String): AnyRef

  /** Create a non-visual marker that holds a position in the sibling order — a DOM
    * comment in the browser. Fragments, portals, and empty renders anchor on one so
    * that every instance always owns at least one real node. `label` is a debugging
    * hint ("fragment", "portal", "empty"). */
  def createAnchor(label: String): AnyRef

  // --- tree shape ----------------------------------------------------------

  def parentNode(node: AnyRef): AnyRef | Null
  def nextSibling(node: AnyRef): AnyRef | Null

  /** Insert `node` under `parent`, immediately before `before`; a null `before`
    * appends at the end. */
  def insertBefore(parent: AnyRef, node: AnyRef, before: AnyRef | Null): Unit

  /** Detach `node` from whatever parent currently holds it. */
  def removeNode(node: AnyRef): Unit

  /** The namespace URI of an element node, or null for text/anchor nodes and for
    * hosts without namespaces. The reconciler consults it only to keep an `<svg>`
    * subtree in the SVG namespace as later children mount. */
  def namespaceURI(node: AnyRef): String | Null

  // --- text ----------------------------------------------------------------

  def setText(node: AnyRef, text: String): Unit

  // --- props ---------------------------------------------------------------

  def setAttribute(node: AnyRef, name: String, value: String): Unit
  def removeAttribute(node: AnyRef, name: String): Unit

  /** Set a live property rather than an attribute. Two things arrive here: the
    * well-known DOM names the reconciler routes as properties — `value` / `checked`
    * and the uncontrolled `defaultValue` / `defaultChecked` seeds, carrying a
    * `String` or `Boolean` — and any prop the application declared as a
    * [[PropValue]], whose payload is passed through untouched and may be of any
    * type the host understands. A removed prop arrives as `null` (a `PropValue`) or
    * `""` (a well-known name), for the host to reset the field with. */
  def setProperty(node: AnyRef, name: String, value: Any): Unit

  /** Replace the element's inline style with exactly `decls`. */
  def setStyle(node: AnyRef, decls: Map[String, String]): Unit

  /** Clear the element's inline style. */
  def clearStyle(node: AnyRef): Unit

  /** Set the element's inner markup verbatim (riposte's `unsafeHtml`). */
  def setInnerHtml(node: AnyRef, html: String): Unit

  // --- events --------------------------------------------------------------

  /** Register `fn` for `event` and return an opaque handle the reconciler stores and
    * hands back to [[removeListener]]. `capture` is part of the listener's identity,
    * so a capture and a bubble listener for the same event coexist. */
  def addListener(
      node:    AnyRef,
      event:   String,
      capture: Boolean,
      once:    Boolean,
      passive: Boolean,
      fn:      Any => Unit,
  ): AnyRef

  def removeListener(node: AnyRef, event: String, capture: Boolean, handle: AnyRef): Unit

// The active host binding. A host installs its config once at startup — riposte's
// DOM host the first time anything in its package is touched, a test host in its
// setup. One process drives one host, mirroring the single-threaded, single-document
// model the reconciler already assumes.
object Host:
  var config: HostConfig = UninstalledHost

// The placeholder config in force before a host installs a real one. Every method
// fails loudly rather than silently doing nothing, so a missing install surfaces at
// the first render instead of as a blank screen.
private object UninstalledHost extends HostConfig:
  private def fail: Nothing = throw new IllegalStateException(
    "No vdom HostConfig installed — the host library must set Host.config before rendering",
  )
  def createElement(tag: String, namespace: String | Null): AnyRef        = fail
  def createText(text: String): AnyRef                                    = fail
  def createAnchor(label: String): AnyRef                                 = fail
  def parentNode(node: AnyRef): AnyRef | Null                             = fail
  def nextSibling(node: AnyRef): AnyRef | Null                            = fail
  def insertBefore(parent: AnyRef, node: AnyRef, before: AnyRef | Null): Unit = fail
  def removeNode(node: AnyRef): Unit                                      = fail
  def namespaceURI(node: AnyRef): String | Null                          = fail
  def setText(node: AnyRef, text: String): Unit                          = fail
  def setAttribute(node: AnyRef, name: String, value: String): Unit      = fail
  def removeAttribute(node: AnyRef, name: String): Unit                  = fail
  def setProperty(node: AnyRef, name: String, value: Any): Unit          = fail
  def setStyle(node: AnyRef, decls: Map[String, String]): Unit           = fail
  def clearStyle(node: AnyRef): Unit                                     = fail
  def setInnerHtml(node: AnyRef, html: String): Unit                     = fail
  def addListener(node: AnyRef, event: String, capture: Boolean, once: Boolean, passive: Boolean, fn: Any => Unit): AnyRef = fail
  def removeListener(node: AnyRef, event: String, capture: Boolean, handle: AnyRef): Unit = fail
