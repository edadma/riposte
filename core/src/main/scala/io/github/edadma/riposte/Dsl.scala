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

// Render `child` into a different DOM container while keeping its place in the
// component tree here. The child's DOM lands under `target` (events, context, and
// re-renders still flow as if it were in place) — for modals, overlays, tooltips,
// and anything that must escape an ancestor's overflow or stacking context.
def portal(target: dom.Element, child: VNode): VNode = VPortal(target, child)

// Contain render failures in a subtree: if mounting or re-rendering `child`
// throws, `fallback(error)` is shown instead of the exception propagating. A
// later re-render retries the real child, so fixing the cause recovers.
//
//   errorBoundary(e => p(s"crashed: ${e.getMessage}")) { RiskyWidget() }
def errorBoundary(fallback: Throwable => VNode)(child: VNode): VNode =
  VErrorBoundary(fallback, child)

// --- attribute & event keys ------------------------------------------------

// An attribute name that becomes a `PropMod` via `:=`. Overloads cover the
// common value shapes so the call site doesn't sprinkle `.toString`. A `Boolean`
// is an HTML boolean (presence) attribute: `true` sets the empty attribute,
// `false` removes it — right for `disabled`, `required`, `hidden`, and the like.
final class AttrKey(val name: String):
  def :=(v: String):  Mod = PropMod(name, Attr(v))
  def :=(v: Int):     Mod = PropMod(name, Attr(v.toString))
  def :=(v: Double):  Mod = PropMod(name, Attr(v.toString))
  def :=(v: Boolean): Mod = PropMod(name, BoolAttr(v))

// An *enumerated* attribute whose boolean value is the literal string `"true"` /
// `"false"`, not HTML presence — what every ARIA state/property and the
// `draggable` / `spellcheck` / `contenteditable` globals require. For these, the
// attribute being absent is semantically different from `"false"` (a screen
// reader treats a missing `aria-expanded` as "not expandable", not "collapsed"),
// so `:= false` must write `"false"` rather than remove the attribute. String /
// Int / Double values pass through unchanged, for token (`aria-current := "page"`)
// and numeric (`aria-level := 2`) states.
final class EnumAttrKey(val name: String):
  def :=(v: String):  Mod = PropMod(name, Attr(v))
  def :=(v: Int):     Mod = PropMod(name, Attr(v.toString))
  def :=(v: Double):  Mod = PropMod(name, Attr(v.toString))
  def :=(v: Boolean): Mod = PropMod(name, Attr(if v then "true" else "false"))

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

// Set the element's inner HTML directly from a trusted string — React's
// `dangerouslySetInnerHTML`. For rendering already-sanitized markup (markdown
// output, CMS content) that you'd otherwise have no way to inject. The string is
// written verbatim, so it MUST be trusted/sanitized — an attacker-controlled
// value here is an XSS hole. It replaces the element's content, so don't give the
// same element VNode children as well.
def unsafeHtml(html: String): Mod = PropMod("innerHTML", RawHtml(html))

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

// Uncontrolled-input seeds — the opposite of `value` / `checked`, which control
// a field on every render. `defaultValue` / `defaultChecked` set the field's
// initial value once, then let the DOM own it as the user types; they map to the
// DOM properties of the same name. For an `<input>` or `<textarea>` you don't
// drive from state.
val defaultValue   = AttrKey("defaultValue")
val defaultChecked = AttrKey("defaultChecked")

// Global attributes that apply to (almost) any element.
val hidden          = AttrKey("hidden")
val lang            = AttrKey("lang")
val dir             = AttrKey("dir")
val draggable       = EnumAttrKey("draggable")
val spellCheck      = EnumAttrKey("spellcheck")
val contentEditable = EnumAttrKey("contenteditable")
val accessKey       = AttrKey("accesskey")
val translate       = AttrKey("translate")
val inputMode       = AttrKey("inputmode")
val autoFocus       = AttrKey("autofocus")
val autoComplete    = AttrKey("autocomplete")

// Forms and inputs.
val min          = AttrKey("min")
val max          = AttrKey("max")
val step         = AttrKey("step")
val pattern      = AttrKey("pattern")
val required     = AttrKey("required")
val multiple     = AttrKey("multiple")
val size         = AttrKey("size")
val maxLength    = AttrKey("maxlength")
val minLength    = AttrKey("minlength")
val accept       = AttrKey("accept")
// `<form action>` is reached via `attr("action")` rather than a named val: the
// bare `action` would collide with `riposte.atoms.action` (the action-atom
// factory) for anyone importing both modules.
val method       = AttrKey("method")
val encType      = AttrKey("enctype")
val target       = AttrKey("target")
val rel          = AttrKey("rel")
val download     = AttrKey("download")
val list         = AttrKey("list")
val selected     = AttrKey("selected")
val cols         = AttrKey("cols")
val rows         = AttrKey("rows")
val wrap         = AttrKey("wrap")
val novalidate   = AttrKey("novalidate")
val formAction   = AttrKey("formaction")
val formMethod   = AttrKey("formmethod")

