package io.github.edadma.riposte

import org.scalajs.dom

// The builder DSL. Elements are assembled from a varargs list of `Mod`s, each
// of which is either a property setter, a child, a list of children, or a key.
// String and VNode values become children automatically via the conversions
// at the bottom, so `div(cls := "row", "hello", child)` reads naturally.

sealed trait Mod

// The conversions live in Mod's companion so that, wherever a `Mod` is
// expected (the tag helpers' varargs), implicit search finds them via the
// target type's companion scope — no `import ...given` needed at call sites.
object Mod:
  given Conversion[String, Mod]     = s  => ChildMod(VText(s))
  given Conversion[VNode, Mod]      = n  => ChildMod(n)
  given Conversion[Int, Mod]        = i  => ChildMod(VText(i.toString))
  given Conversion[Seq[VNode], Mod] = ns => ChildrenMod(ns)

  // An optional child: `Some(node)` shows the node, `None` renders an empty
  // placeholder rather than no child at all — so that toggling between the two
  // keeps a stable slot and leaves the surrounding siblings (and their state)
  // untouched, exactly like `if cond then node else empty`.
  given Conversion[Option[VNode], Mod] =
    case Some(n) => ChildMod(n)
    case None    => ChildMod(VEmpty)

final case class PropMod(name: String, value: Prop)    extends Mod
final case class ChildMod(node: VNode)                 extends Mod
final case class ChildrenMod(nodes: Seq[VNode])        extends Mod
final case class KeyMod(key: String)                   extends Mod
final case class RefMod(ref: ElementRef)               extends Mod
case object NoMod                                       extends Mod

// Folds a list of mods into a VElement. Later props with the same name win;
// `class` is the one exception — repeated `cls :=` values are space-joined so
// `cls := "btn"` and a conditional `cls := "active"` compose.
def h(tag: String)(mods: Mod*): VElement =
  var props                    = Map.empty[String, Prop]
  val children                 = Vector.newBuilder[VNode]
  var key: Option[String]      = None
  var ref: ElementRef | Null   = null
  mods.foreach {
    case PropMod("class", Attr(v)) =>
      props = props.updated("class", Attr(props.get("class") match
        case Some(Attr(existing)) if existing.nonEmpty => existing + " " + v
        case _                                         => v))
    case PropMod(n, v)   => props = props.updated(n, v)
    case ChildMod(n)     => children += n
    case ChildrenMod(ns) => children ++= ns
    case KeyMod(k)       => key = Some(k)
    case RefMod(r)       => ref = r
    case NoMod           => ()
  }
  VElement(tag, props, children.result(), key, ref)

// A transparent group of siblings — splices its children into the parent's
// child list without introducing a wrapper element.
def fragment(children: VNode*): VFragment = VFragment(children.toVector)

// Renders nothing while still occupying a stable slot, so toggling between
// `if cond then something else empty` keeps surrounding siblings put.
val empty: VNode = VEmpty

// Conditional children. `when(cond)(node)` is the node when `cond` holds and an
// empty placeholder otherwise; `unless` is its negation. The node is by-name, so
// it is built only when actually shown. Both yield a VNode (`empty` when hidden)
// — never a dropped child — so the slot is stable and surrounding siblings keep
// their DOM and state as the condition flips. The Scala stand-in for React's
// `{cond && <X/>}`.
def when(cond: Boolean)(node: => VNode): VNode   = if cond then node else VEmpty
def unless(cond: Boolean)(node: => VNode): VNode = if cond then VEmpty else node

// --- attribute & event keys ------------------------------------------------

// An attribute name that becomes a `PropMod` via `:=`. Overloads cover the
// common value shapes so the call site doesn't sprinkle `.toString`.
final class AttrKey(val name: String):
  def :=(v: String):  Mod = PropMod(name, Attr(v))
  def :=(v: Int):     Mod = PropMod(name, Attr(v.toString))
  def :=(v: Boolean): Mod = PropMod(name, BoolAttr(v))

// An event name plus the DOM event type it delivers, so a handler is typed at
// the call site with no cast: `onClick := (e => …)` gets a `dom.MouseEvent`,
// `onKeyDown` a `dom.KeyboardEvent`. The handler is stored untyped — the DOM
// hands the listener the matching event subtype, so the cast to `dom.Event =>
// Unit` is sound (and erases to a no-op, since functions erase to Function1).
final class EventKey[E <: dom.Event](val name: String):
  def :=(fn: E => Unit): Mod = PropMod("on:" + name, Handler(fn.asInstanceOf[dom.Event => Unit]))

// Inline styles. `style := Map("color" -> "red")` or the variadic `css(...)`.
object style:
  def :=(decls: Map[String, String]): Mod = PropMod("style", StyleProp(decls))

