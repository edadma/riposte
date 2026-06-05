package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// salle ships its CSS *inside* the compiled artifact and injects it at runtime instead
// of requiring a <link>. These specs cover the part that's verifiable under jsdom: that
// the build baked the real stylesheet in, and that the auto-install is safe to call
// during render. The actual DOM injection uses cascade layers that only a real browser's
// CSS engine accepts (jsdom can't parse them), so it's covered in the salle-e2e harness;
// here SalleStyles.shouldInject keeps jsdom from ever attempting it.
class SalleStylesSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  test("the build baked the real salle.css into the artifact"):
    val css = SalleCssContent.css
    assert(css.contains("@layer salle")) // wrapped in the cascade layer
    assert(css.contains(".salle-btn"))   // a parts/*.css selector made it in
    assert(css.contains("--salle"))      // tokens.css custom properties made it in
    assert(css.length > 10000)           // the whole stylesheet, not a fragment

  test("rendering a salle component triggers install without crashing"):
    // Under jsdom injection is skipped, but the render path (useSkin -> install) must
    // still run cleanly — this guards the re-entrancy fix.
    render(Button("a"), host())
    Scheduler.flushSync()
    render(Button("b"), host())
    Scheduler.flushSync()
    succeed

  test("installSalleStyles() is safe to call directly"):
    installSalleStyles()
    succeed
