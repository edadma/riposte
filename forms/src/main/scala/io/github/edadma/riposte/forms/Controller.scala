package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*

// What a `Controller`'s render function receives for the wired field. Unlike `register`
// (which spreads DOM props onto an uncontrolled element), a controlled component is driven
// by these: read `value` to display, call `onChange` with the new value, `onBlur` when it
// loses focus. `name` is handy for labels/ids. The store owns the value, so `value` is
// live — the `Controller` re-renders as it changes.
type ControllerField = (
    name:     String,
    value:    Any,
    onChange: Any => Unit,
    onBlur:   () => Unit,
)

// The validation state of a controlled field, mirroring react-hook-form's `fieldState`:
// the current `error` (if any), whether the field has been touched or has diverged from
// its default, and `invalid` (a convenience for `error.isDefined`).
type ControllerFieldState = (
    error:     Option[FieldError],
    isTouched: Boolean,
    isDirty:   Boolean,
    invalid:   Boolean,
)

// The bundle handed to a `Controller`'s render function — react-hook-form's
// `{ field, fieldState, formState }`.
type ControllerArgs = (
    field:      ControllerField,
    fieldState: ControllerFieldState,
    formState:  FormState,
)

// Adapt a *controlled* component into a form. Where `register` wires an uncontrolled
// `<input>` whose value the DOM owns, `Controller` is for components that take a `value`
// and emit changes through a callback — exactly the shape of salle's `Input` / `Select` /
// `Checkbox`. It subscribes to the field's value (so it re-renders as the value changes)
// and to the form state (so `fieldState.error` is live), then calls `render` with both:
//
//   Controller("email", f.control, Rules(required = true)) { a =>
//     SalleInput(
//       value    = a.field.value.asInstanceOf[String],
//       onChange = s => a.field.onChange(s),
//       onBlur   = _ => a.field.onBlur(),
//       error    = a.fieldState.error.map(_.message),
//     )
//   }
//
// The field is registered each render so its rules stay current; its value is seeded from
// the form's defaults on first registration.
private val ControllerImpl =
  component[(name: String, control: FormStore, rules: Rules, render: ControllerArgs => VNode)] { p =>
    p.control.registerControlled(p.name, p.rules)

    val value     = useSyncExternalStore(p.control.subscribeValues, () => p.control.getValue(p.name))
    val formState = useSyncExternalStore(p.control.subscribe, () => p.control.getSnapshot)

    val field: ControllerField = (
      name = p.name,
      value = value,
      onChange = (v: Any) => p.control.changeField(p.name, v),
      onBlur = () => p.control.blurField(p.name),
    )
    val fieldState: ControllerFieldState = (
      error = formState.errors.get(p.name),
      isTouched = formState.touchedFields.contains(p.name),
      isDirty = formState.dirtyFields.contains(p.name),
      invalid = formState.errors.contains(p.name),
    )

    p.render((field = field, fieldState = fieldState, formState = formState))
  }

// Curried so the render function reads as a trailing block:
//
//   Controller("email", f.control) { a => … }
def Controller(name: String, control: FormStore, rules: Rules = Rules())(
    render: ControllerArgs => VNode,
): VNode =
  ControllerImpl((name = name, control = control, rules = rules, render = render))

// Subscribe to one field's live value and re-render when it changes — react-hook-form's
// `useWatch`. Unlike reading `form.getValues` (a one-shot, non-reactive snapshot), this
// wakes the calling component on every change to the watched field, while staying isolated
// from changes to *other* fields (the snapshot diff in `useSyncExternalStore` bails out).
// Annotate the result type to type the value:
//
//   val email = useWatch[String](f.control, "email")
def useWatch[T](control: FormStore, field: String)(using Hooks): T =
  useSyncExternalStore(control.subscribeValues, () => control.getValue(field)).asInstanceOf[T]

// Watch the whole form: every value, re-rendering when any of them changes. The snapshot
// is an immutable `Map`, so an unrelated re-render that leaves the values untouched still
// bails out on structural equality.
def useWatchAll(control: FormStore)(using Hooks): Map[String, Any] =
  useSyncExternalStore(control.subscribeValues, () => control.getValues)

// Watch several fields at once, returning their values in the order requested.
def useWatchFields(control: FormStore, fields: Seq[String])(using Hooks): Seq[Any] =
  useSyncExternalStore(control.subscribeValues, () => fields.map(control.getValue))
