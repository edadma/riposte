package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// Loading affordances — the things that say "something is happening". Three pieces, two
// kinds of "happening":
//
//   • Spinner        — an indeterminate busy indicator (we don't know how far along we are):
//                      a small animated mark, optionally wrapping content as a blur overlay.
//   • Progress       — a determinate linear bar (we know the fraction): native <progress>,
//                      or indeterminate when no value is given.
//   • RadialProgress — a determinate ring with the percentage in the middle.
//
// The animated mark / bar / ring is the one skin-dependent piece (SalleSkin draws its own,
// DaisyUI uses `loading`/`progress`/`radial-progress`), so each goes through a `Skin` method.
// The surrounding layout (the overlay wrapper, the centred ring text) is plain inline geometry
// that renders the same under any skin without depending on salle.css being loaded.
//
// State is mirrored to `data-*`: Spinner carries `data-part`(spinner|spinner-wrap|overlay|
// content|indicator|tip|label); Progress carries `data-part=progress` + `data-indeterminate`
// + `data-value`; RadialProgress carries `data-part=radial-progress` + `data-value`. The
// indeterminate indicators are decorative (`aria-hidden` mark + a screen-reader label, or
// `role=status`/`aria-live`); the determinate ones expose the value to assistive tech.

/** The animation style of a [[Spinner]]. `Spinner` (a spinning ring) is the default; the
  * others are alternate looks. Mirrors DaisyUI's `loading-*` family. */
enum SpinnerType:
  case Spinner, Dots, Ring, Ball, Bars, Infinity

  /** The lowercase modifier token (`"dots"`). Patterns are qualified because the bare
    * `Spinner` case name would otherwise clash with the [[Spinner]] component in this package. */
  def token: String = this match
    case SpinnerType.Spinner  => "spinner"
    case SpinnerType.Dots     => "dots"
    case SpinnerType.Ring     => "ring"
    case SpinnerType.Ball     => "ball"
    case SpinnerType.Bars     => "bars"
    case SpinnerType.Infinity => "infinity"

// Render a number for an attribute without a spurious trailing ".0" — `70.0` becomes `"70"`,
// `33.5` stays `"33.5"`. Used for progress value/max and the radial ring's --value/aria.
private def numStr(d: Double): String =
  if d == d.toLong.toDouble then d.toLong.toString else d.toString

private val SpinnerImpl =
  container[(kind: SpinnerType, size: Size, color: Color, spinning: Boolean, tip: String, label: String)] {
    (p, children) =>
      val skin            = useSkin()
      val accessibleLabel = if p.label.nonEmpty then p.label else if p.tip.nonEmpty then p.tip else "Loading"

      // The mark itself — an empty element styled by the skin, decorative. The text comes from
      // the tip (shown) or an off-screen label (for assistive tech when there is no tip).
      val indicator: VNode =
        span(cls := skin.spinner(p.kind, p.size, p.color), data("part") := "indicator", aria("hidden") := true)

      val caption: Mod =
        if p.tip.nonEmpty then div(cls := "salle-spinner-tip", data("part") := "tip", p.tip)
        else span(cls := "salle-spinner-sr", data("part") := "label", accessibleLabel)

      if children.isEmpty then
        // Standalone indicator. When not spinning there is nothing to show.
        if !p.spinning then VEmpty
        else
          div(
            cls          := "salle-spinner-box",
            data("part")  := "spinner",
            role         := "status",
            aria("live") := "polite",
            indicator,
            caption,
          )
      else
        // Overlay mode: wrap the content and, while spinning, float the indicator over it with
        // the content dimmed and inert. The busy state rides `aria-busy` on the wrapper.
        val overlay: Mod =
          if p.spinning then
            div(
              cls          := "salle-spinner-overlay",
              data("part")  := "overlay",
              role         := "status",
              aria("live") := "polite",
              indicator,
              caption,
            )
          else NoMod
        div(
          cls          := "salle-spinner-wrap",
          data("part")  := "spinner-wrap",
          aria("busy") := p.spinning,
          overlay,
          div(
            cls            := "salle-spinner-content" + (if p.spinning then " salle-spinner-content--busy" else ""),
            data("part")   := "content",
            aria("hidden") := p.spinning,
            children,
          ),
        )
  }

