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

/** The per-part CSS classes for a [[Tag]]. A tag is a badge plus a close button; the root
  * carries the badge look and the close button needs its own class, so the skin returns
  * both. (Plain [[Badge]] needs only one class, so it has a `String`-returning method.) */
type TagClasses = (
    root: String,
    close: String,
)

/** The per-part CSS classes for a [[Pagination]] strip: the `root` nav, each `item` button
  * (prev/next/page), the `active` modifier added to the current page's button, and the
  * `dots`/status span. Distinct elements, so the skin returns one class per part. */
type PaginationClasses = (
    root: String,
    item: String,
    active: String,
    dots: String,
)

/** The per-part CSS classes for a [[Dropdown]]: the `root` wrapper, the `trigger` button,
  * its `arrow` caret, the `menu` popup, each `item`, the `divider` rule, and an item's
  * leading `icon` slot. Highlight (`data-active`) and danger (`data-danger`) are styled via
  * those data attributes rather than classes, mirroring [[Select]]. */
type DropdownClasses = (
    root: String,
    trigger: String,
    arrow: String,
    menu: String,
    item: String,
    divider: String,
    icon: String,
)

/** The per-part CSS classes for a [[toast]] notification: the `item` box (which carries the
  * type accent), its leading `icon` slot and the `spinner` used by the loading variant, the
  * `body` column, the `message` (main line) and `description` (secondary line), and the
  * `close` button. The placement region is styled separately via [[Skin.toastRegion]], since
  * it depends on placement, not type. */
type ToastClasses = (
    item: String,
    icon: String,
    spinner: String,
    body: String,
    message: String,
    description: String,
    close: String,
)

/** The per-part CSS classes for a [[Tooltip]]: the `root` wrapper (the positioning context),
  * the floating `tip` bubble (which carries the placement + colour look), and its `arrow`.
  * Distinct elements, so the skin returns one class per part. Open/exit phase rides
  * `data-state` on the tip, not the class. */
type TooltipClasses = (
    root: String,
    tip: String,
    arrow: String,
)

/** The per-part CSS classes for a [[Tabs]] set: the `root` wrapper, the `list` strip
  * (which carries the variant + size look), each `tab` button with its `active` and
  * `disabled` modifiers (applied conditionally, like [[PaginationClasses.active]]), a tab's
  * leading `icon` slot, and the `panel` region. */
type TabsClasses = (
    root: String,
    list: String,
    tab: String,
    active: String,
    disabled: String,
    icon: String,
    panel: String,
)

/** The per-part CSS classes for a [[Lightbox]]: the full-screen `overlay` scrim (the
  * positioning context), the centred `content` wrapper, the `img` itself, the corner `close`
  * button, the `prev`/`next` navigation controls, and the `counter` readout. Distinct elements,
  * so the skin returns one class per part; open/exit phase and zoom ride `data-*`, not classes. */
type LightboxClasses = (
    overlay: String,
    content: String,
    img: String,
    close: String,
    prev: String,
    next: String,
    counter: String,
)

/** The per-part CSS classes for an [[Image]]: the `root` wrapper, the `img` element, and the
  * `error` placeholder shown when the source (and any fallback) fails. `rounded` requests
  * rounded corners on the wrapper. */
