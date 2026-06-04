package io.github.edadma.riposte.forms

import org.scalajs.dom
import scala.collection.mutable

// When a field is validated, mirroring react-hook-form's `mode` / `reValidateMode`.
//   OnSubmit  — only when the form is submitted (the default; typing never validates)
//   OnBlur    — when the field loses focus
//   OnChange  — on every keystroke
//   OnTouched — first on blur, then on every change afterwards
//   All       — on both change and blur
enum ValidationMode:
  case OnSubmit, OnBlur, OnChange, OnTouched, All

// An immutable view of everything a form-state consumer observes, rebuilt only when
// something it depends on changes. `useSyncExternalStore` returns the *same* instance
// while nothing has changed, so a subscribing component bails out of re-rendering —
// the bail-out the store's change-gating exists to enable.
final case class FormState(
    errors:        Map[String, FieldError],
    touchedFields: Set[String],
    dirtyFields:   Set[String],
    isDirty:       Boolean,
    isValid:       Boolean,
    isSubmitting:  Boolean,
    isSubmitted:   Boolean,
    submitCount:   Int,
)

// The stable per-field closures `register` spreads onto an element. They are created
// once per field name and reused across renders, so the element's `ref`/`onInput`/
// `onBlur` props never change identity — the reconciler leaves the live listeners
// in place and the callback ref does not re-fire on every render.
private final class FieldHandlers(
    val ref:     (dom.Element | Null) => Unit,
    val onInput: dom.Event => Unit,
    val onBlur:  dom.Event => Unit,
)

