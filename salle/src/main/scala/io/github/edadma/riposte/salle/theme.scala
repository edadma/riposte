package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import scala.scalajs.js
import scala.collection.mutable

// Theming. A single global theme name drives the `data-theme` attribute on the
// document root. Both skins respond to it: SalleSkin via the `[data-theme="…"]` token
// blocks in salle.css, DaisySkin via DaisyUI's own themes. The name is a free string,
// so the theme set is open: salle ships `light`/`dark`, `system` follows the OS, and
// any other name (`"dracula"`, …) selects whatever `[data-theme]` block the app defines.

private object ThemeStore:
  private val StorageKey = "salle-theme"

  private var current: String = "system"
  private val listeners       = mutable.Set.empty[() => Unit]
  private var started         = false

  private def root = dom.document.documentElement

  // jsdom (and very old browsers) have no matchMedia; treat its absence as "light".
  private def hasMatchMedia: Boolean =
    !js.isUndefined(dom.window.asInstanceOf[js.Dynamic].matchMedia)

  private def prefersDark: Boolean =
    hasMatchMedia && dom.window.matchMedia("(prefers-color-scheme: dark)").matches

  /** The concrete theme a name resolves to — `"system"` becomes the OS preference. */
  def resolve(name: String): String =
    if name == "system" then (if prefersDark then "dark" else "light") else name

  private def applyToDom(): Unit = root.setAttribute("data-theme", resolve(current))

  private def notifyListeners(): Unit = listeners.foreach(_())

  // Idempotent first-use setup: restore the persisted choice, paint it, and wire the
  // live sources of change (OS preference while on "system", and other tabs).
  private def start(): Unit =
    if !started then
      started = true
      Option(dom.window.localStorage.getItem(StorageKey)).foreach(s => current = s)
      applyToDom()
      if hasMatchMedia then
        dom.window
          .matchMedia("(prefers-color-scheme: dark)")
          .addEventListener(
            "change",
            (_: dom.Event) => if current == "system" then { applyToDom(); notifyListeners() },
          )
      dom.window.addEventListener(
        "storage",
        (e: dom.StorageEvent) =>
          if e.key == StorageKey then
            current = Option(e.newValue).getOrElse("system")
            applyToDom()
            notifyListeners(),
      )

  def get: String =
    start()
    current

  def set(name: String): Unit =
    start()
    current = name
    dom.window.localStorage.setItem(StorageKey, name)
    applyToDom()
    notifyListeners()

  def subscribe(cb: () => Unit): () => Unit =
    start()
    listeners += cb
    () =>
      listeners -= cb
      ()

  // Test seam: clear persisted state and listeners so each spec starts fresh.
  def reset(): Unit =
    listeners.clear()
    started = false
    current = "system"
    dom.window.localStorage.removeItem(StorageKey)
    root.removeAttribute("data-theme")

/** Reset theming to its initial state — for tests only. */
private[salle] def resetThemeForTest(): Unit = ThemeStore.reset()

/** Subscribe to the active theme. Returns the stored theme name (`"system"` /
  * `"light"` / `"dark"` / a custom name), the `resolved` concrete theme (`"system"`
  * becomes the OS preference), a `setTheme` that accepts any name, and a light⇄dark
  * `toggle`. Reading it also installs salle's theme management (it sets `data-theme`,
  * persists to localStorage, follows the OS on `"system"`, and syncs across tabs).
  */
def useTheme()(using
    Hooks,
): (theme: String, resolved: String, setTheme: String => Unit, toggle: () => Unit) =
  val theme = useSyncExternalStore(ThemeStore.subscribe, () => ThemeStore.get)
  (
    theme = theme,
    resolved = ThemeStore.resolve(theme),
    setTheme = (name: String) => ThemeStore.set(name),
    toggle = () => ThemeStore.set(if ThemeStore.resolve(ThemeStore.get) == "dark" then "light" else "dark"),
  )

// Feather sun/moon icons, drawn with `currentColor` so they take the button's colour.
// Injected as trusted innerHTML (a static literal) so the SVG parses into real,
// correctly-namespaced nodes without needing SVG element builders here.
private val MoonIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>"""

private val SunIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>"""

/** An icon button that toggles between light and dark: a moon in light mode (tap to go
  * dark), a sun in dark mode (tap to go light). The glyph uses `currentColor`, so it
  * inherits the surrounding text colour. */
val ThemeToggle = view {
  val t    = useTheme()
  val dark = t.resolved == "dark"
  button(
    cls           := "salle-theme-toggle",
    typ           := "button",
    aria("label") := (if dark then "Switch to light theme" else "Switch to dark theme"),
    onClick       := (_ => t.toggle()),
    unsafeHtml(if dark then SunIcon else MoonIcon),
  )
}

/** A `<select>` of theme names bound to the active theme — handy when an app offers
  * more than light/dark (the open theme set). `ThemeSelect(Seq("system","light","dark"))`.
  */
def ThemeSelect(themes: Seq[String]): VNode = ThemeSelectImpl(themes.toVector)

private val ThemeSelectImpl = component[Vector[String]] { names =>
  val t = useTheme()
  select(
    cls      := "salle-theme-select",
    value    := t.theme,
    onChange := (e => t.setTheme(e.target.asInstanceOf[dom.html.Select].value)),
    names.map(n => option(value := n, n)),
  )
}
