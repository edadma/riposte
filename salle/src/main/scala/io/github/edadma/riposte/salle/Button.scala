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

/** Props for [[Button]]. The three style axes are independent and match the proven
  * DaisyUI model: [[Color]] (semantic palette), [[ButtonVariant]] (fill style), and
  * [[Size]]. The active [[Skin]] turns them into classes. `disabled` is structural,
  * not styling, so it is reflected as the HTML attribute regardless of skin.
  */
final case class ButtonProps(
    label:    String,
    color:    Color         = Color.Default,
    variant:  ButtonVariant = ButtonVariant.Solid,
    size:     Size          = Size.Md,
    disabled: Boolean       = false,
    onClick:  () => Unit    = () => (),
)

/** A clickable button. Its classes come from the active [[Skin]] (salle's own look
  * or DaisyUI's, depending on the enclosing [[SkinProvider]]); the label is its
  * text, the click handler is wired typed, and the disabled state is reflected both
  * as the HTML presence attribute and as `data-state`, a styling/test hook that
  * works independently of the class soup.
  */
val Button = component[ButtonProps] { props =>
  val skin = useSkin()
  button(
    cls           := skin.button(props.color, props.variant, props.size),
    disabled      := props.disabled,
    data("state") := (if props.disabled then "disabled" else "default"),
    onClick       := (_ => props.onClick()),
    props.label,
  )
}
