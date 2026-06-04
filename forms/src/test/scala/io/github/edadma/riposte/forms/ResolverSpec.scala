package io.github.edadma.riposte.forms

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{Future, Promise}

// The schema/async layer: a `resolver` replacing the per-field rules, an async resolver
// driving `isValidating`, and `handleSubmitAsync` holding `isSubmitting` across a Future.
// The async cases use an explicit `Promise` so the in-flight state can be observed before
// it settles — the store completes its continuation inline (parasitic executor) when the
// Promise is fulfilled, and a `flushSync` then commits the re-render.
class ResolverSpec extends AnyFunSuite:

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

  // --- sync resolver -------------------------------------------------------

  private val requireEmail: Resolver = vs =>
    if vs.getOrElse("email", "").asInstanceOf[String].isEmpty then Map("email" -> FieldError("schema", "Email required"))
    else Map.empty

  test("a sync resolver replaces the built-in rules and blocks submit when invalid"):
    val c        = host()
    var validRan = false
    val F = view {
      val f = useForm(Map("email" -> ""), resolver = Some(requireEmail))
      form(
        cls      := "f",
        onSubmit := f.handleSubmit(_ => validRan = true),
        input(cls := "email", f.register("email")), // no Rules — the resolver drives
        span(cls := "err", f.formState.errors.get("email").map(_.message).getOrElse("")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(!validRan)
    assert(c.querySelector("span.err").textContent == "Email required")
    fireInput(c.querySelector("input.email"), "a@b.com")
    fireSubmit(c.querySelector("form.f"))
    assert(validRan)

  test("a sync resolver validates on change in onChange mode"):
    val c = host()
    val F = view {
      val f = useForm(Map("email" -> ""), mode = ValidationMode.OnChange, resolver = Some(requireEmail))
      div(
        input(cls := "email", f.register("email")),
        span(cls := "err", f.formState.errors.get("email").map(_.message).getOrElse("")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.email"), "x@y.com")
    assert(c.querySelector("span.err").textContent.isEmpty)
    fireInput(c.querySelector("input.email"), "")
    assert(c.querySelector("span.err").textContent == "Email required")

  // --- async submit --------------------------------------------------------

  test("handleSubmitAsync holds isSubmitting until the handler's Future settles"):
    val c        = host()
    var ff: Form = null
    val p        = Promise[Unit]()
    val F = view {
      val f = useForm(Map("email" -> "a@b.com"))
      ff = f
      form(
        cls      := "f",
        onSubmit := f.handleSubmitAsync(_ => p.future),
        input(cls := "email", f.register("email", Rules(required = true))),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(ff.formState.isSubmitting) // validated clean, handler still pending
    p.success(())
    Scheduler.flushSync()
    assert(!ff.formState.isSubmitting)
    assert(ff.formState.isSubmitted)

  test("a failing async submit handler still clears isSubmitting"):
    val c        = host()
    var ff: Form = null
    val p        = Promise[Unit]()
    val F = view {
      val f = useForm(Map("email" -> "a@b.com"))
      ff = f
      form(cls := "f", onSubmit := f.handleSubmitAsync(_ => p.future), input(cls := "email", f.register("email")))
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(ff.formState.isSubmitting)
    p.failure(new RuntimeException("boom"))
    Scheduler.flushSync()
    assert(!ff.formState.isSubmitting)

  test("an invalid async submit never runs the handler"):
    val c        = host()
    var ran      = false
    val F = view {
      val f = useForm(Map("email" -> ""))
      form(
        cls      := "f",
        onSubmit := f.handleSubmitAsync(_ => { ran = true; Future.successful(()) }),
        input(cls := "email", f.register("email", Rules(required = true))),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireSubmit(c.querySelector("form.f"))
    assert(!ran)

  // --- async resolver / isValidating --------------------------------------

  test("triggerAsync toggles isValidating and applies the resolver's errors when it settles"):
    val c        = host()
    var ff: Form = null
    val p        = Promise[Map[String, FieldError]]()
    val F = view {
      val f = useForm(Map("user" -> ""), asyncResolver = Some(_ => p.future))
      ff = f
      div(
        input(cls := "user", f.register("user")),
        span(cls := "state", if f.formState.isValidating then "validating" else "idle"),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    ff.triggerAsync()
    Scheduler.flushSync()
    assert(ff.formState.isValidating)
    assert(c.querySelector("span.state").textContent == "validating")
    p.success(Map("user" -> FieldError("schema", "Taken")))
    Scheduler.flushSync()
    assert(!ff.formState.isValidating)
    assert(ff.formState.errors.get("user").exists(_.message == "Taken"))

  test("an async resolver applies its result on change"):
    val c = host()
    val resolver: AsyncResolver = vs =>
      Future.successful(
        if vs.getOrElse("name", "").asInstanceOf[String].isEmpty then Map("name" -> FieldError("schema", "Name required"))
        else Map.empty,
      )
    val F = view {
      val f = useForm(Map("name" -> ""), mode = ValidationMode.OnChange, asyncResolver = Some(resolver))
      div(
        input(cls := "name", f.register("name")),
        span(cls := "err", f.formState.errors.get("name").map(_.message).getOrElse("")),
      )
    }
    render(F(), c)
    Scheduler.flushSync()
    fireInput(c.querySelector("input.name"), "Ada")
    assert(c.querySelector("span.err").textContent.isEmpty)
    fireInput(c.querySelector("input.name"), "")
    assert(c.querySelector("span.err").textContent == "Name required")
