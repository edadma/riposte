package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

/** Props for [[Toggle]], a switch-style boolean control. Same controlled/uncontrolled
  * model as [[Checkbox]]. */
final case class ToggleProps(
    label:          String          = "",
    checked:        Option[Boolean] = None,
    defaultChecked: Boolean         = false,
    color:          Color           = Color.Default,
    size:           Size            = Size.Md,
    disabled:       Boolean         = false,
    onChange:       Boolean => Unit = _ => (),
)

/** A switch — a checkbox input styled as a slider, with `role="switch"` and
  * `aria-checked` so assistive tech reads it as a switch rather than a checkbox.
  * Classes come from the active [[Skin]]; state is controlled-or-uncontrolled via
  * [[useControllable]]. */
val Toggle = component[ToggleProps] { props =>
  val skin                  = useSkin()
  val (current, setChecked) = useControllable(props.checked, props.defaultChecked, props.onChange)
  val box = input(
    typ             := "checkbox",
    role            := "switch",
    cls             := skin.toggle(props.color, props.size),
    checked         := current,
    disabled        := props.disabled,
    aria("checked") := current,
    data("state")   := stateOf(props.disabled, current),
    onChange        := (e => setChecked(e.target.asInstanceOf[dom.html.Input].checked)),
  )
  if props.label.isEmpty then box
  else label(cls := "salle-toggle-label", box, span(props.label))
}
