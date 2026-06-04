package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// `useFieldArray` manages a dynamic, reorderable list of field groups. Because rows are
// keyed by a stable id (not index), the assertions here turn on two things at once: the
// store keeping each row's value across an add / remove / reorder, and the reconciler
// preserving the matching uncontrolled <input> by key so its typed text rides along.
class FieldArraySpec extends AnyFunSuite:

  private def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  private def fireInput(el: dom.Element, value: String): Unit =
    el.asInstanceOf[dom.html.Input].value = value
    el.dispatchEvent(new dom.Event("input"))
    Scheduler.flushSync()

  private def fireSubmit(formEl: dom.Element): Unit =
    formEl.dispatchEvent(new dom.Event("submit", new dom.EventInit { cancelable = true; bubbles = true }))
    Scheduler.flushSync()

  private def guests(c: dom.Element): Seq[dom.html.Input] =
    val nl = c.querySelectorAll("input.guest")
    (0 until nl.length).map(i => nl(i).asInstanceOf[dom.html.Input])

  // A form with a single text field per row, registered at the row's stable-key path.
  private def arrayForm(c: dom.Element, initial: Seq[Map[String, Any]])(using
      capture: (Form, FieldArray) => Unit,
  ): Unit =
    val F = view {
      val f   = useForm()
      val arr = useFieldArray(f.control, "guests", initial = initial)
      capture(f, arr)
      div(
        arr.fields.map { row =>
          input(key := row.id, cls := "guest", f.register(s"guests.${row.id}.name")): VNode
        }: Seq[VNode],
      )
    }
    render(F(), c)
    Scheduler.flushSync()

  // --- seeding & basic shape ----------------------------------------------

  test("initial rows seed their inputs and report the right indices"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    val gs = guests(c)
    assert(gs.length == 2)
    assert(gs(0).value == "Ada")
    assert(gs(1).value == "Grace")
    assert(fa.fields.map(_.index) == Seq(0, 1))

  test("typing into a row updates that row's value in the store"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada")))
    fireInput(guests(c).head, "Lin")
    assert(fa.values.head("name") == "Lin")

  // --- mutating operations -------------------------------------------------

  test("append adds a row at the end, seeded with its defaults"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada")))
    fa.append(Map("name" -> "Hopper"))
    Scheduler.flushSync()
    val gs = guests(c)
    assert(gs.length == 2)
    assert(gs(1).value == "Hopper")

  test("prepend adds a row at the front"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada")))
    fa.prepend(Map("name" -> "Zero"))
    Scheduler.flushSync()
    val gs = guests(c)
    assert(gs.length == 2)
    assert(gs(0).value == "Zero")
    assert(gs(1).value == "Ada")

  test("insert places a row at the given index"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    fa.insert(1, Map("name" -> "Mid"))
    Scheduler.flushSync()
    assert(guests(c).map(_.value) == Seq("Ada", "Mid", "Grace"))

  test("remove deletes a row and clears its stored value, keeping the rest"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace"), Map("name" -> "Lin")))
    fa.remove(1)
    Scheduler.flushSync()
    assert(guests(c).map(_.value) == Seq("Ada", "Lin"))
    assert(fa.values.map(_("name")) == Seq("Ada", "Lin"))

  test("a user-edited value survives the removal of a different row (stable keys)"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    fireInput(guests(c)(1), "Grace Hopper") // edit the second row
    fa.remove(0)                            // remove the first
    Scheduler.flushSync()
    val gs = guests(c)
    assert(gs.length == 1)
    assert(gs(0).value == "Grace Hopper")

  test("move reorders rows while preserving each row's value"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    fa.move(0, 1)
    Scheduler.flushSync()
    assert(guests(c).map(_.value) == Seq("Grace", "Ada"))
    assert(fa.values.map(_("name")) == Seq("Grace", "Ada"))

  test("swap exchanges two rows"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace"), Map("name" -> "Lin")))
    fa.swap(0, 2)
    Scheduler.flushSync()
    assert(guests(c).map(_.value) == Seq("Lin", "Grace", "Ada"))

  test("replace swaps in a whole new set of rows"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    fa.replace(Seq(Map("name" -> "Only")))
    Scheduler.flushSync()
    val gs = guests(c)
    assert(gs.length == 1)
    assert(gs(0).value == "Only")

  // --- validation & submit -------------------------------------------------

  test("per-row required validation blocks submit when a row is blank"):
    val c                = host()
    var validRan         = false
    var formEl: dom.Element = null
    val F = view {
      val f   = useForm()
      val arr = useFieldArray(f.control, "guests", initial = Seq(Map("name" -> "")))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(_ => validRan = true),
        arr.fields.map { row =>
          input(key := row.id, cls := "guest", f.register(s"guests.${row.id}.name", Rules(required = true))): VNode
        }: Seq[VNode],
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    formEl = c.querySelector("form.f")
    fireSubmit(formEl)
    assert(!validRan)
    fireInput(guests(c).head, "Ada")
    fireSubmit(formEl)
    assert(validRan)

  test("fa.values gives the ordered rows for a submit handler to consume"):
    val c              = host()
    var fa: FieldArray = null
    given ((Form, FieldArray) => Unit) = (_, a) => fa = a
    arrayForm(c, Seq(Map("name" -> "Ada"), Map("name" -> "Grace")))
    fa.append(Map("name" -> "Lin"))
    Scheduler.flushSync()
    assert(fa.values.map(_("name")) == Seq("Ada", "Grace", "Lin"))
