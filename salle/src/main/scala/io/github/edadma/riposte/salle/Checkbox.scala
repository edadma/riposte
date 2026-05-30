package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

/** Props for [[Checkbox]], a boolean control. Controlled when `checked` is `Some`,
  * otherwise salle holds the state internally (seeded from `defaultChecked`). A
  * non-empty `label` is rendered beside the box and made clickable. */
final case class CheckboxProps(
    label:          String          = "",
    checked:        Option[Boolean] = None,
    defaultChecked: Boolean         = false,
    color:          Color           = Color.Default,
    size:           Size            = Size.Md,
    disabled:       Boolean         = false,
    onChange:       Boolean => Unit = _ => (),
)

/** A checkbox. Classes come from the active [[Skin]]; the checked state is
  * controlled-or-uncontrolled via [[useControllable]] and mirrored to `data-state`.
  * With a non-empty `label`, the box and text are wrapped in a `<label>` so clicking
  * the text toggles the box. */
val Checkbox = component[CheckboxProps] { props =>
  val skin                  = useSkin()
  val (current, setChecked) = useControllable(props.checked, props.defaultChecked, props.onChange)
  val box = input(
    typ           := "checkbox",
    cls           := skin.checkbox(props.color, props.size),
    checked       := current,
    disabled      := props.disabled,
    data("state") := stateOf(props.disabled, current),
    onChange      := (e => setChecked(e.target.asInstanceOf[dom.html.Input].checked)),
  )
  if props.label.isEmpty then box
  else label(cls := "salle-check-label", box, span(props.label))
}
