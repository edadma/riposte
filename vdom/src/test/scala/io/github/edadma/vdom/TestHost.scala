package io.github.edadma.vdom

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Outcome
import scala.collection.mutable

// An in-memory host node, the test analogue of a DOM node. The reconciler treats
// these as opaque `AnyRef`s through [[TestHost]]; the tests reach in to assert
// structure and to fire events.
sealed abstract class TestNode:
  var parent: TestElement | Null = null

final class TestText(var text: String)   extends TestNode
final class TestAnchor(val label: String) extends TestNode

final class TestElement(val tag: String, val namespace: String | Null) extends TestNode:
  val children   = mutable.ArrayBuffer.empty[TestNode]
  val attributes = mutable.LinkedHashMap.empty[String, String]
  val properties = mutable.LinkedHashMap.empty[String, Any]
  var style: Map[String, String] = Map.empty
  var innerHtml: String | Null   = null
  val listeners  = mutable.ArrayBuffer.empty[TestListener]

final class TestListener(
    val event:   String,
    val capture: Boolean,
    val once:    Boolean,
    val passive: Boolean,
    val fn:      Any => Unit,
)

// A `HostConfig` backed by [[TestNode]]s. Builds and mutates a real ordered tree —
// so the reconciler's positioning, keyed moves, and removals are exercised exactly
// as on the DOM — without needing a browser. The `fire` helper dispatches an event
// to a node's listeners, so an `onClick` round-trips through the scheduler.
final class TestHost extends HostConfig:

  def createElement(tag: String, namespace: String | Null): AnyRef = new TestElement(tag, namespace)
  def createText(text: String): AnyRef                             = new TestText(text)
  def createAnchor(label: String): AnyRef                          = new TestAnchor(label)

  def parentNode(node: AnyRef): AnyRef | Null = node.asInstanceOf[TestNode].parent

  def nextSibling(node: AnyRef): AnyRef | Null =
    val n = node.asInstanceOf[TestNode]
    val p = n.parent
    if p == null then null
    else
      val i = p.children.indexOf(n)
      if i >= 0 && i + 1 < p.children.length then p.children(i + 1) else null

  def insertBefore(parent: AnyRef, node: AnyRef, before: AnyRef | Null): Unit =
    val par = parent.asInstanceOf[TestElement]
    val n   = node.asInstanceOf[TestNode]
    val old = n.parent
    if old != null then old.children -= n // a move detaches first
    n.parent = par
    val idx = if before == null then -1 else par.children.indexOf(before.asInstanceOf[TestNode])
    if idx < 0 then par.children += n else par.children.insert(idx, n)

  def removeNode(node: AnyRef): Unit =
    val n = node.asInstanceOf[TestNode]
    val p = n.parent
    if p != null then
      p.children -= n
      n.parent = null

  def namespaceURI(node: AnyRef): String | Null = node match
    case e: TestElement => e.namespace
    case _              => null

  def setText(node: AnyRef, text: String): Unit = node.asInstanceOf[TestText].text = text

  def setAttribute(node: AnyRef, name: String, value: String): Unit =
    node.asInstanceOf[TestElement].attributes(name) = value

  def removeAttribute(node: AnyRef, name: String): Unit =
    node.asInstanceOf[TestElement].attributes -= name

  def setProperty(node: AnyRef, name: String, value: Any): Unit =
    node.asInstanceOf[TestElement].properties(name) = value

  def setStyle(node: AnyRef, decls: Map[String, String]): Unit =
    node.asInstanceOf[TestElement].style = decls

  def clearStyle(node: AnyRef): Unit = node.asInstanceOf[TestElement].style = Map.empty

  def setInnerHtml(node: AnyRef, html: String): Unit = node.asInstanceOf[TestElement].innerHtml = html

  def addListener(node: AnyRef, event: String, capture: Boolean, once: Boolean, passive: Boolean, fn: Any => Unit): AnyRef =
    val l = new TestListener(event, capture, once, passive, fn)
    node.asInstanceOf[TestElement].listeners += l
    l

  def removeListener(node: AnyRef, event: String, capture: Boolean, handle: AnyRef): Unit =
    node.asInstanceOf[TestElement].listeners -= handle.asInstanceOf[TestListener]

  // --- test helpers --------------------------------------------------------

  /** Invoke the bubble-phase listeners registered for `event` on `node`. A `once`
    * listener is removed as it fires, as the DOM does, so a test can tell the
    * difference between a registration that survived a patch and one that was torn
    * down and freshly re-added. */
  def fire(node: AnyRef, event: String, payload: Any = null): Unit =
    val el  = node.asInstanceOf[TestElement]
    val due = el.listeners.filter(l => l.event == event && !l.capture).toList
    due.foreach { l =>
      if l.once then el.listeners -= l
      l.fn(payload)
    }

  /** Invoke the capture-phase listeners registered for `event` on `node`. */
  def fireCapture(node: AnyRef, event: String, payload: Any = null): Unit =
    node.asInstanceOf[TestElement].listeners
      .filter(l => l.event == event && l.capture)
      .toList
      .foreach(_.fn(payload))

  /** Serialize a node subtree to an HTML-ish string for structural assertions.
    * Attributes are emitted in sorted order so the output is deterministic. */
  def serialize(node: TestNode): String = node match
    case t: TestText   => t.text
    case a: TestAnchor => s"<!--${a.label}-->"
    case e: TestElement =>
      val attrs = e.attributes.toSeq.sortBy(_._1).map((k, v) => s""" $k="$v"""").mkString
      val kids  = e.children.map(serialize).mkString
      s"<${e.tag}$attrs>$kids</${e.tag}>"

  /** The concatenated text content of a subtree (text nodes only). */
  def textOf(node: TestNode): String = node match
    case t: TestText    => t.text
    case _: TestAnchor  => ""
    case e: TestElement => e.children.map(textOf).mkString

// A manual stand-in for the `Timers.schedule` seam: nothing fires until the test says
// so, and the cancel function the hooks hold really does cancel. Install it with
// `Timers.schedule = timers.schedule` in a test that exercises a delay — debounce,
// throttle, or a presence exit — then drive it with `fireAll()`.
final class ManualTimers:
  private final class Timer(val fn: () => Unit, val delay: Int)
  private val pending = mutable.ArrayBuffer.empty[Timer]

  val schedule: (() => Unit, Int) => (() => Unit) = (fn, delay) =>
    val t = new Timer(fn, delay)
    pending += t
    () => pending -= t // reference equality: cancels this timer, not an equal-looking one

  /** How many timers are outstanding — 0 proves a cancel actually cancelled. */
  def count: Int = pending.length

  /** The delays the pending timers were scheduled with, in scheduling order. */
  def delays: List[Int] = pending.map(_.delay).toList

  /** Fire every pending timer, clearing the queue first so a timer that schedules
    * another (the throttle's next window) leaves it pending rather than looping. */
  def fireAll(): Unit =
    val due = pending.toList
    pending.clear()
    due.foreach(_.fn())

// A manual stand-in for the `Transition.requestFrame` / `cancelFrame` seams, driven the
// same way. `now` is a plain var a test advances to move the transition clock.
final class ManualFrames:
  private val pending = mutable.LinkedHashMap.empty[Int, () => Unit]
  private var seq     = 0
  var now: Double     = 0.0

  val request: (() => Unit) => Int = fn =>
    seq += 1
    pending(seq) = fn
    seq

  val cancel: Int => Unit = id => pending -= id

  /** How many frames are outstanding — 0 proves a settled transition stopped asking. */
  def count: Int = pending.size

  def fireAll(): Unit =
    val due = pending.toList
    pending.clear()
    due.foreach(_._2())

// Base suite for headless reconciler/hooks tests. Installs a fresh `TestHost` and
// inert scheduling/timing seams before each test, so renders only commit when a test
// calls `Scheduler.flushSync()` — fully deterministic, no real microtasks or timers.
trait VdomSuite extends AnyFunSuite:

  protected var host: TestHost = new TestHost

  override def withFixture(test: NoArgTest): Outcome =
    host = new TestHost
    Host.config = host
    Scheduler.scheduleMicrotask = _ => ()
    Scheduler.scheduleMacrotask = _ => ()
    Transition.now          = () => 0.0
    Transition.requestFrame = _ => 0
    Transition.cancelFrame  = _ => ()
    Timers.schedule         = (_, _) => () => ()
    super.withFixture(test)

  /** A detached container element to mount into. */
  protected def container(): TestElement = new TestElement("root", null)

  // --- tiny VNode builders (vdom has no DSL — that lives in the host) -------

  protected def el(tag: String, props: (String, Prop)*)(children: VNode*): VElement =
    VElement(tag, props.toMap, children.toVector, None)

  protected def keyed(tag: String, key: String, props: (String, Prop)*)(children: VNode*): VElement =
    VElement(tag, props.toMap, children.toVector, Some(key))

  protected def onClick(fn: Any => Unit): (String, Prop) = "on:click" -> Handler(fn)
  protected def attrib(name: String, value: String): (String, Prop) = name -> Attr(value)

  /** Install a manual timer queue over the `Timers.schedule` seam and return it. */
  protected def manualTimers(): ManualTimers =
    val t = new ManualTimers
    Timers.schedule = t.schedule
    t

  /** Install a manual frame pump and clock over the `Transition` seams and return it. */
  protected def manualFrames(): ManualFrames =
    val f = new ManualFrames
    Transition.now          = () => f.now
    Transition.requestFrame = f.request
    Transition.cancelFrame  = f.cancel
    f
