package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// A react-hook-form-style form layer over riposte. Three layers under test: the pure
// validators (`validateValue`), the store (values fed from the DOM, validation per
// mode, touched/dirty/submit state, change-gated notification), and the `useForm`
// hook driving real uncontrolled inputs in jsdom.
class FormSpec extends AnyFunSuite:

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

  // --- validators ----------------------------------------------------------

  test("required rejects an empty value and accepts a present one"):
    assert(validateValue("", Rules(required = true)).exists(_.kind == "required"))
    assert(validateValue("x", Rules(required = true)).isEmpty)

  test("an empty optional field is valid and skips the other rules"):
    assert(validateValue("", Rules(minLength = Some(5))).isEmpty)

  test("minLength and maxLength bound string length"):
    assert(validateValue("ab", Rules(minLength = Some(3))).exists(_.kind == "minLength"))
    assert(validateValue("abcd", Rules(maxLength = Some(3))).exists(_.kind == "maxLength"))
    assert(validateValue("abc", Rules(minLength = Some(3), maxLength = Some(3))).isEmpty)

  test("min and max bound a numeric value"):
    assert(validateValue("3", Rules(min = Some(5))).exists(_.kind == "min"))
    assert(validateValue("9", Rules(max = Some(5))).exists(_.kind == "max"))
    assert(validateValue("5", Rules(min = Some(1), max = Some(9))).isEmpty)

  test("pattern rejects a non-matching value"):
    val email = Rules(pattern = Some("""^.+@.+$""".r))
    assert(validateValue("nope", email).exists(_.kind == "pattern"))
    assert(validateValue("a@b.com", email).isEmpty)

  test("a custom validator can reject with its own message"):
    val noFoo = Rules(validate = Seq(v => Option.when(v == "foo")("no foo allowed")))
    assert(validateValue("foo", noFoo).contains(FieldError("validate", "no foo allowed")))
    assert(validateValue("bar", noFoo).isEmpty)

  test("required wins over a later rule on a blank field"):
    assert(validateValue("", Rules(required = true, minLength = Some(5))).exists(_.kind == "required"))

  test("a custom message overrides the default"):
    val r = validateValue("", Rules(required = true, messages = Messages(required = "Needed!")))
    assert(r.contains(FieldError("required", "Needed!")))

  // --- store ---------------------------------------------------------------

  private def newStore(defaults: Map[String, Any] = Map.empty) =
    new FormStore(defaults, ValidationMode.OnSubmit, ValidationMode.OnChange)

  test("register seeds a field from its default value"):
    val s = newStore(Map("email" -> "seed@x.com"))
    s.register("email", Rules())
    assert(s.getValue("email") == "seed@x.com")

  test("submit reports invalid fields and increments the submit count"):
    val s = newStore(Map("email" -> ""))
    s.register("email", Rules(required = true))
    var valid: Map[String, Any]          = null
    var invalid: Map[String, FieldError] = null
    s.submit(v => valid = v, e => invalid = e)
    assert(valid == null)
    assert(invalid.contains("email"))
    assert(s.getSnapshot.submitCount == 1)
    assert(s.getSnapshot.isSubmitted)

  test("submit runs the valid handler once the values pass"):
    val s = newStore(Map("email" -> ""))
    s.register("email", Rules(required = true))
    s.setValue("email", "a@b.com", shouldValidate = false)
    var valid: Map[String, Any] = null
    s.submit(v => valid = v, _ => ())
    assert(valid("email") == "a@b.com")

  test("trigger validates a field on demand"):
    val s = newStore(Map("name" -> ""))
    s.register("name", Rules(required = true))
    assert(!s.trigger(Some("name")))
    assert(s.getSnapshot.errors.contains("name"))
    s.setValue("name", "Bob", shouldValidate = false)
    assert(s.trigger(Some("name")))
    assert(!s.getSnapshot.errors.contains("name"))

  test("isDirty tracks divergence from the default and reset clears it"):
    val s = newStore(Map("name" -> "default"))
    s.register("name", Rules())
    assert(!s.getSnapshot.isDirty)
    s.setValue("name", "changed", shouldValidate = false)
    assert(s.getSnapshot.isDirty)
    assert(s.getSnapshot.dirtyFields == Set("name"))
    s.reset(None)
    assert(s.getValue("name") == "default")
    assert(!s.getSnapshot.isDirty)

  test("setError and clearErrors manage errors by hand"):
    val s = newStore(Map("name" -> ""))
    s.register("name", Rules())
    s.setError("name", FieldError("server", "taken"))
    assert(s.getSnapshot.errors("name").message == "taken")
    s.clearErrors(Some("name"))
    assert(!s.getSnapshot.errors.contains("name"))

  // --- useForm in the DOM --------------------------------------------------

  test("typing into a registered input updates the form's values"):
    val c          = host()
    var form: Form = null
    val F = view {
      val f = useForm(Map("email" -> ""))
      form = f
      input(cls := "email", f.register("email"))
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "x@y.com")
    assert(form.getValue[String]("email") == "x@y.com")

  test("an uncontrolled input shows its default value on mount"):
    val c = host()
    val F = view {
      val f = useForm(Map("name" -> "Ada"))
      input(cls := "name", f.register("name"))
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(c.querySelector("input.name").asInstanceOf[dom.html.Input].value == "Ada")

  test("onChange mode surfaces an error as you type and clears it when fixed"):
    val c = host()
    val F = view {
      val f = useForm(Map("email" -> ""), mode = ValidationMode.OnChange)
      div(
        input(cls := "email", f.register("email", Rules(required = true, pattern = Some("""^.+@.+$""".r)))),
        span(cls := "err", f.formState.errors.get("email").map(_.message).getOrElse("")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "nope")
    assert(c.querySelector("span.err").textContent.nonEmpty)
    fireInput(c.querySelector("input.email"), "a@b.com")
    assert(c.querySelector("span.err").textContent.isEmpty)

  test("handleSubmit delivers the typed values to the valid handler"):
    val c                       = host()
    var got: Map[String, Any]   = null
    val F = view {
      val f = useForm(Map("email" -> ""))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(v => got = v),
        input(cls := "email", f.register("email", Rules(required = true))),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "hi@there.com")
    fireSubmit(c.querySelector("form.f"))
    assert(got("email") == "hi@there.com")

  test("a blank required field blocks submit and shows the error"):
    val c                  = host()
    var validRan           = false
    val F = view {
      val f = useForm(Map("email" -> ""))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(_ => validRan = true),
        input(cls := "email", f.register("email", Rules(required = true))),
        span(cls := "err", f.formState.errors.get("email").map(_.message).getOrElse("")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(!validRan)
    assert(c.querySelector("span.err").textContent.nonEmpty)

  test("a field is marked touched on blur"):
    val c          = host()
    var form: Form = null
    val F = view {
      val f = useForm(Map("name" -> ""))
      form = f
      input(cls := "name", f.register("name"))
    }
    render(F(), c)
    Scheduler.flushSync()
    assert(!form.formState.touchedFields.contains("name"))
    fireBlur(c.querySelector("input.name"))
    assert(form.formState.touchedFields.contains("name"))
