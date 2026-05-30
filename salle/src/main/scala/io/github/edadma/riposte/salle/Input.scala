package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// Props as a named tuple; the public `Input` below carries the defaults.
private val InputImpl =
  component[
    (
        value: Option[String],
        defaultValue: String,
        placeholder: String,
        inputType: String,
        color: Color,
        size: Size,
        disabled: Boolean,
        invalid: Boolean,
        onChange: String => Unit,
    ),
  ] { p =>
    val skin               = useSkin()
    val (current, setText) = useControllable(p.value, p.defaultValue, p.onChange)
    input(
      cls             := skin.input(p.color, p.size, p.invalid),
      typ             := p.inputType,
      placeholder     := p.placeholder,
      value           := current,
      disabled        := p.disabled,
      aria("invalid") := p.invalid,
      data("state") := (
        if p.disabled then "disabled"
        else if p.invalid then "invalid"
        else "default"
      ),
      onInput := (e => setText(e.target.asInstanceOf[dom.html.Input].value)),
    )
  }

/** A single-line text input. The field is *controlled* when `value` is `Some` (the
  * app drives it, notified via `onChange`) and *uncontrolled* when `value` is `None`
  * (salle holds the text internally, seeded from `defaultValue`). [[Color]] tints the
  * focus ring; `invalid` switches to the error treatment and sets `aria-invalid`.
  * Classes come from the active [[Skin]]; the value path runs through
  * [[useControllable]].
  */
def Input(
    value:        Option[String] = None,
    defaultValue: String         = "",
    placeholder:  String         = "",
    inputType:    String         = "text",
    color:        Color          = Color.Default,
    size:         Size           = Size.Md,
    disabled:     Boolean        = false,
    invalid:      Boolean        = false,
    onChange:     String => Unit = _ => (),
): VNode =
  InputImpl(
    (
      value = value,
      defaultValue = defaultValue,
      placeholder = placeholder,
      inputType = inputType,
      color = color,
      size = size,
      disabled = disabled,
      invalid = invalid,
      onChange = onChange,
    ),
  )
