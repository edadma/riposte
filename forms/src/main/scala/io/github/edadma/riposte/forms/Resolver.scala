package io.github.edadma.riposte.forms

import scala.concurrent.Future

// A schema-validation seam: given the form's current values, return the errors keyed by
// field name (an empty map means valid). A resolver *replaces* the per-field `Rules` — it
// is how an external schema validator (zod / yup / a hand-written check) drives the form
// instead of the built-in rules. react-hook-form's `resolver`, in its common shape.
//
//   useForm(resolver = Some { values =>
//     if values.getOrElse("email", "") == "" then Map("email" -> FieldError("schema", "required"))
//     else Map.empty
//   })
type Resolver = Map[String, Any] => Map[String, FieldError]

// The asynchronous counterpart: validation that has to await something (a server check, a
// uniqueness query). While it is in flight the form's `isValidating` is true. Configure
// either a sync `resolver` or an `asyncResolver`, not both.
type AsyncResolver = Map[String, Any] => Future[Map[String, FieldError]]