/** A busy indicator for work of unknown duration. Used bare, it is a small animated mark with
  * an off-screen "Loading" label (override via `label`); give it a `tip` to show a caption
  * beneath. Pass `children` to switch to overlay mode — the mark floats over the (dimmed,
  * inert) content while `spinning`, and the content shows normally once it is `false`. `kind`
  * picks the animation, `size` the scale, `color` the tint. The look comes from the active
  * [[Skin]]; state is mirrored to `data-*`. */
def Spinner(
    kind:     SpinnerType = SpinnerType.Spinner,
    size:     Size        = Size.Md,
    color:    Color       = Color.Default,
    spinning: Boolean     = true,
    tip:      String      = "",
    label:    String      = "",
)(children: VNode*): VNode =
  SpinnerImpl((kind = kind, size = size, color = color, spinning = spinning, tip = tip, label = label))(children*)

private val ProgressImpl =
  component[(value: Option[Double], max: Double, color: Color, label: String)] { p =>
    val skin = useSkin()

    // A determinate bar carries its `value`; omitting it makes the native <progress> render
    // its indeterminate animation, so we drop the attribute entirely in that case.
    val valueMod: Mod = p.value match
      case Some(v) => value := numStr(v)
      case None    => NoMod

    val labelMod: Mod = if p.label.nonEmpty then aria("label") := p.label else NoMod

    progress(
      cls                    := skin.progress(p.color),
      data("part")           := "progress",
      data("indeterminate")  := p.value.isEmpty,
      data("value")          := (p.value match { case Some(v) => numStr(v); case None => "" }),
      max                    := numStr(p.max),
      valueMod,
      labelMod,
    )
  }

/** A linear progress bar. Give it a `value` (out of `max`, default 100) for a determinate
  * bar; pass `value = None` for the indeterminate animation (work in progress, fraction
  * unknown). `color` tints the fill, `label` provides an accessible name. Renders the native
  * `<progress>` element so assistive tech reads the value for free; the look comes from the
  * active [[Skin]] and the state is mirrored to `data-*` (`data-indeterminate`, `data-value`). */
def Progress(
    value: Option[Double] = None,
    max:   Double         = 100,
    color: Color          = Color.Default,
    label: String         = "",
): VNode =
  ProgressImpl((value = value, max = max, color = color, label = label))

private val RadialProgressImpl =
  container[(value: Double, size: String, thickness: String, color: Color, showValue: Boolean)] { (p, children) =>
    val skin = useSkin()

    // The ring is drawn from the --value custom property (a 0–100 number the CSS reads as a
    // percentage); size/thickness are optional overrides of the skin's defaults.
    var sty = Map("--value" -> numStr(p.value))
    if p.size.nonEmpty then sty += "--size"           -> p.size
    if p.thickness.nonEmpty then sty += "--thickness" -> p.thickness

    // The centre shows custom content if given, else the percentage (unless suppressed).
    val centre: Mod =
      if children.nonEmpty then fragment(children*)
      else if p.showValue then span(cls := "salle-radial-progress__value", data("part") := "value", numStr(p.value) + "%")
      else NoMod

    div(
      cls               := skin.radialProgress(p.color),
      data("part")      := "radial-progress",
      data("value")     := numStr(p.value),
      role              := "progressbar",
      aria("valuenow")  := numStr(p.value),
      aria("valuemin")  := "0",
      aria("valuemax")  := "100",
      style             := sty,
      centre,
    )
  }

/** A circular progress ring with the percentage in its centre. `value` is a 0–100 number;
  * `size` (a CSS length like `"5rem"`) and `thickness` (like `"4px"`) override the skin's
  * defaults. `color` tints the ring. By default the centre shows `"{value}%"`; pass
  * `showValue = false` for a bare ring, or `children` for custom centre content (an icon, a
  * label). Exposes the value to assistive tech (`role=progressbar`, `aria-valuenow`); the look
  * comes from the active [[Skin]] and the value is mirrored to `data-value`. */
def RadialProgress(
    value:     Double  = 0,
    size:      String  = "",
    thickness: String  = "",
    color:     Color   = Color.Default,
    showValue: Boolean = true,
)(children: VNode*): VNode =
  RadialProgressImpl(
    (value = value, size = size, thickness = thickness, color = color, showValue = showValue),
  )(children*)