// Tables.
val colSpan = AttrKey("colspan")
val rowSpan = AttrKey("rowspan")
val headers = AttrKey("headers")
val scope   = AttrKey("scope")
// `<col>` / `<colgroup>`'s own `span` attribute (distinct from `colspan` above);
// named `spanAttr` because the bare `span` is taken by the `<span>` element.
val spanAttr = AttrKey("span")

// Lists, details, media, and other content.
val start            = AttrKey("start")
val reversed         = AttrKey("reversed")
val open             = AttrKey("open")
val dateTime         = AttrKey("datetime")
val srcSet           = AttrKey("srcset")
val sizes            = AttrKey("sizes")
val loading          = AttrKey("loading")
val decoding         = AttrKey("decoding")
val referrerPolicy   = AttrKey("referrerpolicy")
val crossOrigin      = AttrKey("crossorigin")
val poster           = AttrKey("poster")
val preload          = AttrKey("preload")
val controls         = AttrKey("controls")
val loop             = AttrKey("loop")
val muted            = AttrKey("muted")
val autoPlay         = AttrKey("autoplay")
val kind             = AttrKey("kind")
val srcLang          = AttrKey("srclang")
val default          = AttrKey("default")
val media            = AttrKey("media")
val content          = AttrKey("content")
val charset          = AttrKey("charset")
val async            = AttrKey("async")
val defer            = AttrKey("defer")

def attr(n: String): AttrKey = new AttrKey(n)

// `aria("label") := …` and `data("id") := …` build the `aria-*` / `data-*`
// attribute of that name — the two open-ended namespaces, so they get a helper
// rather than one val per possible suffix. Both are `EnumAttrKey`, so a boolean
// renders as `"true"`/`"false"` (what ARIA states require, and the sensible
// reading for a dataset value) rather than HTML presence.
def aria(n: String): EnumAttrKey = new EnumAttrKey("aria-" + n)
def data(n: String): EnumAttrKey = new EnumAttrKey("data-" + n)

// --- common events ---------------------------------------------------------

val onClick     = EventKey[dom.MouseEvent]("click")
val onDblClick  = EventKey[dom.MouseEvent]("dblclick")
val onInput     = EventKey[dom.Event]("input")
val onChange    = EventKey[dom.Event]("change")
val onSubmit    = EventKey[dom.Event]("submit")
val onReset     = EventKey[dom.Event]("reset")
val onInvalid   = EventKey[dom.Event]("invalid")
val onKeyDown   = EventKey[dom.KeyboardEvent]("keydown")
val onKeyUp     = EventKey[dom.KeyboardEvent]("keyup")
val onKeyPress  = EventKey[dom.KeyboardEvent]("keypress")
val onFocus     = EventKey[dom.FocusEvent]("focus")
val onBlur      = EventKey[dom.FocusEvent]("blur")
val onFocusIn   = EventKey[dom.FocusEvent]("focusin")
val onFocusOut  = EventKey[dom.FocusEvent]("focusout")

val onMouseDown  = EventKey[dom.MouseEvent]("mousedown")
val onMouseUp    = EventKey[dom.MouseEvent]("mouseup")
val onMouseMove  = EventKey[dom.MouseEvent]("mousemove")
val onMouseEnter = EventKey[dom.MouseEvent]("mouseenter")
val onMouseLeave = EventKey[dom.MouseEvent]("mouseleave")
val onMouseOver  = EventKey[dom.MouseEvent]("mouseover")
val onMouseOut   = EventKey[dom.MouseEvent]("mouseout")
val onContextMenu = EventKey[dom.MouseEvent]("contextmenu")
val onWheel      = EventKey[dom.WheelEvent]("wheel")
val onScroll     = EventKey[dom.Event]("scroll")

val onPointerDown   = EventKey[dom.PointerEvent]("pointerdown")
val onPointerUp     = EventKey[dom.PointerEvent]("pointerup")
val onPointerMove   = EventKey[dom.PointerEvent]("pointermove")
val onPointerEnter  = EventKey[dom.PointerEvent]("pointerenter")
val onPointerLeave  = EventKey[dom.PointerEvent]("pointerleave")
val onPointerCancel = EventKey[dom.PointerEvent]("pointercancel")

val onDrag      = EventKey[dom.DragEvent]("drag")
val onDragStart = EventKey[dom.DragEvent]("dragstart")
val onDragEnd   = EventKey[dom.DragEvent]("dragend")
val onDragEnter = EventKey[dom.DragEvent]("dragenter")
val onDragOver  = EventKey[dom.DragEvent]("dragover")
val onDragLeave = EventKey[dom.DragEvent]("dragleave")
val onDrop      = EventKey[dom.DragEvent]("drop")

