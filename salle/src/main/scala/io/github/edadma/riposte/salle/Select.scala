package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js

// A single-select dropdown built as a CUSTOM ARIA combobox/listbox rather than a
// native `<select>`. A native control can't host search, custom option rendering, a
// clear affordance, or styled options; the combobox pattern can, and it gives full
// control over keyboard and ARIA. The DOM is: a `role=combobox` trigger that owns a
// `role=listbox` popup of `role=option` items, wired with `aria-activedescendant`.
//
// Every meaningful state is mirrored to `data-*` so tests and consumers select on
// stable, skin-independent hooks rather than CSS classes: the root carries
// `data-state` (open|closed|disabled) and `data-value`; each option carries
// `data-value`, `data-selected`, `data-active`, and `data-disabled`.

/** One choice in a [[Select]]: its submitted `value`, the `label` shown to the user,
  * and whether it is `disabled` (skipped by keyboard and pointer). Built with [[Opt]];
  * a plain tuple, never a case class. */
type SelectOption = (value: String, label: String, disabled: Boolean)

/** Construct a [[SelectOption]]. `label` defaults to the `value` when omitted; an
  * option may be `disabled`. `Opt("us", "United States")`, `Opt("ca")`. */
def Opt(value: String, label: String = "", disabled: Boolean = false): SelectOption =
  (value = value, label = if label.isEmpty then value else label, disabled = disabled)

// Mutable across-render scratch for typeahead: a single object held in a state cell.
// `useState` is by-name, so the object is allocated once (first render) and then
// survives re-renders, with writes to its fields never triggering one.
private class TypeaheadState:
  var buf:  String = ""
  var time: Double = 0d

