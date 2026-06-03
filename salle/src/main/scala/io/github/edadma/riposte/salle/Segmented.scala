package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// A single-choice control rendered as one connected strip of segments — the "sort by" or
// grid-vs-list switch you reach for instead of a `<select>` when there are only a handful of
// mutually exclusive options worth showing at once. Data-driven like [[Tabs]]: pass the
// `options` (each a value, a label, and an optional icon) and salle renders the
// `role=radiogroup`, handles selection, keyboard, and the ARIA wiring. Selection is
// controllable (pass `value` to own it) or self-managed (seed it with `defaultValue`).
//
// Unlike [[Tabs]] there is no panel — the control reports the chosen value through `onChange`
// and the caller decides what changes. Keyboard follows the WAI-ARIA radio-group pattern with
// roving `tabindex`: only the selected segment is in the tab order, Left/Up and Right/Down move
// focus to the adjacent enabled segment and select it (wrapping, skipping disabled), and
// Home/End jump to the first/last enabled segment. Disabled segments carry the native
// `disabled` attribute, so they are skipped by both pointer and keyboard; `disabled` on the
// whole control disables every segment.
//
// State is mirrored to `data-*`: the root carries `data-part=segmented` + `data-size` +
// `data-block` + `data-disabled` + `data-value` (the selected value); each segment
// `data-part=segment` with `data-value`, `data-checked`, `data-disabled`.

/** One segment of a [[Segmented]] control: `value` identifies it (reported to `onChange` and
  * used in the ARIA wiring), `label` is the segment's content, `icon` is an optional leading
  * slot, and `disabled` removes it from pointer and keyboard selection. Build with
  * [[SegmentedOpt]]. */
type SegmentedOption = (
    value: String,
    label: VNode,
    icon: Option[VNode],
    disabled: Boolean,
)

/** Build a [[Segmented]] option: a `value`, the `label`, an optional leading `icon`, and whether
  * it is `disabled`. */
def SegmentedOpt(
    value:    String,
    label:    VNode,
    icon:     Option[VNode] = None,
    disabled: Boolean       = false,
): SegmentedOption =
  (value = value, label = label, icon = icon, disabled = disabled)

private val SegmentedImpl =
  component[(
      options:      Vector[SegmentedOption],
      value:        Option[String],
      defaultValue: Option[String],
      size:         Size,
      block:        Boolean,
      disabled:     Boolean,
      ariaLabel:    String,
      onChange:     String => Unit,
  )] { p =>
    val skin    = useSkin()
    val parts   = skin.segmented(p.size, p.block)
    val options = p.options

    // Uncontrolled selection seeds from `defaultValue`, falling back to the first option.
    val seed = p.defaultValue.orElse(options.headOption.map(_.value)).getOrElse("")
    val (selected, setSelected) = useControllable(p.value, seed, p.onChange)

    val base = useId()
    def segId(v: String) = base + "-seg-" + v

    def isEnabled(i: Int): Boolean =
      i >= 0 && i < options.length && !p.disabled && !options(i).disabled

    def firstEnabled: Int = options.indexWhere(o => !p.disabled && !o.disabled)
    def lastEnabled:  Int = options.lastIndexWhere(o => !p.disabled && !o.disabled)

    // Step to the next enabled segment in `dir` (+1/-1), wrapping; `from` may be any index.
    def nextEnabled(from: Int, dir: Int): Int =
      if options.isEmpty then -1
      else
        var i = from
        var n = 0
        while n < options.length do
          i = (i + dir + options.length) % options.length
          if isEnabled(i) then return i
          n += 1
        from

    // Select a segment and move real focus to it — selection follows focus, like Tabs's
    // automatic activation. The element exists regardless of the roving tabindex, so focusing
    // by id works before the re-render flips its tabindex to 0.
    def moveTo(i: Int): Unit =
      if isEnabled(i) then
        val v = options(i).value
        setSelected(v)
        val el = dom.document.getElementById(segId(v))
        if el != null then el.asInstanceOf[dom.html.Element].focus()

    val onSegKey: Int => dom.KeyboardEvent => Unit = idx =>
      e =>
        e.key match
          case "ArrowRight" | "ArrowDown" =>
            e.preventDefault(); moveTo(nextEnabled(idx, 1))
          case "ArrowLeft" | "ArrowUp" =>
            e.preventDefault(); moveTo(nextEnabled(idx, -1))
          case "Home" =>
            e.preventDefault(); moveTo(firstEnabled)
          case "End" =>
            e.preventDefault(); moveTo(lastEnabled)
          case _ => ()

    val segNodes: Seq[VNode] = options.zipWithIndex.map { (opt, i) =>
      val isSelected = opt.value == selected
      val isDisabled = p.disabled || opt.disabled
      val iconNode: Mod = opt.icon match
        case Some(ic) => span(cls := parts.icon, data("part") := "icon", aria("hidden") := true, ic)
        case None     => NoMod
      button(
        cls := (parts.item
          + (if isSelected then " " + parts.active else "")
          + (if isDisabled then " " + parts.disabled else "")),
        id               := segId(opt.value),
        typ              := "button",
        role             := "radio",
        data("part")     := "segment",
        data("value")    := opt.value,
        data("checked")  := isSelected,
        data("disabled") := isDisabled,
        aria("checked")  := (if isSelected then "true" else "false"),
        tabIndex         := (if isSelected then "0" else "-1"),
        disabled         := isDisabled,
        onClick          := (_ => if !isDisabled then setSelected(opt.value)),
        onKeyDown        := onSegKey(i),
        iconNode,
        span(cls := parts.label, opt.label),
      )
    }

    div(
      cls              := parts.root,
      role             := "radiogroup",
      aria("label")    := p.ariaLabel,
      data("part")     := "segmented",
      data("size")     := p.size.token,
      data("block")    := p.block,
      data("disabled") := p.disabled,
      data("value")    := selected,
      segNodes,
    )
  }

/** A single-choice segmented control: pass the `options` ([[SegmentedOpt]]) and salle renders a
  * connected `role=radiogroup` strip. Selection is controllable via `value` (the caller owns it,
  * told of intent through `onChange`) or self-managed from `defaultValue` (defaulting to the
  * first option). `size` scales the strip, `block` makes it span the full container width
  * (segments share the space equally), and `disabled` disables the whole control. Fully
  * keyboard-driven with roving `tabindex` and selection-follows-focus; classes come from the
  * active [[Skin]] and state is mirrored to `data-*`. `ariaLabel` names the group for assistive
  * tech. */
def Segmented(
    options:      Seq[SegmentedOption],
    value:        Option[String] = None,
    defaultValue: Option[String] = None,
    size:         Size           = Size.Md,
    block:        Boolean        = false,
    disabled:     Boolean        = false,
    ariaLabel:    String         = "",
    onChange:     String => Unit = _ => (),
): VNode =
  SegmentedImpl(
    (
      options = options.toVector,
      value = value,
      defaultValue = defaultValue,
      size = size,
      block = block,
      disabled = disabled,
      ariaLabel = ariaLabel,
      onChange = onChange,
    ),
  )
