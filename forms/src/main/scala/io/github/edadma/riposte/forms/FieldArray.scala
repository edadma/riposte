package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*

// One row of a field array, as `useFieldArray` exposes it: a stable `id` (use it as the
// reconciler `key` *and* in the registered field paths) and the row's current `index`.
type FieldArrayItem = (id: String, index: Int)

// A handle over a dynamic list of field groups — react-hook-form's `useFieldArray`. The
// store owns the rows; this just names the array and forwards the mutating operations.
//
// Unlike react-hook-form, a row addresses its sub-fields by the row's stable `id`, not by
// its index — `register(s"$name.${row.id}.field")`. This is what makes remove / move /
// insert robust over the flat string-path store: reordering rows never reshuffles stored
// values or invalidates a registered field, and keying each row by `row.id` lets the
// reconciler keep its live DOM across reorders.
//
//   val fa = useFieldArray(f.control, "guests")
//   div(
//     fa.fields.map { row =>
//       div(key := row.id,
//         input(f.register(s"guests.${row.id}.name")),
//         button(typ := "button", onClick := (_ => fa.remove(row.index)), "remove"),
//       )
//     },
//     button(typ := "button", onClick := (_ => fa.append(Map("name" -> ""))), "add"),
//   )
final class FieldArray private[forms] (
    val name:    String,
    val fields:  Seq[FieldArrayItem],
    val control: FormStore,
):
  // Add a row at the end, optionally seeding its sub-fields from `values`
  // (keys are sub-field names: `Map("name" -> "Ada", "vip" -> true)`).
  def append(values: Map[String, Any] = Map.empty): Unit = control.arrayAppend(name, values)

  // Add a row at the front.
  def prepend(values: Map[String, Any] = Map.empty): Unit = control.arrayPrepend(name, values)

  // Insert a row at `index` (clamped to the current bounds).
  def insert(index: Int, values: Map[String, Any] = Map.empty): Unit = control.arrayInsert(name, index, values)

  // Remove the row at `index`, discarding its stored values, errors, and touched state.
  def remove(index: Int): Unit = control.arrayRemove(name, index)

  // Move the row at `from` to `to`, preserving every row's values (stable keys).
  def move(from: Int, to: Int): Unit = control.arrayMove(name, from, to)

  // Swap two rows.
  def swap(a: Int, b: Int): Unit = control.arraySwap(name, a, b)

  // Replace the whole array with fresh rows.
  def replace(rows: Seq[Map[String, Any]]): Unit = control.arrayReplace(name, rows)

  // The rows as ordered maps of sub-field → value, read live from the store — the ordered
  // view to consume at submit time, since `getValues` returns opaque stable-key paths.
  def values: Seq[Map[String, Any]] = control.fieldArrayValues(name)

// Manage a dynamic list of field groups. `initial` seeds the array on first mount (once;
// later edits survive re-renders and remounts). The returned handle's `fields` re-renders
// the calling component whenever rows are added, removed, or reordered.
def useFieldArray(
    control: FormStore,
    name:    String,
    initial: Seq[Map[String, Any]] = Nil,
)(using Hooks): FieldArray =
  useMemo(() => { control.ensureFieldArray(name, initial); () }, Array())
  val keys  = useSyncExternalStore(control.subscribeValues, () => control.fieldArrayKeys(name))
  val items = keys.zipWithIndex.map((k, i) => (id = k, index = i))
  new FieldArray(name, items, control)