val onTouchStart  = EventKey[dom.TouchEvent]("touchstart")
val onTouchEnd    = EventKey[dom.TouchEvent]("touchend")
val onTouchMove   = EventKey[dom.TouchEvent]("touchmove")
val onTouchCancel = EventKey[dom.TouchEvent]("touchcancel")

val onCopy  = EventKey[dom.ClipboardEvent]("copy")
val onCut   = EventKey[dom.ClipboardEvent]("cut")
val onPaste = EventKey[dom.ClipboardEvent]("paste")

val onLoad  = EventKey[dom.Event]("load")
val onError = EventKey[dom.Event]("error")

// A handler for any other event, typed as a plain `dom.Event`. For a typed
// custom event, name the type on the key directly: `EventKey[dom.WheelEvent](
// "wheel") := (e => …)`.
def on(n: String): EventKey[dom.Event] = new EventKey(n)

// Read the current value of the input/textarea/select that fired an event —
// the common need inside an `onInput` handler.
def targetValue(e: dom.Event): String =
  e.target.asInstanceOf[dom.html.Input].value

// --- common element tags ---------------------------------------------------

// Structure and sections.
def div(mods: Mod*):     VElement = h("div")(mods*)
def span(mods: Mod*):    VElement = h("span")(mods*)
def p(mods: Mod*):       VElement = h("p")(mods*)
def section(mods: Mod*): VElement = h("section")(mods*)
def article(mods: Mod*): VElement = h("article")(mods*)
def aside(mods: Mod*):   VElement = h("aside")(mods*)
def nav(mods: Mod*):     VElement = h("nav")(mods*)
def mainTag(mods: Mod*): VElement = h("main")(mods*)
def header(mods: Mod*):  VElement = h("header")(mods*)
def footer(mods: Mod*):  VElement = h("footer")(mods*)
def address(mods: Mod*): VElement = h("address")(mods*)
def h1(mods: Mod*):      VElement = h("h1")(mods*)
def h2(mods: Mod*):      VElement = h("h2")(mods*)
def h3(mods: Mod*):      VElement = h("h3")(mods*)
def h4(mods: Mod*):      VElement = h("h4")(mods*)
def h5(mods: Mod*):      VElement = h("h5")(mods*)
def h6(mods: Mod*):      VElement = h("h6")(mods*)
def hgroup(mods: Mod*):  VElement = h("hgroup")(mods*)

// Grouping and lists.
def ul(mods: Mod*):         VElement = h("ul")(mods*)
def ol(mods: Mod*):         VElement = h("ol")(mods*)
def li(mods: Mod*):         VElement = h("li")(mods*)
def dl(mods: Mod*):         VElement = h("dl")(mods*)
def dt(mods: Mod*):         VElement = h("dt")(mods*)
def dd(mods: Mod*):         VElement = h("dd")(mods*)
def menu(mods: Mod*):       VElement = h("menu")(mods*)
def blockquote(mods: Mod*): VElement = h("blockquote")(mods*)
def figure(mods: Mod*):     VElement = h("figure")(mods*)
def figcaption(mods: Mod*): VElement = h("figcaption")(mods*)
def pre(mods: Mod*):        VElement = h("pre")(mods*)
def hr(mods: Mod*):         VElement = h("hr")(mods*)

// Text-level semantics.
def a(mods: Mod*):      VElement = h("a")(mods*)
def code(mods: Mod*):   VElement = h("code")(mods*)
def strong(mods: Mod*): VElement = h("strong")(mods*)
def em(mods: Mod*):     VElement = h("em")(mods*)
def b(mods: Mod*):      VElement = h("b")(mods*)
def i(mods: Mod*):      VElement = h("i")(mods*)
def u(mods: Mod*):      VElement = h("u")(mods*)
def s(mods: Mod*):      VElement = h("s")(mods*)
def small(mods: Mod*):  VElement = h("small")(mods*)
def mark(mods: Mod*):   VElement = h("mark")(mods*)
def sub(mods: Mod*):    VElement = h("sub")(mods*)
def sup(mods: Mod*):    VElement = h("sup")(mods*)
def abbr(mods: Mod*):   VElement = h("abbr")(mods*)
def cite(mods: Mod*):   VElement = h("cite")(mods*)
def q(mods: Mod*):      VElement = h("q")(mods*)
def kbd(mods: Mod*):    VElement = h("kbd")(mods*)
def samp(mods: Mod*):   VElement = h("samp")(mods*)
def time(mods: Mod*):   VElement = h("time")(mods*)
def ins(mods: Mod*):    VElement = h("ins")(mods*)
def del(mods: Mod*):    VElement = h("del")(mods*)
def br(mods: Mod*):     VElement = h("br")(mods*)
def wbr(mods: Mod*):    VElement = h("wbr")(mods*)

