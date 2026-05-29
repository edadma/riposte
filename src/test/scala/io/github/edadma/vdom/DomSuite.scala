package io.github.edadma.vdom

import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Shared fixtures for the DOM-level suites.
//
// Tests run under a real DOM (jsdom via the Test jsEnv), so they exercise the
// actual insertBefore / attribute / event semantics the reconciler relies on,
// rather than a hand-rolled model that could only confirm our own assumptions.
//
// State updates are batched onto the microtask queue, so the `click` / `input`
// helpers call `Scheduler.flushSync()` afterwards — committed DOM can then be
// asserted synchronously. Plain `assert` is used rather than ScalaTest's
// Matchers, whose DSL words `value` / `key` / `empty` would shadow the vdom
// identifiers of the same name.
trait DomSuite extends AnyFunSuite:

  // A fresh detached container per call, so cases don't bleed into each other.
  protected def container(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  protected def fireClick(el: dom.Element): Unit =
    el.dispatchEvent(new dom.Event("click"))
    Scheduler.flushSync()

  protected def typeInto(el: dom.Element, v: String): Unit =
    el.asInstanceOf[dom.html.Input].value = v
    el.dispatchEvent(new dom.Event("input"))
    Scheduler.flushSync()
