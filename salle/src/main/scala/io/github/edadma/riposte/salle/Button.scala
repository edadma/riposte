package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

/** The visual style of a [[Button]], orthogonal to its [[Color]]. `Solid` is the
  * filled default; the rest progressively reduce emphasis (`Ghost`/`Link` are the
  * quietest). Mirrors DaisyUI's button style axis. */
enum ButtonVariant:
  case Solid, Outline, Dash, Soft, Ghost, Link

  /** The lowercase modifier token (`"outline"`); empty for [[Solid]], the default
    * fill that needs no modifier class. */
  def token: String = this match
    case Solid => ""
    case v     => v.toString.toLowerCase

// The component proper. Props travel as a named tuple (no case class); the render
// reads them as `p.field`, which keeps the DSL's `disabled` / `onClick` keys in scope
// unshadowed. The public `Button` below adds the defaults a named tuple can't carry.
private val ButtonImpl =
  component[
    (label: String, color: Color, variant: ButtonVariant, size: Size, disabled: Boolean, onClick: () => Unit),
  ] { p =>
    val skin = useSkin()
    button(
      cls           := skin.button(p.color, p.variant, p.size),
      disabled      := p.disabled,
      data("state") := (if p.disabled then "disabled" else "default"),
      onClick       := (_ => p.onClick()),
      p.label,
    )
  }

/** A clickable button. Its classes come from the active [[Skin]] (salle's own look or
  * DaisyUI's, per the enclosing [[SkinProvider]]); the label is its text, the click
  * handler is wired typed, and the disabled state is reflected both as the HTML
  * presence attribute and as `data-state`. The three style axes — [[Color]],
  * [[ButtonVariant]], [[Size]] — are independent and default to a plain medium button.
  */
def Button(
    label:    String,
    color:    Color         = Color.Default,
    variant:  ButtonVariant = ButtonVariant.Solid,
    size:     Size          = Size.Md,
    disabled: Boolean       = false,
    onClick:  () => Unit    = () => (),
): VNode =
  ButtonImpl((label = label, color = color, variant = variant, size = size, disabled = disabled, onClick = onClick))
