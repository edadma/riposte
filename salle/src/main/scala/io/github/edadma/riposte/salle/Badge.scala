package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// Small status/label pills. Three pieces, all built on one badge look:
//
//   • Badge        — a non-interactive label: a category, a resolution ("4K"), "New".
//   • Tag          — a Badge that can carry a leading icon, be clicked, and be dismissed
//                    (a close button that fires onClose).
//   • CheckableTag — a Tag that toggles on click, for filter chips ("Nature", "Abstract").
//
// (AntD/AsterUI call the icon-carrying variant "Chip"; salle folds that into Tag's `icon`
// slot rather than shipping a third near-duplicate.)
//
// Colour/variant/size come from the active [[Skin]]; `pill` (full radius) and `dot` (a tiny
// dotless circle) are plain inline geometry, skin-independent. State is mirrored to `data-*`:
// Badge carries `data-part=badge` + `data-dot`; Tag adds `data-part`(tag|icon|close) +
// `data-closable`; CheckableTag adds `data-checked` and ARIA.

/** The visual style of a [[Badge]]/[[Tag]], orthogonal to its [[Color]]. `Solid` is the
  * filled default; the others reduce emphasis. Mirrors DaisyUI's badge style axis. */
enum BadgeVariant:
  case Solid, Outline, Soft, Dash

  /** The lowercase modifier token (`"outline"`); empty for `Solid`, the default fill. */
  def token: String = this match
    case Solid => ""
    case v     => v.toString.toLowerCase

private val BadgeImpl =
  container[(color: Color, variant: BadgeVariant, size: Size, pill: Boolean, dot: Boolean)] { (p, children) =>
    val skin = useSkin()

    // `pill` and `dot` are pure geometry, the same under any skin, so they live inline
    // rather than as skin classes: a pill is a full-radius capsule; a dot is a tiny circle
    // with no text (a bare status indicator).
    var sty = Map.empty[String, String]
    if p.pill then sty += "border-radius" -> "999px"
    if p.dot then
      sty += "width"         -> "0.6rem"
      sty += "height"        -> "0.6rem"
      sty += "padding"       -> "0"
      sty += "border-radius" -> "50%"

    span(
      cls           := skin.badge(p.color, p.variant, p.size),
      data("part")  := "badge",
      data("dot")   := p.dot,
      style         := sty,
      if p.dot then NoMod else children,
    )
  }

/** A small label pill — a category, a resolution badge, "New". `children` are its content
  * (omitted when `dot = true`). [[Color]] and [[BadgeVariant]] set the look, [[Size]] the
  * scale; `pill` rounds it to a full capsule, `dot` collapses it to a tiny indicator circle
  * with no text. Classes come from the active [[Skin]]; state is mirrored to `data-*`. */
def Badge(
    color:   Color        = Color.Default,
    variant: BadgeVariant = BadgeVariant.Solid,
    size:    Size         = Size.Md,
    pill:    Boolean      = false,
    dot:     Boolean      = false,
)(children: VNode*): VNode =
  BadgeImpl((color = color, variant = variant, size = size, pill = pill, dot = dot))(children*)

private val TagImpl =
  container[
    (
        color: Color,
        variant: BadgeVariant,
        size: Size,
        pill: Boolean,
        closable: Boolean,
        onClose: () => Unit,
        icon: Option[VNode],
        onClick: () => Unit,
    ),
  ] { (p, children) =>
    val skin  = useSkin()
    val parts = skin.tag(p.color, p.variant, p.size)

    val iconNode: Mod = p.icon match
      case Some(ic) => span(cls := "salle-tag__icon", data("part") := "icon", ic)
      case None     => NoMod

    val closeNode: Mod =
      if p.closable then
        button(
          cls           := parts.close,
          typ           := "button",
          data("part")  := "close",
          aria("label") := "Remove",
          // Don't let the close click bubble to the tag's own onClick.
          onClick := (e =>
            e.stopPropagation()
            p.onClose()
          ),
          unsafeHtml(TagCloseIcon),
        )
      else NoMod

    var sty = Map.empty[String, String]
    if p.pill then sty += "border-radius" -> "999px"

    span(
      cls            := parts.root,
      data("part")   := "tag",
      data("closable") := p.closable,
      style          := sty,
      onClick        := (_ => p.onClick()),
      iconNode,
      children,
      closeNode,
    )
  }

/** A [[Badge]] that can carry a leading `icon`, respond to `onClick`, and be dismissed.
  * When `closable`, a trailing close button fires `onClose` (its click is kept from
  * bubbling to `onClick`). `children` are the label. Colour/variant/size/`pill` behave as on
  * [[Badge]]. Classes come from the active [[Skin]]; state is mirrored to `data-*`
  * (`data-part` tag|icon|close, `data-closable`). */
def Tag(
    color:    Color        = Color.Default,
    variant:  BadgeVariant = BadgeVariant.Solid,
    size:     Size         = Size.Md,
    pill:     Boolean      = false,
    closable: Boolean      = false,
    onClose:  () => Unit   = () => (),
    icon:     Option[VNode] = None,
    onClick:  () => Unit   = () => (),
)(children: VNode*): VNode =
  TagImpl(
    (
      color = color,
      variant = variant,
      size = size,
      pill = pill,
      closable = closable,
      onClose = onClose,
      icon = icon,
      onClick = onClick,
    ),
  )(children*)

private val CheckableTagImpl =
  container[(checked: Boolean, size: Size, onChange: Boolean => Unit)] { (p, children) =>
    val skin = useSkin()

    // Checked reads as a filled primary pill; unchecked as a quiet neutral one. Reuses the
    // badge look rather than a dedicated skin method — the two states are just two
    // (colour, variant) choices.
    val color   = if p.checked then Color.Primary else Color.Neutral
    val variant = if p.checked then BadgeVariant.Solid else BadgeVariant.Soft

    val toggle: () => Unit = () => p.onChange(!p.checked)

    span(
      cls              := skin.badge(color, variant, p.size) + " salle-tag--checkable",
      role             := "button",
      tabIndex         := "0",
      aria("pressed")  := (if p.checked then "true" else "false"),
      data("part")     := "checkable-tag",
      data("checked")  := p.checked,
      style            := Map("cursor" -> "pointer", "user-select" -> "none"),
      onClick          := (_ => toggle()),
      onKeyDown := (e =>
        if e.key == "Enter" || e.key == " " || e.key == "Spacebar" then
          e.preventDefault()
          toggle()
      ),
      children,
    )
  }

/** A [[Tag]] that toggles between checked and unchecked on click — a filter chip. It is
  * *controlled*: the caller owns `checked` and is told of a requested flip via `onChange`.
  * Checked is a filled primary pill, unchecked a quiet neutral one. Fully keyboard-operable
  * (`role=button`, Enter/Space) with `aria-pressed` and a `data-checked` mirror. */
def CheckableTag(
    checked:  Boolean        = false,
    size:     Size           = Size.Md,
    onChange: Boolean => Unit = _ => (),
)(children: VNode*): VNode =
  CheckableTagImpl((checked = checked, size = size, onChange = onChange))(children*)

// Feather "x", drawn with currentColor; injected as trusted static innerHTML so it parses
// into correctly-namespaced SVG nodes.
private val TagCloseIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>"""
