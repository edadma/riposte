package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// A skin is the seam that lets the same components target different visual systems.
// Components never name colours or shapes; they describe *intent* ([[Color]],
// [[ButtonVariant]], [[Size]]). A `Skin` translates that intent into the CSS classes
// of one concrete styling system, and the active skin is read from context — so
// switching an app's entire look is a single provider at the root, with no
// call-site changes.
//
// Each component contributes one method here. Keeping the lookup in the skin (rather
// than in the component, as DaisyUI-React libraries do) means the maps are built once
// and a component's call sites stay identical across every skin.

/** The per-part CSS classes for a [[Select]]. The listbox renders the same DOM under
  * every skin; this names each stylable piece so a skin can target them independently
  * (a single class string can't, since the parts are distinct elements). */
type SelectClasses = (
    root: String,
    trigger: String,
    value: String,
    arrow: String,
    clear: String,
    list: String,
    option: String,
)

/** The per-part CSS classes for an [[ImageCard]]. Like [[SelectClasses]], the DOM is
  * identical across skins; this names each stylable piece (the outer card, the
  * aspect-ratio frame, the image, the loading skeleton, the error placeholder, and the
  * badge / hover-overlay slots) so a skin can target them independently. */
type ImageCardClasses = (
    root: String,
    frame: String,
    img: String,
    skeleton: String,
    error: String,
    badge: String,
    overlay: String,
)

/** The per-part CSS classes for a [[Modal]]. The dialog renders the same DOM under every
  * skin; this names each stylable piece — the full-screen scrim/overlay, the dialog box,
  * the header band, the title, the close affordance, the scrollable body, and the footer
  * action row — so a skin can target them independently. */
type ModalClasses = (
    overlay: String,
    box: String,
    header: String,
    title: String,
    close: String,
    body: String,
    footer: String,
)

/** Maps a component's semantic props to the CSS classes that realize a particular
  * visual system. salle ships [[SalleSkin]] (its own look, styled by `salle.css`)
  * and [[DaisySkin]] (the DaisyUI class vocabulary). Add a method per new component;
  * both shipped skins must then implement it.
  */
trait Skin:
  /** Classes for a [[Button]] with the given colour, variant, and size. */
  def button(color: Color, variant: ButtonVariant, size: Size): String

  /** Classes for an [[Input]] of the given colour and size; `invalid` overrides the
    * colour with the error treatment. */
  def input(color: Color, size: Size, invalid: Boolean): String

  /** Classes for a [[Checkbox]] of the given colour and size. */
  def checkbox(color: Color, size: Size): String

  /** Classes for a [[Toggle]] (switch) of the given colour and size. */
  def toggle(color: Color, size: Size): String

  /** Classes for a [[Select]]'s parts. A custom listbox has several styled pieces
    * whose DOM is identical across skins; only the classes differ, so the skin returns
    * one class string per part rather than a single string. `invalid` overrides the
    * colour with the error treatment, as on [[input]]. */
  def select(color: Color, size: Size, invalid: Boolean): SelectClasses

  /** Classes for an [[ImageCard]]'s parts. `rounded` requests rounded corners on the
    * card; the active skin maps it to whatever radius treatment it uses. */
  def imageCard(rounded: Boolean): ImageCardClasses

  /** Classes for a [[Modal]]'s parts. `centered` vertically centres the dialog box
    * (versus anchoring it toward the top); the active skin maps it to its own
    * positioning. */
  def modal(centered: Boolean): ModalClasses

/** salle's native look. Emits stable `salle-*` classes whose rules live in
  * `salle.css` under `@layer salle`. Apps retheme it with plain CSS — override the
  * `--salle-*` custom properties for value changes, or the rules themselves for
  * structural changes (the low-priority layer means app CSS wins without
  * `!important`). This is the default skin, so an app that configures nothing still
  * gets a styled look.
  */
object SalleSkin extends Skin:
  def button(color: Color, variant: ButtonVariant, size: Size): String =
    bem("salle-btn", color.token, variant.token, size.token)

  def input(color: Color, size: Size, invalid: Boolean): String =
    bem("salle-input", if invalid then "error" else color.token, size.token)

  def checkbox(color: Color, size: Size): String =
    bem("salle-checkbox", color.token, size.token)

  def toggle(color: Color, size: Size): String =
    bem("salle-toggle", color.token, size.token)

  def select(color: Color, size: Size, invalid: Boolean): SelectClasses =
    (
      root = "salle-select",
      trigger = bem("salle-select__trigger", if invalid then "error" else color.token, size.token),
      value = "salle-select__value",
      arrow = "salle-select__arrow",
      clear = "salle-select__clear",
      list = "salle-select__list",
      option = "salle-select__option",
    )

  def imageCard(rounded: Boolean): ImageCardClasses =
    (
      root = bem("salle-image-card", if rounded then "rounded" else ""),
      frame = "salle-image-card__frame",
      img = "salle-image-card__img",
      skeleton = "salle-image-card__skeleton",
      error = "salle-image-card__error",
      badge = "salle-image-card__badge",
      overlay = "salle-image-card__overlay",
    )

  def modal(centered: Boolean): ModalClasses =
    (
      overlay = bem("salle-modal__overlay", if centered then "centered" else ""),
      box = "salle-modal__box",
      header = "salle-modal__header",
      title = "salle-modal__title",
      close = "salle-modal__close",
      body = "salle-modal__body",
      footer = "salle-modal__footer",
    )