private val SelectImpl =
  component[
    (
        options: Vector[SelectOption],
        value: Option[String],
        defaultValue: String,
        placeholder: String,
        color: Color,
        size: Size,
        disabled: Boolean,
        invalid: Boolean,
        clearable: Boolean,
        name: String,
        onChange: String => Unit,
    ),
  ] { p =>
    val skin                = useSkin()
    val parts               = skin.select(p.color, p.size, p.invalid)
    val (current, setValue) = useControllable(p.value, p.defaultValue, p.onChange)
    val (open, setOpen, _)     = useState(false)
    val (active, setActive, _) = useState(-1)

    // Stable ids tie the trigger to the listbox and each option, for aria-controls /
    // aria-activedescendant and for tests to address options directly.
    val base          = useId()
    val triggerId     = base + "-trigger"
    val listId        = base + "-list"
    def optId(i: Int) = base + "-opt-" + i.toString

    val wrapper = useRef[dom.Element | Null](null)
    val ta      = useState(new TypeaheadState)._1

    val opts          = p.options
    val selectedIndex = opts.indexWhere(_.value == current)
    val selected      = if selectedIndex >= 0 then Some(opts(selectedIndex)) else None

    def isEnabled(i: Int): Boolean = i >= 0 && i < opts.length && !opts(i).disabled
    def firstEnabled: Int          = opts.indexWhere(!_.disabled)
    def lastEnabled: Int           = opts.lastIndexWhere(!_.disabled)

    // Step to the next enabled option in `dir` (+1/-1), wrapping; `from` may be -1.
    def nextEnabled(from: Int, dir: Int): Int =
      if opts.isEmpty then -1
      else
        var i = from
        var n = 0
        while n < opts.length do
          i = (i + dir + opts.length) % opts.length
          if !opts(i).disabled then return i
          n += 1
        from

    def openMenu(): Unit =
      setOpen(true)
      setActive(if selectedIndex >= 0 then selectedIndex else firstEnabled)

    def commit(i: Int): Unit =
      if isEnabled(i) then
        setValue(opts(i).value)
        setOpen(false)

    // Jump to the next enabled option whose label starts with the accumulated keys.
    // A pause (>600ms) starts a fresh buffer; a single key advances past the current
    // option so repeated taps cycle same-initial matches.
    def typeahead(ch: String): Unit =
      val now = js.Date.now()
      ta.buf = (if now - ta.time > 600 then "" else ta.buf) + ch.toLowerCase
      ta.time = now
      val n = opts.length
      if n > 0 then
        val from   = if active >= 0 then active else 0
        val single = ta.buf.length == 1
        var found  = -1
        var idx    = 0
        while idx < n && found < 0 do
          val cand = (from + (if single then idx + 1 else idx)) % n
          val o    = opts(cand)
          if !o.disabled && o.label.toLowerCase.startsWith(ta.buf) then found = cand
          idx += 1
        if found >= 0 then
          if !open then setOpen(true)
          setActive(found)

    // Dismiss on a press outside the whole control (robust; not a blur race).
    useClickOutside(wrapper, open, () => setOpen(false))

    // Keep the active option visible as the highlight moves.
    useEffect(
      () =>
        if open && active >= 0 then
          val el = dom.document.getElementById(optId(active))
          if el != null then
            val dyn = el.asInstanceOf[js.Dynamic]
            // Guard the call: some environments (notably jsdom) don't implement it.
            if !js.isUndefined(dyn.scrollIntoView) then dyn.scrollIntoView(js.Dynamic.literal(block = "nearest"))
        noCleanup
      ,
      Array(open, active),
    )

    val onKey: dom.KeyboardEvent => Unit = e =>
      if !p.disabled then
        e.key match
          case "ArrowDown" =>
            e.preventDefault()
            if open then setActive(nextEnabled(active, 1)) else openMenu()
          case "ArrowUp" =>
            e.preventDefault()
            if open then setActive(nextEnabled(active, -1)) else openMenu()
          case "Home" =>
            if open then { e.preventDefault(); setActive(firstEnabled) }
          case "End" =>
            if open then { e.preventDefault(); setActive(lastEnabled) }
          case "Enter" =>
            e.preventDefault()
            if open then commit(active) else openMenu()
          case " " | "Spacebar" =>
            e.preventDefault()
            if open then commit(active) else openMenu()
          case "Escape" =>
            if open then { e.preventDefault(); setOpen(false) }
          case "Tab" =>
            if open then setOpen(false) // let focus move on; just close
          case k if k.length == 1 =>
            typeahead(k)
          case _ => ()

    val clearControl: Mod =
      if p.clearable && current.nonEmpty && !p.disabled then
        span(
          cls           := parts.clear,
          role          := "button",
          aria("label") := "Clear selection",
          data("part")  := "clear",
          onMouseDown   := (e => e.preventDefault()), // keep focus on the trigger
          onClick       := (e => { e.stopPropagation(); setValue("") }),
          unsafeHtml(ClearIcon),
        )
      else NoMod

    val triggerNode =
      div(
        cls                       := parts.trigger,
        id                        := triggerId,
        role                      := "combobox",
        tabIndex                  := (if p.disabled then "-1" else "0"),
        aria("haspopup")          := "listbox",
        aria("expanded")          := (if open then "true" else "false"),
        aria("controls")          := listId,
        aria("invalid")           := (if p.invalid then "true" else "false"),
        aria("disabled")          := (if p.disabled then "true" else "false"),
        aria("activedescendant")  := (if open && active >= 0 then optId(active) else ""),
        data("state")             := (if p.disabled then "disabled" else if open then "open" else "closed"),
        onClick                   := (_ => if !p.disabled then (if open then setOpen(false) else openMenu())),
        onKeyDown                 := onKey,
        onBlur                    := (_ => if open then setOpen(false)),
        span(
          cls                   := parts.value,
          data("placeholder")   := (if selected.isEmpty then "true" else "false"),
          selected.map(_.label).getOrElse(p.placeholder),
        ),
        clearControl,
        span(cls := parts.arrow, aria("hidden") := "true", unsafeHtml(CaretIcon)),
      )

    val listNode: Mod =
      if open then
        ul(
          cls  := parts.list,
          id   := listId,
          role := "listbox",
          opts.zipWithIndex.map { (o, i) =>
            li(
              cls               := parts.option,
              id                := optId(i),
              role              := "option",
              aria("selected")  := (if i == selectedIndex then "true" else "false"),
              aria("disabled")  := (if o.disabled then "true" else "false"),
              data("value")     := o.value,
              data("selected")  := (if i == selectedIndex then "true" else "false"),
              data("active")    := (if i == active then "true" else "false"),
              data("disabled")  := (if o.disabled then "true" else "false"),
              onMouseDown       := (e => e.preventDefault()),  // don't steal focus from the trigger
              onMouseEnter      := (_ => if !o.disabled then setActive(i)),
              onClick           := (_ => commit(i)),
              span(o.label),
            )
          },
        )
      else NoMod

    // A hidden mirror so a `name`d select participates in native form submission.
    val hidden: Mod =
      if p.name.nonEmpty then input(typ := "hidden", name := p.name, value := current)
      else NoMod

    div(
      cls           := parts.root,
      ref           := wrapper,
      data("state") := (if p.disabled then "disabled" else if open then "open" else "closed"),
      data("value") := current,
      triggerNode,
      listNode,
      hidden,
    )
  }

/** A single-select dropdown. The chosen `value` is *controlled* when `Some` (the app
  * drives it via `onChange`) and *uncontrolled* otherwise (salle holds it, seeded from
  * `defaultValue`). `options` are built with [[Opt]]. [[Color]] tints the focus ring,
  * `invalid` switches to the error treatment, `clearable` adds a reset affordance, and
  * a non-empty `name` emits a hidden input for form submission. Classes come from the
  * active [[Skin]]; it is a fully keyboard-driven ARIA combobox with `data-*` state.
  */
def Select(
    options:      Seq[SelectOption],
    value:        Option[String] = None,
    defaultValue: String         = "",
    placeholder:  String         = "Select…",
    color:        Color          = Color.Default,
    size:         Size           = Size.Md,
    disabled:     Boolean        = false,
    invalid:      Boolean        = false,
    clearable:    Boolean        = false,
    name:         String         = "",
    onChange:     String => Unit = _ => (),
): VNode =
  SelectImpl(
    (
      options = options.toVector,
      value = value,
      defaultValue = defaultValue,
      placeholder = placeholder,
      color = color,
      size = size,
      disabled = disabled,
      invalid = invalid,
      clearable = clearable,
      name = name,
      onChange = onChange,
    ),
  )

// Feather chevron-down / x, drawn with `currentColor`; injected as trusted static
// innerHTML so they parse into correctly-namespaced SVG nodes.
private val CaretIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="6 9 12 15 18 9"></polyline></svg>"""

private val ClearIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""