// Forms.
def form(mods: Mod*):     VElement = h("form")(mods*)
def label(mods: Mod*):    VElement = h("label")(mods*)
def input(mods: Mod*):    VElement = h("input")(mods*)
def textarea(mods: Mod*): VElement = h("textarea")(mods*)
def button(mods: Mod*):   VElement = h("button")(mods*)
def select(mods: Mod*):   VElement = h("select")(mods*)
def option(mods: Mod*):   VElement = h("option")(mods*)
def optgroup(mods: Mod*): VElement = h("optgroup")(mods*)
def datalist(mods: Mod*): VElement = h("datalist")(mods*)
def fieldset(mods: Mod*): VElement = h("fieldset")(mods*)
def legend(mods: Mod*):   VElement = h("legend")(mods*)
def output(mods: Mod*):   VElement = h("output")(mods*)
def progress(mods: Mod*): VElement = h("progress")(mods*)
def meter(mods: Mod*):    VElement = h("meter")(mods*)

// Tables.
def table(mods: Mod*):    VElement = h("table")(mods*)
def caption(mods: Mod*):  VElement = h("caption")(mods*)
def colgroup(mods: Mod*): VElement = h("colgroup")(mods*)
def col(mods: Mod*):      VElement = h("col")(mods*)
def thead(mods: Mod*):    VElement = h("thead")(mods*)
def tbody(mods: Mod*):    VElement = h("tbody")(mods*)
def tfoot(mods: Mod*):    VElement = h("tfoot")(mods*)
def tr(mods: Mod*):       VElement = h("tr")(mods*)
def th(mods: Mod*):       VElement = h("th")(mods*)
def td(mods: Mod*):       VElement = h("td")(mods*)

// Embedded content and media.
def img(mods: Mod*):     VElement = h("img")(mods*)
def picture(mods: Mod*): VElement = h("picture")(mods*)
def source(mods: Mod*):  VElement = h("source")(mods*)
def video(mods: Mod*):   VElement = h("video")(mods*)
def audio(mods: Mod*):   VElement = h("audio")(mods*)
def track(mods: Mod*):   VElement = h("track")(mods*)
def canvas(mods: Mod*):  VElement = h("canvas")(mods*)
def iframe(mods: Mod*):  VElement = h("iframe")(mods*)
def embed(mods: Mod*):   VElement = h("embed")(mods*)

// Interactive elements.
def details(mods: Mod*): VElement = h("details")(mods*)
def summary(mods: Mod*): VElement = h("summary")(mods*)
def dialog(mods: Mod*):  VElement = h("dialog")(mods*)

// A bare text node, for the rare case the String conversion isn't triggered.
def text(s: String): VNode = VText(s)

// --- SVG --------------------------------------------------------------------

// SVG element tags. An `<svg>` (and everything nested under it) is mounted in the
// SVG namespace automatically, so these render as real graphics. The SVG `<text>`
// element is exposed as `svgText` because `text` already builds a DOM text node.
def svg(mods: Mod*):      VElement = h("svg")(mods*)
def g(mods: Mod*):        VElement = h("g")(mods*)
def path(mods: Mod*):     VElement = h("path")(mods*)
def circle(mods: Mod*):   VElement = h("circle")(mods*)
def rect(mods: Mod*):     VElement = h("rect")(mods*)
def line(mods: Mod*):     VElement = h("line")(mods*)
def polyline(mods: Mod*): VElement = h("polyline")(mods*)
def polygon(mods: Mod*):  VElement = h("polygon")(mods*)
def ellipse(mods: Mod*):  VElement = h("ellipse")(mods*)
def svgText(mods: Mod*):  VElement = h("text")(mods*)

// Common SVG attribute keys. Anything else goes through `attr("…")` — these are
// just the frequently-typed ones (exact case matters for SVG, e.g. `viewBox`).
val viewBox     = AttrKey("viewBox")
val fill        = AttrKey("fill")
val stroke      = AttrKey("stroke")
val strokeWidth = AttrKey("stroke-width")
val d           = AttrKey("d")
val cx          = AttrKey("cx")
val cy          = AttrKey("cy")
val r           = AttrKey("r")
val x           = AttrKey("x")
val y           = AttrKey("y")
val x1          = AttrKey("x1")
val y1          = AttrKey("y1")
val x2          = AttrKey("x2")
val y2          = AttrKey("y2")
val points      = AttrKey("points")
val width       = AttrKey("width")
val height      = AttrKey("height")
val transform   = AttrKey("transform")