// The reactive core of a form. Field values live here (not in component state), fed
// from the live DOM through callback refs, so the inputs stay uncontrolled and typing
// touches the DOM only — never the component tree. The store validates per the active
// `ValidationMode`, tracks touched/dirty/submit state, and notifies subscribers (via a
// rebuilt `FormState` snapshot) when any of that observable state changes.
final class FormStore(
    initialDefaults: Map[String, Any],
    val mode:           ValidationMode,
    val reValidateMode: ValidationMode,
):
  private var defaults = initialDefaults
  private val values   = mutable.Map.from(initialDefaults)
  private val errors   = mutable.Map.empty[String, FieldError]
  private val touched  = mutable.Set.empty[String]

  private var isSubmitting = false
  private var isSubmitted  = false
  private var submitCount  = 0

  // Per-field registration data, populated by `register` and consumed on validation
  // and imperative write-back. `elements` holds the live DOM node for each mounted
  // field; `rules` the latest rules passed to `register`.
  private val rules    = mutable.Map.empty[String, Rules]
  private val elements = mutable.Map.empty[String, dom.html.Element]
  private val handlers = mutable.Map.empty[String, FieldHandlers]

  // Field-array bookkeeping: per array name, an ordered list of stable row keys. A row
  // addresses its sub-fields by stable key (`items.<rowKey>.name`), never by index, so a
  // remove / move / insert only reorders this list — no stored value has to shift, no
  // registered handler goes stale, and the reconciler's keyed diff preserves each row's
  // live DOM. The opaque keys come from a monotonic counter.
  private val arrays    = mutable.Map.empty[String, mutable.ArrayBuffer[String]]
  private var rowKeySeq = 0

  private val listeners        = mutable.Set.empty[() => Unit]
  private val valueListeners    = mutable.Set.empty[() => Unit]
  private var snapshot         = build()

  // --- subscription (the useSyncExternalStore seam) ------------------------

  def subscribe(cb: () => Unit): () => Unit =
    listeners += cb
    () => listeners -= cb

  def getSnapshot: FormState = snapshot

  // A second, finer subscription: it fires on every change to a field *value*, not
  // just on the observable `FormState`. `useWatch` and `Controller` ride this so a
  // component that reads a field's live value re-renders as the user types — the
  // opt-in reactivity the uncontrolled `formState` seam deliberately withholds.
  // Callers bail out via `useSyncExternalStore`'s snapshot diff, so notifying on
  // every value change (even unrelated fields) is cheap.
  def subscribeValues(cb: () => Unit): () => Unit =
    valueListeners += cb
    () => valueListeners -= cb

  private def notifyValues(): Unit = valueListeners.foreach(_())

  // Rebuild the snapshot and wake subscribers only if it actually changed. A keystroke
  // that moves no observable state — a further edit to an already-dirty field with no
  // error change — rebuilds an equal snapshot and notifies no one, so typing doesn't
  // re-render the form. The first keystroke that flips `isDirty` or an error does.
  private def notifyListeners(): Unit =
    val next = build()
    if next != snapshot then
      snapshot = next
      listeners.foreach(_())

  private def build(): FormState =
    val dirty = values.collect { case (k, v) if !sameAsDefault(k, v) => k }.toSet
    FormState(
      errors = errors.toMap,
      touchedFields = touched.toSet,
      dirtyFields = dirty,
      isDirty = dirty.nonEmpty,
      isValid = errors.isEmpty,
      isSubmitting = isSubmitting,
      isSubmitted = isSubmitted,
      submitCount = submitCount,
    )

  private def sameAsDefault(name: String, value: Any): Boolean =
    defaults.get(name) match
      case Some(d) => d == value
      case None    => value == "" || value == false || value == null

  // --- reading & writing DOM elements --------------------------------------

  // The current value of a field's element: a checkbox reports its `checked` flag, any
  // other input/textarea/select its string `value`.
  private def readElement(el: dom.html.Element): Any =
    el match
      case i: dom.html.Input if i.`type` == "checkbox" => i.checked
      case i: dom.html.Input                           => i.value
      case t: dom.html.TextArea                        => t.value
      case s: dom.html.Select                          => s.value
      case other                                       => other.asInstanceOf[dom.html.Input].value

  // Push a programmatic value back into a field's element — the half of `setValue` /
  // `reset` that an uncontrolled input needs, since the DOM (not state) owns the text.
  private def writeElement(el: dom.html.Element, value: Any): Unit =
    el match
      case i: dom.html.Input if i.`type` == "checkbox" => i.checked = value == true
      case i: dom.html.Input                           => i.value = stringOf(value)
      case t: dom.html.TextArea                        => t.value = stringOf(value)
      case s: dom.html.Select                          => s.value = stringOf(value)
      case other => other.asInstanceOf[dom.html.Input].value = stringOf(value)

  private def stringOf(value: Any): String =
    value match
      case null      => ""
      case s: String => s
      case other     => other.toString

  // --- registration --------------------------------------------------------

  // The string a freshly-mounted field shows, so an uncontrolled input renders its
  // default without being controlled on every render.
  def defaultString(name: String): String = stringOf(defaults.getOrElse(name, ""))

  def defaultBoolean(name: String): Boolean = defaults.get(name).contains(true)

  // Record (or refresh) a field's rules and hand back its stable handler closures,
  // creating them on first registration. Re-registering with new rules updates the
  // rules in place but keeps the same closures, so the element's props stay stable.
  def register(name: String, fieldRules: Rules): FieldHandlers =
    rules(name) = fieldRules
    if !values.contains(name) then values(name) = defaults.getOrElse(name, "")
    handlers.getOrElseUpdate(name, makeHandlers(name))

  // Register a controlled field — one whose value the store owns directly rather than
  // reading from a DOM element through a ref. `Controller` calls this each render to keep
  // the field's rules current and seed its value once; updates then flow through
  // `changeField` / `blurField` instead of the uncontrolled `onInput` / `onBlur`.
  def registerControlled(name: String, fieldRules: Rules): Unit =
    rules(name) = fieldRules
    if !values.contains(name) then values(name) = defaults.getOrElse(name, "")

  // The controlled counterpart of `onInput`: a `Controller`-wrapped component reports a
  // new value here. Validation follows the same per-mode policy as an uncontrolled field,
  // and both subscriptions fire so a `useWatch` and the `formState` consumers update.
  def changeField(name: String, value: Any): Unit =
    values(name) = value
    if shouldValidateOnChange(name) then revalidateField(name)
    notifyValues()
    notifyListeners()

  // The controlled counterpart of `onBlur`: mark the field touched and validate it if the
  // mode calls for it.
  def blurField(name: String): Unit =
    val wasTouched = touched(name)
    touched += name
    val validating = shouldValidateOnBlur(name)
    if validating then revalidateField(name)
    if validating || !wasTouched then notifyListeners()

  private def makeHandlers(name: String): FieldHandlers =
    val ref = (el: dom.Element | Null) =>
      el match
        case e: dom.html.Element =>
          elements(name) = e
          // Seed the live node from the stored value so a remount (or a value set
          // before mount) is reflected without making the field controlled.
          values.get(name).foreach(writeElement(e, _))
        case _ =>
          elements -= name
          ()

    val onInput = (_: dom.Event) =>
      elements.get(name).foreach(e => values(name) = readElement(e))
      if shouldValidateOnChange(name) then revalidateField(name)
      // A keystroke can flip `isDirty`/`dirtyFields` (and any error it just cleared)
      // even when we didn't validate, so rebuild the snapshot either way — the diff is
      // cheap and uncontrolled inputs don't re-render from it. `notifyValues` separately
      // wakes any `useWatch` on this field.
      notifyValues()
      notifyListeners()

    val onBlur = (_: dom.Event) =>
      val wasTouched = touched(name)
      touched += name
      elements.get(name).foreach(e => values(name) = readElement(e))
      val validating = shouldValidateOnBlur(name)
      if validating then revalidateField(name)
      if validating || !wasTouched then notifyListeners()

    FieldHandlers(ref, onInput, onBlur)

  // Whether a field validates on a value change, given the form's mode (and, after the
  // first submit, its reValidateMode). Shared by the uncontrolled `onInput` handler and
  // the controlled `changeField`, so both honour the same policy.
  private def shouldValidateOnChange(name: String): Boolean =
    mode == ValidationMode.OnChange || mode == ValidationMode.All ||
      (mode == ValidationMode.OnTouched && touched(name)) ||
      (isSubmitted && (reValidateMode == ValidationMode.OnChange || reValidateMode == ValidationMode.All))

  // Whether a field validates when it loses focus, under the same shared policy.
  private def shouldValidateOnBlur(name: String): Boolean =
    mode == ValidationMode.OnBlur || mode == ValidationMode.OnTouched ||
      mode == ValidationMode.All ||
      (isSubmitted && reValidateMode == ValidationMode.OnBlur)

  // Validate one field against its rules, updating its error entry. Returns whether the
  // entry changed, so a caller can decide whether the change is worth notifying for.
  private def revalidateField(name: String): Boolean =
    val result = validateValue(values.getOrElse(name, ""), rules.getOrElse(name, Rules()))
    val before = errors.get(name)
    result match
      case Some(err) => errors(name) = err
      case None      => errors -= name
    before != result

  // --- imperative API ------------------------------------------------------

  def getValue(name: String): Any = values.getOrElse(name, "")

  def getValues: Map[String, Any] = values.toMap

  def setValue(name: String, value: Any, shouldValidate: Boolean): Unit =
    values(name) = value
    elements.get(name).foreach(writeElement(_, value))
    if shouldValidate then revalidateField(name)
    notifyValues()
    notifyListeners()

  def setError(name: String, error: FieldError): Unit =
    errors(name) = error
    notifyListeners()

  def clearErrors(name: Option[String]): Unit =
    name match
      case Some(n) => errors -= n
      case None    => errors.clear()
    notifyListeners()

  // Validate the named field, or the whole form when no name is given; returns whether
  // the validated scope is error-free.
  def trigger(name: Option[String]): Boolean =
    name match
      case Some(n) => revalidateField(n)
      case None    => rules.keys.foreach(revalidateField)
    notifyListeners()
    name match
      case Some(n) => !errors.contains(n)
      case None    => errors.isEmpty

  // Reset to the given values (or back to the original defaults), clearing errors,
  // touched, and submit state and writing every mounted element back to its value.
  def reset(newValues: Option[Map[String, Any]]): Unit =
    newValues.foreach(v => defaults = v)
    values.clear()
    values ++= defaults
    errors.clear()
    touched.clear()
    isSubmitting = false
    isSubmitted = false
    submitCount = 0
    elements.foreach((name, el) => values.get(name).foreach(writeElement(el, _)))
    notifyValues()
    notifyListeners()

  // Validate every registered field and run `onValid` with the values when the form is
  // clean, or `onInvalid` with the errors otherwise. `submitCount` and `isSubmitted`
  // advance regardless, switching subsequent validation to `reValidateMode`.
  def submit(onValid: Map[String, Any] => Unit, onInvalid: Map[String, FieldError] => Unit): Unit =
    isSubmitting = true
    submitCount += 1
    notifyListeners()

    elements.foreach((name, el) => values(name) = readElement(el))
    rules.keys.foreach(revalidateField)

    isSubmitting = false
    isSubmitted = true
    notifyListeners()

    if errors.isEmpty then onValid(values.toMap)
    else onInvalid(errors.toMap)

  // --- field arrays --------------------------------------------------------

  private def nextRowKey(): String =
    rowKeySeq += 1
    s"r$rowKeySeq"

  private def arrayBuf(name: String): mutable.ArrayBuffer[String] =
    arrays.getOrElseUpdate(name, mutable.ArrayBuffer.empty[String])

  // Write a row's seed values into the store under its stable-key paths.
  private def seedRow(name: String, key: String, row: Map[String, Any]): Unit =
    row.foreach((sub, v) => values(s"$name.$key.$sub") = v)

  // Drop everything belonging to one row — values, errors, touched, rules, the live
  // element, and the cached handlers — so a removed row leaves nothing behind in the
  // store or in `getValues` / submit.
  private def removeRowEntries(name: String, key: String): Unit =
    val prefix = s"$name.$key."
    values.filterInPlace((k, _) => !k.startsWith(prefix))
    errors.filterInPlace((k, _) => !k.startsWith(prefix))
    touched.filterInPlace(!_.startsWith(prefix))
    rules.filterInPlace((k, _) => !k.startsWith(prefix))
    elements.filterInPlace((k, _) => !k.startsWith(prefix))
    handlers.filterInPlace((k, _) => !k.startsWith(prefix))

  private def afterArrayChange(): Unit =
    notifyValues()
    notifyListeners()

  // The ordered row keys of an array — what `useFieldArray` turns into keyed rows.
  def fieldArrayKeys(name: String): Seq[String] =
    arrays.get(name).map(_.toSeq).getOrElse(Seq.empty)

  // Create an array once, seeding it from `initial`. Idempotent across re-renders and
  // remounts: an array that already exists is left exactly as the user has edited it.
  def ensureFieldArray(name: String, initial: Seq[Map[String, Any]]): Unit =
    if !arrays.contains(name) then
      val buf = mutable.ArrayBuffer.empty[String]
      initial.foreach { row =>
        val k = nextRowKey()
        buf += k
        seedRow(name, k, row)
      }
      arrays(name) = buf
      afterArrayChange()

  def arrayAppend(name: String, row: Map[String, Any]): Unit =
    val k = nextRowKey()
    arrayBuf(name) += k
    seedRow(name, k, row)
    afterArrayChange()

  def arrayPrepend(name: String, row: Map[String, Any]): Unit =
    val k = nextRowKey()
    arrayBuf(name).prepend(k)
    seedRow(name, k, row)
    afterArrayChange()

  def arrayInsert(name: String, index: Int, row: Map[String, Any]): Unit =
    val buf = arrayBuf(name)
    val at  = index.max(0).min(buf.length)
    val k   = nextRowKey()
    buf.insert(at, k)
    seedRow(name, k, row)
    afterArrayChange()

  def arrayRemove(name: String, index: Int): Unit =
    val buf = arrayBuf(name)
    if index >= 0 && index < buf.length then
      val k = buf.remove(index)
      removeRowEntries(name, k)
      afterArrayChange()

  def arrayMove(name: String, from: Int, to: Int): Unit =
    val buf = arrayBuf(name)
    if from >= 0 && from < buf.length && to >= 0 && to < buf.length then
      val k = buf.remove(from)
      buf.insert(to, k)
      afterArrayChange()

  def arraySwap(name: String, a: Int, b: Int): Unit =
    val buf = arrayBuf(name)
    if a >= 0 && a < buf.length && b >= 0 && b < buf.length then
      val tmp = buf(a)
      buf(a) = buf(b)
      buf(b) = tmp
      afterArrayChange()

  // Replace the whole array with fresh rows, discarding every old row's entries.
  def arrayReplace(name: String, rows: Seq[Map[String, Any]]): Unit =
    val buf = arrayBuf(name)
    buf.toList.foreach(k => removeRowEntries(name, k))
    buf.clear()
    rows.foreach { row =>
      val k = nextRowKey()
      buf += k
      seedRow(name, k, row)
    }
    afterArrayChange()

  // The array's rows as ordered maps of sub-field → value, reconstructed from the flat
  // store at read time — the ordered view the opaque stable keys don't give directly.
  def fieldArrayValues(name: String): Seq[Map[String, Any]] =
    fieldArrayKeys(name).map { key =>
      val rowPrefix = s"$name.$key."
      values.collect {
        case (k, v) if k.startsWith(rowPrefix) => k.substring(rowPrefix.length) -> v
      }.toMap
    }
