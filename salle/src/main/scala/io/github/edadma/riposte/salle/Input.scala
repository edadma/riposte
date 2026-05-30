package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

/** Props for [[Input]], a single-line text field. The field is *controlled* when
  * `value` is `Some` (the app drives it and is notified via `onChange`) and
  * *uncontrolled* when `value` is `None` (salle holds the text internally, seeded
  * from `defaultValue`). [[Color]] tints the focus ring; `invalid` switches to the
  * error treatment and sets `aria-invalid`.
  */
final case class InputProps(
    value:        Option[String] = None,
    defaultValue: String         = "",
    placeholder:  String         = "",
    inputType:    String         = "text",
    color:        Color          = Color.Default,
    size:         Size           = Size.Md,
    disabled:     Boolean        = false,
    invalid:      Boolean        = false,
    onChange:     String => Unit = _ => (),
)

/** A single-line text input. Classes come from the active [[Skin]]; the text is
  * controlled-or-uncontrolled via [[useControllable]]; validity and disabled state
  * are exposed through `aria-invalid` and `data-state` for accessibility and styling.
  */
val Input = component[InputProps] { props =>
  val skin               = useSkin()
  val (current, setText) = useControllable(props.value, props.defaultValue, props.onChange)
  input(
    cls             := skin.input(props.color, props.size, props.invalid),
    typ             := props.inputType,
    placeholder     := props.placeholder,
    value           := current,
    disabled        := props.disabled,
    aria("invalid") := props.invalid,
    data("state") := (
      if props.disabled then "disabled"
      else if props.invalid then "invalid"
      else "default"
    ),
    onInput := (e => setText(e.target.asInstanceOf[dom.html.Input].value)),
  )
}
