package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// `Controller` wires a *controlled* component (one driven by a `value` prop and an
// `onChange` callback, like salle's inputs) into a form, and `useWatch` reactively reads a
// field's live value. Both ride the store's value subscription, so the assertions here turn
// on a component actually re-rendering as the underlying value changes.
class ControllerSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  private def fireInput(el: dom.Element, value: String): Unit =
    el.asInstanceOf[dom.html.Input].value = value
    el.dispatchEvent(new dom.Event("input"))
    Scheduler.flushSync()

  private def fireBlur(el: dom.Element): Unit =
    el.dispatchEvent(new dom.Event("blur"))
    Scheduler.flushSync()

  private def fireSubmit(formEl: dom.Element): Unit =
    formEl.dispatchEvent(new dom.Event("submit", new dom.EventInit { cancelable = true; bubbles = true }))
    Scheduler.flushSync()

  // A minimal controlled <input>: it shows whatever value it is handed and reports edits
  // through the callback — the contract `Controller` expects of the component it wraps.
  private def controlledInput(a: ControllerArgs, klass: String): VNode =
    input(
      cls      := klass,
      value    := a.field.value.asInstanceOf[String],
      onInput  := (e => a.field.onChange(e.target.asInstanceOf[dom.html.Input].value)),
      onBlur   := (_ => a.field.onBlur()),
    )

  // --- Controller ----------------------------------------------------------

  test("a controlled field shows its default value"):
    val c = host()
    val F = view {
      val f = useForm(Map("email" -> "seed@x.com"))
      Controller("email", f.control)(a => controlledInput(a, "email"))
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(c.querySelector("input.email").asInstanceOf[dom.html.Input].value == "seed@x.com")

  test("onChange updates the form's value and the controlled view reflects it"):
    val c          = host()
    var form: Form = null
    val F = view {
      val f = useForm(Map("email" -> ""))
      form = f
      Controller("email", f.control)(a => controlledInput(a, "email"))
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "x@y.com")
    assert(form.getValue[String]("email") == "x@y.com")
    assert(c.querySelector("input.email").asInstanceOf[dom.html.Input].value == "x@y.com")

  test("onChange mode surfaces and clears a controlled field's error as you type"):
    val c = host()
    val F = view {
      val f = useForm(Map("email" -> ""), mode = ValidationMode.OnChange)
      Controller("email", f.control, Rules(required = true, pattern = Some("""^.+@.+$""".r))) { a =>
        div(
          controlledInput(a, "email"),
          span(cls := "err", a.fieldState.error.map(_.message).getOrElse("")),
        )
      }
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "nope")
    assert(c.querySelector("span.err").textContent.nonEmpty)
    fireInput(c.querySelector("input.email"), "a@b.com")
    assert(c.querySelector("span.err").textContent.isEmpty)

  test("a controlled field is marked touched on blur"):
    val c          = host()
    var form: Form = null
    val F = view {
      val f = useForm(Map("name" -> ""))
      form = f
      Controller("name", f.control)(a => controlledInput(a, "name"))
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(!form.formState.touchedFields.contains("name"))
    fireBlur(c.querySelector("input.name"))
    assert(form.formState.touchedFields.contains("name"))

  test("fieldState.isDirty tracks a controlled field's divergence from its default"):
    val c          = host()
    var seen       = false
    val F = view {
      val f = useForm(Map("name" -> "Ada"))
      Controller("name", f.control) { a =>
        if a.fieldState.isDirty then seen = true
        controlledInput(a, "name")
      }
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(!seen)
    fireInput(c.querySelector("input.name"), "Grace")
    assert(seen)

  test("a controlled field's value reaches the submit handler"):
    val c                     = host()
    var got: Map[String, Any] = null
    val F = view {
      val f = useForm(Map("email" -> ""))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(v => got = v),
        Controller("email", f.control, Rules(required = true))(a => controlledInput(a, "email")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "hi@there.com")
    fireSubmit(c.querySelector("form.f"))
    assert(got("email") == "hi@there.com")

  test("a blank required controlled field blocks submit and reports its error"):
    val c        = host()
    var validRan = false
    val F = view {
      val f = useForm(Map("email" -> ""))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(_ => validRan = true),
        Controller("email", f.control, Rules(required = true)) { a =>
          div(
            controlledInput(a, "email"),
            span(cls := "err", a.fieldState.error.map(_.message).getOrElse("")),
          )
        },
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(!validRan)
    assert(c.querySelector("span.err").textContent.nonEmpty)

  // --- useWatch ------------------------------------------------------------

  test("watch re-renders the component with a registered field's live value"):
    val c = host()
    val F = view {
      val f       = useForm(Map("name" -> ""))
      val watched = f.watch[String]("name")
      div(
        input(cls := "name", f.register("name")),
        span(cls := "echo", watched),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.echo").textContent.isEmpty)
    fireInput(c.querySelector("input.name"), "hello")
    assert(c.querySelector("span.echo").textContent == "hello")

  test("useWatchAll exposes every value and updates as they change"):
    val c = host()
    val F = view {
      val f   = useForm(Map("a" -> "", "b" -> ""))
      val all = useWatchAll(f.control)
      div(
        input(cls := "a", f.register("a")),
        input(cls := "b", f.register("b")),
        span(cls := "out", s"${all.getOrElse("a", "")}|${all.getOrElse("b", "")}"),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(c.querySelector("span.out").textContent == "|")
    fireInput(c.querySelector("input.a"), "x")
    assert(c.querySelector("span.out").textContent == "x|")
    fireInput(c.querySelector("input.b"), "y")
    assert(c.querySelector("span.out").textContent == "x|y")

  test("watch reflects a programmatic setValue"):
    val c          = host()
    var form: Form = null
    val F = view {
      val f       = useForm(Map("name" -> ""))
      form = f
      val watched = f.watch[String]("name")
      div(input(cls := "name", f.register("name")), span(cls := "echo", watched))
    }
    render(F(), c)
    Scheduler.flushSync()
    form.setValue("name", "set!")
    Scheduler.flushSync()
    assert(c.querySelector("span.echo").textContent == "set!")
