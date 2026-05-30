package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// Props as a named tuple; the public `Toggle` below carries the defaults.
private val ToggleImpl =
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
      typ             := "checkbox",
      role            := "switch",
      cls             := skin.toggle(p.color, p.size),
      checked         := current,
      disabled        := p.disabled,
      aria("checked") := current,
      data("state")   := stateOf(p.disabled, current),
      onChange        := (e => setChecked(e.target.asInstanceOf[dom.html.Input].checked)),
    )
    if p.label.isEmpty then box
    else label(cls := "salle-toggle-label", box, span(p.label))
  }

/** A switch — a checkbox input styled as a slider, with `role="switch"` and
  * `aria-checked` so assistive tech reads it as a switch. Same controlled/uncontrolled
  * model as [[Checkbox]]; classes come from the active [[Skin]].
  */
def Toggle(
    label:          String          = "",
    checked:        Option[Boolean] = None,
    defaultChecked: Boolean         = false,
    color:          Color           = Color.Default,
    size:           Size            = Size.Md,
    disabled:       Boolean         = false,
    onChange:       Boolean => Unit = _ => (),
): VNode =
  ToggleImpl(
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
