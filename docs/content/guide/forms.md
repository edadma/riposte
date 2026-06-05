---
title: "Forms"
weight: 6
---

**riposte-forms** is a form layer in the shape of [react-hook-form]: fields are
*uncontrolled* — the DOM owns the text and the form reads it through a ref — so typing never
re-renders the component tree. Only the parts that read form state (errors, validity) wake
up, and only when their slice changes. It's a separate artifact built on the core's public
API, usable on its own or under [salle](/salle/).

[react-hook-form]: https://react-hook-form.com/

Add the dependency (it pulls in the core transitively):

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-forms" % "0.2.1"

import io.github.edadma.riposte.forms.*
```

## A form

`useForm` returns a handle. `register` wires an uncontrolled element by spreading mods onto
it (see [spreading a bundle of mods](/guide/components/#spreading-a-bundle-of-mods));
`handleSubmit` validates every field then runs your callback with the values:

```scala
val Signup = view {
  val f = useForm(defaultValues = Map("email" -> ""))

  form(onSubmit := f.handleSubmit(values => save(values)))(
    input(typ := "email", f.register("email", Rules(required = true))*),
    f.formState.errors.get("email").map(e => span(cls := "error", e.message)),
    button(typ := "submit", "Sign up"),
  )
}
```

`f.formState` is the live snapshot — read `errors`, `isDirty`, `isValid`, `isValidating`,
`isSubmitting`, `isSubmitted`, `submitCount`, `touchedFields`, `dirtyFields` in the body and
the component re-renders exactly when that slice changes.

For a submit that awaits — a server POST — use `handleSubmitAsync(values => api.save(values))`
where the handler returns a `Future[Unit]`; `formState.isSubmitting` stays true across both
validation and the handler, so a spinner or disabled button bound to it covers the whole
round-trip.

## Validation rules

`Rules` mirrors react-hook-form's per-field rule object — each rule is opt-in and the first
failure wins:

```scala
f.register("password", Rules(
  required  = true,
  minLength = Some(8),
  pattern   = Some("""\d""".r),
  validate  = Seq(v => Option.when(v == "password")("Too obvious")),
  messages  = Messages(minLength = "Use at least %s characters"),
))
```

`required`, `minLength`/`maxLength` (string length), `min`/`max` (numeric), `pattern` (a
`Regex`), and `validate` (custom predicates returning `Some(message)` to reject). A failure
is a `FieldError(kind, message)` — `kind` names the rule that rejected so a UI can branch on
it. `Messages` overrides the default text; `%s` is filled with the rule's bound.

**When** fields validate is set by `mode` (before the first submit) and `reValidateMode`
(after it), each a `ValidationMode`: `OnSubmit` (default), `OnBlur`, `OnChange`, `OnTouched`,
or `All`.

## Schema validation

To drive validation from a whole-form schema (zod/yup-style, or a hand-written check)
instead of per-field `Rules`, pass a `resolver` — it takes the current values and returns the
errors keyed by field (an empty map means valid):

```scala
val f = useForm(resolver = Some { values =>
  if values.getOrElse("email", "") == "" then Map("email" -> FieldError("schema", "Required"))
  else Map.empty
})
```

`asyncResolver` is the awaiting counterpart (a server check, a uniqueness query) — while it
runs, `formState.isValidating` is true. Configure one or the other, not both.

## The imperative API

The handle also exposes:

- `setValue(field, value, shouldValidate)` / `getValue[T](field)` / `getValues` — write and
  read field values (writing reflects back into the live element).
- `trigger(field)` / `trigger()` — run validation on demand, returning validity;
  `triggerAsync(field)` / `triggerAsync()` return a `Future[Boolean]` for an async resolver.
- `setError(field, err)` / `clearErrors(field)` / `clearErrors()` — drive errors by hand,
  e.g. from server-side validation.
- `reset(values)` / `reset()` — reset to fresh values (or the original defaults), clearing
  errors/touched/submit state.

## Watching values

`f.getValues` is a one-shot, non-reactive read. To re-render as a value changes, watch it —
the calling component wakes only for the field(s) it watches:

```scala
val agreed = f.watch[Boolean]("agree")        // one field, on the handle
val email  = useWatch[String](f.control, "email")
val all    = useWatchAll(f.control)            // every value
val some   = useWatchFields(f.control, Seq("first", "last"))
```

## Controlled components

`register` wires *uncontrolled* DOM elements. For a component that takes a `value` and emits
changes through a callback — salle's `Input`, `Select`, `Checkbox` — use `Controller`, which
subscribes to the field's value and error and hands them to your render function:

```scala
Controller("country", f.control, Rules(required = true)) { a =>
  Select(
    value    = a.field.value.asInstanceOf[String],
    onChange = v => a.field.onChange(v),
    onBlur   = _ => a.field.onBlur(),
    invalid  = a.fieldState.invalid,
  )
}
```

The render function receives `field` (`name`/`value`/`onChange`/`onBlur`), `fieldState`
(`error`/`isTouched`/`isDirty`/`invalid`), and the whole `formState`. The store owns the
value, so it stays live and the `Controller` re-renders as it changes.

## Field arrays

`useFieldArray` manages a dynamic list of repeating field groups — guests on an invite, line
items on an order. The handle's `fields` re-renders when rows are added, removed, or
reordered; each row carries a **stable `id`** you use both as the reconciler `key` and inside
the registered field paths:

```scala
val fa = useFieldArray(f.control, "guests", initial = Seq(Map("name" -> "")))

div(
  fa.fields.map { row =>
    div(key := row.id)(
      input(f.register(s"guests.${row.id}.name")*),
      button(typ := "button", onClick := (_ => fa.remove(row.index)), "Remove"),
    )
  },
  button(typ := "button", onClick := (_ => fa.append(Map("name" -> ""))), "Add guest"),
)
```

Addressing a row by its `id` rather than its index is what keeps remove/move/insert robust
over the flat string-path store: reordering never reshuffles stored values or invalidates a
registered field. The handle exposes `append`/`prepend`/`insert`/`remove`/`move`/`swap`/
`replace`, and `values` — the rows as ordered maps of sub-field → value, the view to read at
submit time.
