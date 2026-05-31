package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// Component-specific helpers shared across salle's controls. Generic, app-level hooks
// (useClickOutside, useIntersectionObserver, …) live in the riposte core, not here.

/** Resolve a value that may be *controlled* by the caller or owned internally — the
  * pattern every interactive salle component shares. When `controlled` is `Some`,
  * that value is authoritative and the returned setter only reports changes through
  * `onChange`; when `None`, an internal `useState` (seeded from `default`) holds the
  * value and the setter updates it *and* reports. Returns the current value and the
  * setter. Mirrors AsterUI's `current = controlled ?? internal` idiom.
  */
def useControllable[T](controlled: Option[T], default: T, onChange: T => Unit)(using
    Hooks,
): (T, T => Unit) =
  val (internal, setInternal, _) = useState(default)
  val current                    = controlled.getOrElse(internal)
  val set: T => Unit = next =>
    if controlled.isEmpty then setInternal(next)
    onChange(next)
  (current, set)

/** The `data-state` value for a checkable control ([[Checkbox]], [[Toggle]]). */
private[salle] def stateOf(disabled: Boolean, checked: Boolean): String =
  if disabled then "disabled" else if checked then "checked" else "unchecked"