def css(decls: (String, String)*): Mod = PropMod("style", StyleProp(decls.toMap))

// Tags a sibling with a stable identity so the reconciler preserves its state
// and DOM across reorders, inserts, and removes.
object key:
  def :=(k: String): Mod = KeyMod(k)
  def :=(k: Int):    Mod = KeyMod(k.toString)

// Binds an element to a handle on its live DOM node. Either a `useRef` box —
// `val r = useRef[dom.html.Input | Null](null); input(ref := r)`, read as
// `r.current` from an effect — or a callback `node => …` run on mount (node)
// and unmount (null). `ref` is a top-level val (not an object) so its compiled
// name doesn't case-clash with the `Ref` class on case-insensitive filesystems.
final class RefKey:
  def :=[T](box: Ref[T]): Mod                   = RefMod(BoxRef(box))
  def :=(fn: (dom.Element | Null) => Unit): Mod = RefMod(FnRef(fn))

val ref = new RefKey

// --- common attributes -----------------------------------------------------

val cls         = AttrKey("class")
val id          = AttrKey("id")
val href        = AttrKey("href")
val src         = AttrKey("src")
val alt         = AttrKey("alt")
val title       = AttrKey("title")
val typ         = AttrKey("type")
val value       = AttrKey("value")
val placeholder = AttrKey("placeholder")
val name        = AttrKey("name")
val checked     = AttrKey("checked")
val disabled    = AttrKey("disabled")
val readOnly    = AttrKey("readonly")
val role        = AttrKey("role")
val tabIndex    = AttrKey("tabindex")
val forId       = AttrKey("for")

def attr(n: String): AttrKey = new AttrKey(n)

// --- common events ---------------------------------------------------------

val onClick     = EventKey[dom.MouseEvent]("click")
val onInput     = EventKey[dom.Event]("input")
val onChange    = EventKey[dom.Event]("change")
val onSubmit    = EventKey[dom.Event]("submit")
val onKeyDown   = EventKey[dom.KeyboardEvent]("keydown")
val onKeyUp     = EventKey[dom.KeyboardEvent]("keyup")
val onFocus     = EventKey[dom.FocusEvent]("focus")
val onBlur      = EventKey[dom.FocusEvent]("blur")
val onMouseDown = EventKey[dom.MouseEvent]("mousedown")
val onMouseUp   = EventKey[dom.MouseEvent]("mouseup")

// A handler for any other event, typed as a plain `dom.Event`. For a typed
// custom event, name the type on the key directly: `EventKey[dom.WheelEvent](
// "wheel") := (e => …)`.
def on(n: String): EventKey[dom.Event] = new EventKey(n)

// Read the current value of the input/textarea/select that fired an event —
// the common need inside an `onInput` handler.
def targetValue(e: dom.Event): String =
  e.target.asInstanceOf[dom.html.Input].value

// --- common element tags ---------------------------------------------------

def div(mods: Mod*):    VElement = h("div")(mods*)
def span(mods: Mod*):   VElement = h("span")(mods*)
def p(mods: Mod*):      VElement = h("p")(mods*)
def button(mods: Mod*): VElement = h("button")(mods*)
def input(mods: Mod*):  VElement = h("input")(mods*)
def label(mods: Mod*):  VElement = h("label")(mods*)
def ul(mods: Mod*):     VElement = h("ul")(mods*)
def ol(mods: Mod*):     VElement = h("ol")(mods*)
def li(mods: Mod*):     VElement = h("li")(mods*)
def a(mods: Mod*):      VElement = h("a")(mods*)
def img(mods: Mod*):    VElement = h("img")(mods*)
def h1(mods: Mod*):     VElement = h("h1")(mods*)
def h2(mods: Mod*):     VElement = h("h2")(mods*)
def h3(mods: Mod*):     VElement = h("h3")(mods*)
def section(mods: Mod*): VElement = h("section")(mods*)
def header(mods: Mod*): VElement = h("header")(mods*)
def footer(mods: Mod*): VElement = h("footer")(mods*)
def pre(mods: Mod*):    VElement = h("pre")(mods*)
def code(mods: Mod*):   VElement = h("code")(mods*)
def strong(mods: Mod*): VElement = h("strong")(mods*)
def em(mods: Mod*):     VElement = h("em")(mods*)
def small(mods: Mod*):  VElement = h("small")(mods*)
def hr(mods: Mod*):     VElement = h("hr")(mods*)
def br(mods: Mod*):     VElement = h("br")(mods*)

// A bare text node, for the rare case the String conversion isn't triggered.
def text(s: String): VNode = VText(s)
