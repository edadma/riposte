package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*
import org.scalajs.dom

// The handle a component gets back from `useForm`. It pairs the imperative API
// (`register`, `setValue`, `handleSubmit`, …) with the current `formState` snapshot,
// re-read from the store on each render through `useSyncExternalStore` — so reading
// `form.formState.errors` in the body re-renders exactly when those errors change.
//
// The underlying `control` (the store) is exposed for the pieces that compose on top
// of a form — controlled-component adapters and field arrays — without going through
// the facade.
final class Form private[forms] (val control: FormStore, val formState: FormState):

  // Wire an uncontrolled element into the form. Spread the returned mods onto an
  // `<input>` / `<textarea>` / `<select>`; the field's value then lives in the store,
  // fed from the live DOM, and the element shows its default via the callback ref:
  //
  //   input(typ := "email", form.register("email", Rules(required = true))*)
  //
  // The handler closures are stable across renders (the store caches them per field),
  // so the element's listeners and ref never churn and typing never re-renders.
  def register(field: String, rules: Rules = Rules()): Seq[Mod] =
    val h = control.register(field, rules)
    Seq(
      name    := field,
      ref     := h.ref,
      onInput := h.onInput,
      onBlur  := h.onBlur,
    )

  // Build a submit handler: validate every field, then run `onValid` with the values
  // when the form is clean or `onInvalid` with the errors otherwise. Native form
  // submission is prevented, so attach it to the `<form>`:
  //
  //   form(onSubmit := f.handleSubmit(values => save(values)))
  def handleSubmit(
      onValid:   Map[String, Any] => Unit,
      onInvalid: Map[String, FieldError] => Unit = _ => (),
  ): dom.Event => Unit =
    e =>
      e.preventDefault()
      control.submit(onValid, onInvalid)

  // The whole form's current values; uncontrolled, so this reflects what the user has
  // typed without the form being controlled on every keystroke. This is a one-shot read —
  // for a value that re-renders the component as it changes, use `watch`.
  def getValues: Map[String, Any] = control.getValues

  // Reactively read one field's value: the calling component re-renders when this field
  // changes (and only this field). A hook — call it unconditionally, like any other.
  // Annotate the type at the call site: `val email = f.watch[String]("email")`.
  def watch[T](field: String)(using Hooks): T = useWatch[T](control, field)

  // One field's current value, cast to the type the call site expects — the typed
  // read over the string-path store (`getValue[Int]("age")`).
  def getValue[T](field: String): T = control.getValue(field).asInstanceOf[T]

  // Programmatically set a field, writing the value back into its live element so an
  // uncontrolled input reflects it. `shouldValidate` re-runs the field's rules.
  def setValue(field: String, value: Any, shouldValidate: Boolean = false): Unit =
    control.setValue(field, value, shouldValidate)

  // Attach an error to a field by hand — for server-side validation results that the
  // built-in rules can't know about.
  def setError(field: String, error: FieldError): Unit = control.setError(field, error)

  def clearErrors(field: String): Unit = control.clearErrors(Some(field))

  def clearErrors(): Unit = control.clearErrors(None)

  // Run validation on demand: one field, returning whether it is valid.
  def trigger(field: String): Boolean = control.trigger(Some(field))

  // Run validation across the whole form, returning whether every field is valid.
  def trigger(): Boolean = control.trigger(None)

  // Reset the form to fresh values (or back to its original defaults), clearing errors,
  // touched, and submit state and re-seeding every mounted element.
  def reset(values: Map[String, Any]): Unit = control.reset(Some(values))

  def reset(): Unit = control.reset(None)

// Create a form and observe its state. `defaultValues` seed the fields (and define the
// baseline `reset` returns to and `dirtyFields` is measured against); `mode` chooses
// when fields validate as the user interacts, and `reValidateMode` when they re-validate
// after the first submit (react-hook-form's defaults: validate on submit, then on
// change). The store is created once and survives re-renders; the returned `formState`
// is the live snapshot.
def useForm(
    defaultValues:  Map[String, Any] = Map.empty,
    mode:           ValidationMode   = ValidationMode.OnSubmit,
    reValidateMode: ValidationMode   = ValidationMode.OnChange,
)(using Hooks): Form =
  val store     = useMemo(() => new FormStore(defaultValues, mode, reValidateMode), Array())
  val formState = useSyncExternalStore(store.subscribe, () => store.getSnapshot)
  new Form(store, formState)
