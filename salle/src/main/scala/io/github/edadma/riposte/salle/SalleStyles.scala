package io.github.edadma.riposte.salle

import org.scalajs.dom

/** Installs [[SalleSkin]]'s stylesheet into the document.
  *
  * Because salle is always Scala.js, its CSS travels *inside* the compiled artifact
  * (baked in as [[SalleCssContent]] by `project/SalleCss.scala`) rather than as a
  * separate file to download. The first time a salle component renders under
  * [[SalleSkin]], [[useSkin]] calls [[install]], which appends one `<style>` to the
  * document head. The upshot: add `riposte-salle` to your build and the components are
  * styled — no `<link>`, no CDN, no bundler, nothing to keep in version-sync.
  *
  * Apps that use [[DaisySkin]] never trigger this (they bring their own DaisyUI +
  * Tailwind build), and apps wanting to override salle's look just add their own CSS
  * after it — every rule lives in the low-priority `@layer salle`, so unlayered app
  * CSS wins without `!important`.
  */
object SalleStyles:
  private val elementId = "salle-styles"
  private var installed  = false

  /** Append salle's stylesheet to the document head, once. Idempotent: a no-op after
    * the first call, and guarded by the element id so a second runtime can't double it. */
  def install(): Unit =
    if !installed then
      // Set the guard before touching the DOM: appending to <head> can synchronously
      // re-enter this (via the renderer), and that re-entry must be a no-op rather than
      // infinite recursion.
      installed = true
      if shouldInject && dom.document.getElementById(elementId) == null then
        val style = dom.document.createElement("style")
        style.id = elementId
        style.textContent = SalleCssContent.css
        dom.document.head.appendChild(style)

  /** salle's stylesheet uses modern CSS (cascade layers) that every real browser
    * handles but jsdom's CSS engine cannot — and jsdom surfaces that parse failure
    * asynchronously, so it can't be caught. The unit tests run in jsdom, so skip
    * injection there; the real-browser path is exercised by the salle-e2e Playwright
    * harness. This is the only place that needs to know it might be running headless. */
  private def shouldInject: Boolean =
    !dom.window.navigator.userAgent.contains("jsdom")

/** Inject salle's stylesheet eagerly — e.g. at app start, before the first salle
  * component renders, to guarantee no flash of unstyled content. Usually unnecessary,
  * since the styles self-install on first render; provided as an explicit escape hatch. */
def installSalleStyles(): Unit = SalleStyles.install()
