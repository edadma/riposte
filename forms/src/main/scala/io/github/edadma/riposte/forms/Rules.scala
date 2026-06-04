package io.github.edadma.riposte.forms

import scala.util.matching.Regex

// Validation for a single field. Each rule is opt-in and carries its own message,
// mirroring react-hook-form's `register(name, rules)` object. The rules are checked
// in a fixed order and the *first* failure wins — so a blank required field reports
// "required" rather than also complaining about a pattern it can't match yet.
//
//   register("email", Rules(required = true, pattern = Some("""^.+@.+$""".r)))
//
// `validate` holds custom predicates: each returns `Some(message)` to reject the
// value or `None` to accept it. Multiple validators run in order; the first that
// rejects wins. The value handed to a validator is whatever the field currently
// holds (a `String` for text inputs, a `Boolean` for checkboxes).
final case class Rules(
    required:  Boolean         = false,
    minLength: Option[Int]     = None,
    maxLength: Option[Int]     = None,
    min:       Option[Double]  = None,
    max:       Option[Double]  = None,
    pattern:   Option[Regex]   = None,
    validate:  Seq[Validator]  = Nil,
    messages:  Messages        = Messages(),
):
  def isEmpty: Boolean =
    !required && minLength.isEmpty && maxLength.isEmpty &&
      min.isEmpty && max.isEmpty && pattern.isEmpty && validate.isEmpty

// A custom validator: `Some(message)` rejects, `None` accepts.
type Validator = Any => Option[String]

// The error attached to a field that failed validation. `kind` names the rule that
// rejected the value ("required", "minLength", "pattern", "validate", …) so a UI can
// branch on it; `message` is the human-readable text to show.
final case class FieldError(kind: String, message: String)

// Default messages for the built-in rules, overridable per field via `Rules.messages`.
// `%s` (where present) is filled with the rule's bound — e.g. the minLength count —
// so a custom message can still surface the limit.
final case class Messages(
    required:  String = "This field is required",
    minLength: String = "Must be at least %s characters",
    maxLength: String = "Must be at most %s characters",
    min:       String = "Must be at least %s",
    max:       String = "Must be at most %s",
    pattern:   String = "Invalid format",
)

// Run a field's rules against its current value, returning the first failure (if any).
// A value is "empty" — and so fails `required` but passes every other rule — when it
// is null, the empty string, or a `false` checkbox; this matches react-hook-form,
// where an absent optional field is simply valid.
def validateValue(value: Any, rules: Rules): Option[FieldError] =
  val isEmpty = value match
    case null      => true
    case s: String => s.isEmpty
    case false     => true
    case _         => false

  if rules.required && isEmpty then Some(FieldError("required", rules.messages.required))
  else if isEmpty then None
  else
    val str = value match
      case s: String => s
      case other     => other.toString
    val num = str.toDoubleOption

    def fmt(template: String, bound: Any): String = template.replace("%s", bound.toString)

    val checks: Seq[Option[FieldError]] = Seq(
      rules.minLength.filter(str.length < _).map(n => FieldError("minLength", fmt(rules.messages.minLength, n))),
      rules.maxLength.filter(str.length > _).map(n => FieldError("maxLength", fmt(rules.messages.maxLength, n))),
      rules.min.filter(m => num.exists(_ < m)).map(m => FieldError("min", fmt(rules.messages.min, m))),
      rules.max.filter(m => num.exists(_ > m)).map(m => FieldError("max", fmt(rules.messages.max, m))),
      rules.pattern.filter(p => p.findFirstIn(str).isEmpty).map(_ => FieldError("pattern", rules.messages.pattern)),
    )

    checks.flatten.headOption.orElse(
      rules.validate.iterator.flatMap(v => v(value).map(msg => FieldError("validate", msg))).nextOption(),
    )
