package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// Props as a named tuple; the public `Checkbox` below carries the defaults.
private val CheckboxImpl =
  component[
    (
        label: String,
        checked: Option[Boolean],
        defaultChecked: Boolean,
        color: Color,
        size: Size,
        disabled: Boolean,
        onChange: Boolean => Unit,
    ),
  ] { p =>
    val skin                  = useSkin()
    val (current, setChecked) = useControllable(p.checked, p.defaultChecked, p.onChange)
    val box = input(
      typ           := "checkbox",
      cls           := skin.checkbox(p.color, p.size),
      checked       := current,
      disabled      := p.disabled,
      data("state") := stateOf(p.disabled, current),
      onChange      := (e => setChecked(e.target.asInstanceOf[dom.html.Input].checked)),
    )
    if p.label.isEmpty then box
    else label(cls := "salle-check-label", box, span(p.label))
  }

/** A checkbox. Controlled when `checked` is `Some`, otherwise salle holds the state
  * internally (seeded from `defaultChecked`). Classes come from the active [[Skin]];
  * the checked state mirrors to `data-state`. A non-empty `label` wraps the box in a
  * `<label>` so clicking the text toggles the box.
  */
def Checkbox(
    label:          String          = "",
    checked:        Option[Boolean] = None,
    defaultChecked: Boolean         = false,
    color:          Color           = Color.Default,
    size:           Size            = Size.Md,
    disabled:       Boolean         = false,
    onChange:       Boolean => Unit = _ => (),
): VNode =
  CheckboxImpl(
    (
      label = label,
      checked = checked,
      defaultChecked = defaultChecked,
      color = color,
      size = size,
      disabled = disabled,
      onChange = onChange,
    ),
  )
