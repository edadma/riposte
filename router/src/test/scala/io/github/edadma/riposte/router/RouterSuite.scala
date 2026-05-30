package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite
import scala.scalajs.js

// Shared fixtures for the router's DOM-level suites. Tests run under jsdom, so the
// History API, anchor clicks, and event dispatch behave like a real browser — with
// the one documented exception that `history.back()` does not fire `popstate` in
// jsdom, which is why the back/forward case dispatches a synthetic `popstate`
// rather than calling `history.back()`.
trait RouterSuite extends AnyFunSuite:

  // Start each test from a clean location in the chosen mode, dropping any
  // subscribers left over from a prior test.
  protected def start(mode: RouterMode = RouterMode.History): Unit =
    Location.reset(mode)

  protected def host(): dom.Element =
    val el = dom.document.createElement("div")
    dom.document.body.appendChild(el)
    el

  // A plain left-click, dispatched as a real browser would: button 0, no modifier
  // keys, and crucially *cancelable* — so a `Link`'s `preventDefault()` actually
  // suppresses the anchor's navigation (an uncancelable synthetic click would let
  // jsdom attempt a real navigation, which it doesn't implement).
  protected def click(el: dom.Element): Unit =
    val init = new dom.MouseEventInit {
      bubbles = true
      cancelable = true
      button = 0
    }
    el.dispatchEvent(new dom.MouseEvent("click", init))
    Scheduler.flushSync()