/** The DaisyUI vocabulary, seeded from AsterUI's component class maps. salle emits
  * the classes (`btn btn-primary btn-outline btn-sm`); the styles come from DaisyUI
  * + Tailwind in the consuming app's CSS build, which must be set up for these
  * classes to mean anything. No app *view* code changes — only the skin provided at
  * the root. The mapping rule is uniform: a non-empty modifier token `t` for base
  * `b` becomes `b-t` (`btn-primary`, `btn-sm`); an empty token (a `Default` colour or
  * `Solid` variant) emits nothing, matching DaisyUI's "base look has no modifier".
  */
object DaisySkin extends Skin:
  def button(color: Color, variant: ButtonVariant, size: Size): String =
    daisy("btn", color.token, variant.token, size.token)

  // AsterUI's Input maps `status=error|warning` over the colour; salle's `invalid`
  // is the error case, which wins over the colour just as it does there.
  def input(color: Color, size: Size, invalid: Boolean): String =
    daisy("input", if invalid then "error" else color.token, size.token)

  def checkbox(color: Color, size: Size): String =
    daisy("checkbox", color.token, size.token)

  def toggle(color: Color, size: Size): String =
    daisy("toggle", color.token, size.token)

  // DaisyUI has no custom-combobox; we reuse its `input` look for the trigger and
  // `dropdown`/`menu` utilities for the popup, giving a native-feeling result without
  // any salle CSS. Keyboard-active highlighting falls back to hover here (DaisyUI has
  // no class for "active descendant"); SalleSkin styles it via `data-active`.
  def select(color: Color, size: Size, invalid: Boolean): SelectClasses =
    val trigger = daisy("input", if invalid then "error" else color.token, size.token)
    (
      root = "dropdown w-full",
      trigger = trigger + " w-full flex items-center gap-2 cursor-pointer",
      value = "flex-1 text-left truncate",
      arrow = "opacity-60 shrink-0",
      clear = "opacity-60 hover:opacity-100 cursor-pointer shrink-0",
      list =
        "dropdown-content menu bg-base-100 rounded-box shadow-lg border border-base-300 max-h-60 overflow-auto w-full mt-1 z-[1] flex-nowrap p-1",
      option = "rounded-lg",
    )

  // Mapped to DaisyUI's `card` plus utilities; the hover overlay and badge corner are
  // positioned with Tailwind utilities (best-effort, like `select` — the fully styled
  // hover transition lives in SalleSkin's CSS). The frame is `relative` so the
  // absolutely-positioned skeleton/error/badge/overlay anchor to it.
  def imageCard(rounded: Boolean): ImageCardClasses =
    (
      root = "card bg-base-100 shadow-sm overflow-hidden group" + (if rounded then " rounded-box" else ""),
      frame = "relative overflow-hidden w-full h-full",
      img = "w-full h-full block",
      skeleton = "skeleton absolute inset-0 w-full h-full",
      error = "absolute inset-0 flex items-center justify-center bg-base-200 text-base-content/40",
      badge = "absolute top-2 right-2 z-10",
      overlay =
        "absolute inset-0 flex items-end opacity-0 group-hover:opacity-100 transition-opacity bg-gradient-to-t from-black/60 to-transparent",
    )

  // DaisyUI's `modal` is the full-screen scrim and `modal-box` the dialog; we only render
  // it while open, so emitting `modal-open` keeps it shown without a native <dialog>. The
  // close button reuses DaisyUI's circular ghost-button look and the footer its
  // `modal-action` row. Daisy centres the box by default, so `centered` adds nothing and
  // the non-centered case nudges it toward the top with `modal-top`.
  def modal(centered: Boolean): ModalClasses =
    (
      overlay = "modal modal-open" + (if centered then "" else " modal-top"),
      box = "modal-box",
      header = "flex items-center justify-between gap-4 mb-2",
      title = "text-lg font-bold",
      close = "btn btn-sm btn-circle btn-ghost",
      body = "py-2",
      footer = "modal-action",
    )

// Join a base class with its non-empty modifier tokens using salle's BEM-ish
// `base--token` convention (`salle-btn salle-btn--primary`).
private def bem(base: String, tokens: String*): String =
  (base +: tokens.filter(_.nonEmpty).map(t => s"$base--$t")).mkString(" ")

// Join a base class with its non-empty modifier tokens using DaisyUI's `base-token`
// convention (`btn btn-primary`).
private def daisy(base: String, tokens: String*): String =
  (base +: tokens.filter(_.nonEmpty).map(t => s"$base-$t")).mkString(" ")

/** The active skin for the subtree. Defaults to [[SalleSkin]]; override once with
  * [[SkinProvider]] to re-skin every salle component below that point. */
val SkinContext: Context[Skin] = createContext(SalleSkin)

/** The skin in effect at this point in the tree. Components call this and ask it for
  * their classes rather than hard-coding any. */
def useSkin()(using Hooks): Skin = useContext(SkinContext)

/** Apply `skin` to every salle component inside `child`:
  * `render(SkinProvider(DaisySkin)(App(())), container)`. Without it, components use
  * [[SalleSkin]]. */
def SkinProvider(skin: Skin)(child: VNode): VNode = SkinContext.provide(skin, child)