type ImageClasses = (
    root: String,
    img: String,
    error: String,
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

  /** The class for a [[Skeleton]] placeholder block. `animated` requests the loading
    * shimmer/pulse; a skin may render a still block when it is false. One class (not a
    * parts tuple) — the composites (text, image) reuse it for each of their pieces. */
  def skeleton(animated: Boolean): String

  /** The class for a [[Badge]] of the given colour, variant, and size. One class — a badge
    * is a single element. [[Tag]] reuses this look for its root via [[tag]], and
    * [[CheckableTag]] reuses it directly for its two states. */
  def badge(color: Color, variant: BadgeVariant, size: Size): String

  /** Classes for a [[Tag]]'s parts: the `root` (the badge look) and the `close` button.
    * The root mirrors [[badge]]; the close button gets its own class so a skin can size and
    * style the dismiss affordance independently. */
  def tag(color: Color, variant: BadgeVariant, size: Size): TagClasses

  /** Classes for a [[Pagination]] strip of the given size. Returns the nav `root`, each
    * `item` button, the `active`-page modifier, and the `dots` gap span. */
  def pagination(size: Size): PaginationClasses

  /** Classes for a [[Dropdown]]'s parts. Stateless — the open/active/danger states are
    * mirrored to `data-*` and styled from there, so this returns the structural classes
    * only. */
  def dropdown: DropdownClasses

  /** The class for a toast placement region — the fixed-position stack anchored at one
    * corner/edge. Depends only on `placement`, since one region holds toasts of mixed
    * types ([[ToastPlacement]]). */
  def toastRegion(placement: ToastPlacement): String

  /** Classes for a toast's parts, by notification `kind`. Otherwise stateless — the
    * enter/exit phase is mirrored to `data-state` and styled from there. */
  def toast(kind: ToastType): ToastClasses

  /** The class for a [[Spinner]] indicator of the given type, size, and colour. One class —
    * the mark is a single decorative element; the wrapper/overlay structure around it is plain
    * layout, the same under any skin. */
  def spinner(kind: SpinnerType, size: Size, color: Color): String

  /** The class for a linear [[Progress]] bar of the given colour. One class on the native
    * `<progress>` element; the determinate value and the indeterminate state ride the
    * element's attributes, not the class. */
  def progress(color: Color): String

  /** The class for a [[RadialProgress]] ring of the given colour. One class; the value, size,
    * and thickness ride inline custom properties, not the class. */
  def radialProgress(color: Color): String

  /** Classes for a [[Tooltip]]'s parts. `placement` chooses the side the bubble sits on and
    * `color` tints it; the open/exit phase rides `data-state` and is styled from there. */
  def tooltip(placement: TooltipPlacement, color: Color): TooltipClasses

  /** Classes for a [[Tabs]] set's parts. `variant` and `size` shape the strip; `position`
    * places the panel above or below (the skin maps it to the panel's margin side). The
    * `active`/`disabled` tab modifiers are applied conditionally by the component. */
  def tabs(variant: TabsVariant, size: Size, position: TabsPosition): TabsClasses

  /** Classes for a [[Lightbox]]'s parts. Stateless — the open/exit phase and zoom are mirrored
    * to `data-*` and styled from there, so this returns the structural classes only. */
  def lightbox: LightboxClasses

  /** Classes for an [[Image]]'s parts. `rounded` requests rounded corners on the wrapper; the
    * active skin maps it to whatever radius treatment it uses. */
  def image(rounded: Boolean): ImageClasses

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

  // One class; `--static` drops the shimmer keyframes for a still block (e.g. when the
  // user prefers reduced motion and the caller passes animated = false).
  def skeleton(animated: Boolean): String =
    bem("salle-skeleton", if animated then "" else "static")

  def badge(color: Color, variant: BadgeVariant, size: Size): String =
    bem("salle-badge", color.token, variant.token, size.token)

  def tag(color: Color, variant: BadgeVariant, size: Size): TagClasses =
    (
      root = bem("salle-tag", color.token, variant.token, size.token),
      close = "salle-tag__close",
    )

  def pagination(size: Size): PaginationClasses =
    (
      root = "salle-pagination",
      item = bem("salle-pagination__item", size.token),
      active = "salle-pagination__item--active",
      dots = "salle-pagination__dots",
    )

  def dropdown: DropdownClasses =
    (
      root = "salle-dropdown",
      trigger = "salle-dropdown__trigger",
      arrow = "salle-dropdown__arrow",
      menu = "salle-dropdown__menu",
      item = "salle-dropdown__item",
      divider = "salle-dropdown__divider",
      icon = "salle-dropdown__icon",
    )

  def toastRegion(placement: ToastPlacement): String =
    bem("salle-toast", placement.token)

  def toast(kind: ToastType): ToastClasses =
    (
      item = bem("salle-toast__item", kind.token),
      icon = "salle-toast__icon",
      spinner = "salle-toast__spinner",
      body = "salle-toast__body",
      message = "salle-toast__message",
      description = "salle-toast__description",
      close = "salle-toast__close",
    )

  def spinner(kind: SpinnerType, size: Size, color: Color): String =
    bem("salle-spinner", kind.token, size.token, color.token)

  def progress(color: Color): String =
    bem("salle-progress", color.token)

  def radialProgress(color: Color): String =
    bem("salle-radial-progress", color.token)

  def tooltip(placement: TooltipPlacement, color: Color): TooltipClasses =
    (
      root = "salle-tooltip",
      tip = bem("salle-tooltip__tip", placement.token, color.token),
      arrow = "salle-tooltip__arrow",
    )

  def tabs(variant: TabsVariant, size: Size, position: TabsPosition): TabsClasses =
    (
      root = bem("salle-tabs", position.token),
      list = bem("salle-tabs__list", variant.token, size.token),
      tab = "salle-tabs__tab",
      active = "salle-tabs__tab--active",
      disabled = "salle-tabs__tab--disabled",
      icon = "salle-tabs__icon",
      panel = "salle-tabs__panel",
    )

  def lightbox: LightboxClasses =
    (
      overlay = "salle-lightbox__overlay",
      content = "salle-lightbox__content",
      img = "salle-lightbox__img",
      close = "salle-lightbox__close",
      prev = "salle-lightbox__nav salle-lightbox__nav--prev",
      next = "salle-lightbox__nav salle-lightbox__nav--next",
      counter = "salle-lightbox__counter",
    )

  def image(rounded: Boolean): ImageClasses =
    (
      root = bem("salle-image", if rounded then "rounded" else ""),
      img = "salle-image__img",
      error = "salle-image__error",
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

  // DaisyUI's `skeleton` is always animated (its own pulse); a still block falls back to a
  // plain muted fill (`bg-base-300 rounded-box`) since there is no "static skeleton" class.
  def skeleton(animated: Boolean): String =
    if animated then "skeleton" else "bg-base-300 rounded-box"

  def badge(color: Color, variant: BadgeVariant, size: Size): String =
    daisy("badge", color.token, variant.token, size.token)

  // A tag is DaisyUI's badge plus an inline gap for the icon/close; the close button reuses
  // the tiny circular ghost-button look (matching Modal's close affordance).
  def tag(color: Color, variant: BadgeVariant, size: Size): TagClasses =
    (
      root = daisy("badge", color.token, variant.token, size.token) + " gap-1 inline-flex items-center",
      close = "btn btn-xs btn-circle btn-ghost",
    )

  // DaisyUI groups the buttons with `join`; each is a `btn join-item` at the chosen size,
  // the active page gets `btn-active`, and the dots reuse the disabled-button look.
  def pagination(size: Size): PaginationClasses =
    val btn = daisy("btn", size.token)
    (
      root = "join",
      item = btn + " join-item",
      active = "btn-active",
      dots = btn + " join-item btn-disabled",
    )

  // DaisyUI's `dropdown` wraps a `btn` trigger over a `dropdown-content menu` popup. The
  // keyboard-active highlight is the known DaisyUI gap (no "active descendant" class), same
  // as Select; SalleSkin styles it via `data-active`.
  def dropdown: DropdownClasses =
    (
      root = "dropdown",
      trigger = "btn flex items-center gap-2",
      arrow = "opacity-60 shrink-0",
      menu =
        "dropdown-content menu bg-base-100 rounded-box shadow-lg border border-base-300 min-w-52 mt-1 z-[1] p-1",
      item = "rounded-lg",
      divider = "divider my-0",
      icon = "inline-flex items-center shrink-0",
    )

  // DaisyUI's `toast` utility fixes a stack to a corner/edge; `toast-top`/`toast-bottom`
  // chooses the vertical anchor and `toast-start`/`toast-center`/`toast-end` the horizontal.
  def toastRegion(placement: ToastPlacement): String =
    "toast " + (placement match
      case ToastPlacement.TopLeft      => "toast-top toast-start"
      case ToastPlacement.TopCenter    => "toast-top toast-center"
      case ToastPlacement.TopRight     => "toast-top toast-end"
      case ToastPlacement.BottomLeft   => "toast-bottom toast-start"
      case ToastPlacement.BottomCenter => "toast-bottom toast-center"
      case ToastPlacement.BottomRight  => "toast-bottom toast-end"
    )

  // Each item is an `alert` tinted by type; loading reuses the info tint alongside DaisyUI's
  // spinner. There is no "alert-loading", so it maps to `alert-info`.
  def toast(kind: ToastType): ToastClasses =
    val alertColor = kind match
      case ToastType.Success => "alert-success"
      case ToastType.Warning => "alert-warning"
      case ToastType.Error   => "alert-error"
      case _                 => "alert-info"
    (
      item = "alert " + alertColor + " shadow-lg",
      icon = "shrink-0",
      spinner = "loading loading-spinner loading-sm",
      body = "flex flex-col min-w-0",
      message = "font-semibold",
      description = "text-sm opacity-80",
      close = "btn btn-xs btn-circle btn-ghost",
    )

  // DaisyUI's `loading` is the spinner; the type picks the animation and the size scales it.
  // It has no colour class (the mark inherits currentColor), so a colour maps to a Tailwind
  // `text-*` utility — the same way AsterUI colours its RadialProgress ring.
  def spinner(kind: SpinnerType, size: Size, color: Color): String =
    daisy("loading", kind.token, size.token) + (if color == Color.Default then "" else s" text-${color.token}")

  def progress(color: Color): String =
    daisy("progress", color.token)

  // DaisyUI's `radial-progress` also takes its ring colour from currentColor, so a colour maps
  // to a `text-*` utility rather than a `radial-progress-*` class.
  def radialProgress(color: Color): String =
    "radial-progress" + (if color == Color.Default then "" else s" text-${color.token}")

  // DaisyUI's own `tooltip` is a pure-CSS, hover-only, string-in-`data-tip` affair that renders
  // its own ::before bubble — incompatible with salle's JS-rendered, controllable, rich-content
  // tip node. So we style our tip node with Tailwind utilities instead: an absolutely-positioned
  // bubble offset to the chosen side, filled with the colour's `bg-*`/`*-content` pair (Default
  // → neutral). The arrow is dropped here (utilities can't easily draw it); SalleSkin's CSS has
  // a proper arrow.
  def tooltip(placement: TooltipPlacement, color: Color): TooltipClasses =
    val pos = placement match
      case TooltipPlacement.Top    => "bottom-full left-1/2 -translate-x-1/2 mb-2"
      case TooltipPlacement.Bottom => "top-full left-1/2 -translate-x-1/2 mt-2"
      case TooltipPlacement.Left   => "right-full top-1/2 -translate-y-1/2 mr-2"
      case TooltipPlacement.Right  => "left-full top-1/2 -translate-y-1/2 ml-2"
    val c = if color == Color.Default then "neutral" else color.token
    (
      root = "relative inline-block",
      tip =
        s"absolute z-[1] $pos px-2 py-1 rounded text-sm shadow-lg whitespace-nowrap pointer-events-none bg-$c text-$c-content",
      arrow = "hidden",
    )

  // DaisyUI puts the variant + size on the `tabs` strip and `tab`/`tab-active`/`tab-disabled`
  // on each button. There is no wrapper class, so the root is empty; position is handled by DOM
  // order in the component, and the panel just takes a top/bottom margin.
  def tabs(variant: TabsVariant, size: Size, position: TabsPosition): TabsClasses =
    (
      root = "",
      list = daisy("tabs", variant.token, size.token),
      tab = "tab",
      active = "tab-active",
      disabled = "tab-disabled",
      icon = "mr-1 inline-flex items-center",
      panel = if position == TabsPosition.Top then "mt-4" else "mb-4",
    )

  // DaisyUI has no lightbox, so the overlay is a fixed full-screen scrim built from Tailwind
  // utilities (above the modal's z-50). The image is contained within the viewport; the close
  // and nav controls reuse the circular ghost-button look, tinted white over the dark scrim.
  def lightbox: LightboxClasses =
    (
      overlay = "fixed inset-0 z-[60] flex items-center justify-center bg-black/90 p-4",
      content = "relative flex items-center justify-center max-w-full max-h-full",
      img = "max-w-full max-h-[90vh] object-contain",
      close = "btn btn-sm btn-circle btn-ghost absolute top-4 right-4 text-white",
      prev = "btn btn-circle btn-ghost absolute left-4 top-1/2 -translate-y-1/2 text-white",
      next = "btn btn-circle btn-ghost absolute right-4 top-1/2 -translate-y-1/2 text-white",
      counter = "absolute bottom-4 left-1/2 -translate-x-1/2 text-white text-sm",
    )

  // A plain inline image; `rounded` adds DaisyUI's box radius (with clipping). The error
  // placeholder reuses the muted base-200 fill used by ImageCard's error state.
  def image(rounded: Boolean): ImageClasses =
    (
      root = "relative inline-block" + (if rounded then " rounded-box overflow-hidden" else ""),
      img = "block max-w-full",
      error = "flex items-center justify-center bg-base-200 text-base-content/40 p-4",
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
