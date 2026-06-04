package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// The "nothing here" placeholder — what a gallery shows when a search or filter returns no
// results, a list is empty, or a panel has no content yet. Three stacked pieces: an
// illustration, a description line, and an optional action slot (the `children` — typically a
// button that clears the filter or adds the first item). Modelled on Ant Design's / AsterUI's
// `Empty`: a default line-art illustration is built in, with a smaller `Simple` variant, and
// either can be replaced with a custom node or hidden entirely via [[EmptyImage]].
//
// It is a status region — the root carries `role=status` and an `aria-label` (the description
// text when it is a plain string, else "Empty") so assistive tech announces the empty state.
// State is mirrored to `data-*`: `data-part=empty` on the root, with `data-part` on each piece.

/** Which illustration an [[Empty]] shows. `Default` is the full line-art drawing, `Simple` the
  * smaller minimal one (both drawn in `currentColor` so they take the surrounding text colour),
  * `Hidden` omits the illustration, and `Custom` supplies your own node (an icon, a photo). */
enum EmptyImage:
  case Default
  case Simple
  case Hidden
  case Custom(node: VNode)

// The full line-art drawing (Ant Design's default empty illustration), redrawn in `currentColor`
// with per-shape opacity for depth so it reads under any skin and inherits the text colour.
private def defaultEmptyImage: VNode =
  svg(
    width        := "184",
    height       := "152",
    viewBox      := "0 0 184 152",
    aria("hidden") := true,
    g(
      fill              := "none",
      attr("fill-rule") := "evenodd",
      g(
        transform := "translate(24 31.67)",
        ellipse(cx := "67.797", cy := "106.89", attr("rx") := "67.797", attr("ry") := "12.668", fill := "currentColor", attr("opacity") := "0.08"),
        path(d := "M122.034 69.674L98.109 40.229c-1.148-1.386-2.826-2.225-4.593-2.225h-51.44c-1.766 0-3.444.839-4.592 2.225L13.56 69.674v15.383h108.475V69.674z", fill := "currentColor", attr("opacity") := "0.1"),
        path(d := "M101.537 86.214L80.63 61.102c-1.001-1.207-2.507-1.867-4.048-1.867H31.724c-1.54 0-3.047.66-4.048 1.867L6.769 86.214v13.792h94.768V86.214z", transform := "translate(13.56)", fill := "currentColor", attr("opacity") := "0.04"),
        ellipse(cx := "67.797", cy := "106.89", attr("rx") := "67.797", attr("ry") := "12.668", fill := "currentColor", attr("opacity") := "0.08"),
        path(d := "M122.034 69.674L98.109 40.229c-1.148-1.386-2.826-2.225-4.593-2.225h-51.44c-1.766 0-3.444.839-4.592 2.225L13.56 69.674v15.383h108.475V69.674z", fill := "currentColor", attr("opacity") := "0.1"),
        path(d := "M33.83 0h67.933a4 4 0 0 1 4 4v93.344a4 4 0 0 1-4 4H33.83a4 4 0 0 1-4-4V4a4 4 0 0 1 4-4z", fill := "currentColor", attr("opacity") := "0.1"),
        path(d := "M42.678 9.953h50.237a2 2 0 0 1 2 2V36.91a2 2 0 0 1-2 2H42.678a2 2 0 0 1-2-2V11.953a2 2 0 0 1 2-2zM42.94 49.767h49.713a2.262 2.262 0 1 1 0 4.524H42.94a2.262 2.262 0 0 1 0-4.524zM42.94 61.53h49.713a2.262 2.262 0 1 1 0 4.525H42.94a2.262 2.262 0 0 1 0-4.525zM121.813 105.032c-.775 3.071-3.497 5.36-6.735 5.36H20.515c-3.238 0-5.96-2.29-6.734-5.36a7.309 7.309 0 0 1-.222-1.79V69.675h26.318c2.907 0 5.25 2.448 5.25 5.42v.04c0 2.971 2.37 5.37 5.277 5.37h34.785c2.907 0 5.277-2.421 5.277-5.393V75.1c0-2.972 2.343-5.426 5.25-5.426h26.318v33.569c0 .617-.077 1.216-.221 1.789z", fill := "currentColor", attr("opacity") := "0.04"),
      ),
      path(d := "M149.121 33.292l-6.83 2.65a1 1 0 0 1-1.317-1.23l1.937-6.207c-2.589-2.944-4.109-6.534-4.109-10.408C138.802 8.102 148.92 0 161.402 0 173.881 0 184 8.102 184 18.097c0 9.995-10.118 18.097-22.599 18.097-4.528 0-8.744-1.066-12.28-2.902z", fill := "currentColor", attr("opacity") := "0.1"),
      g(
        transform := "translate(149.65 15.383)",
        fill      := "currentColor",
        attr("opacity") := "0.04",
        ellipse(cx := "20.654", cy := "3.167", attr("rx") := "2.849", attr("ry") := "2.815"),
        path(d := "M5.698 5.63H0L2.898.704zM9.259.704h4.985V5.63H9.259z"),
      ),
    ),
  )

// The minimal variant (Ant Design's simple empty image), likewise redrawn in `currentColor`.
private def simpleEmptyImage: VNode =
  svg(
    width        := "64",
    height       := "41",
    viewBox      := "0 0 64 41",
    aria("hidden") := true,
    g(
      transform         := "translate(0 1)",
      fill              := "none",
      attr("fill-rule") := "evenodd",
      ellipse(cx := "32", cy := "33", attr("rx") := "32", attr("ry") := "7", fill := "currentColor", attr("opacity") := "0.08"),
      g(
        attr("fill-rule")    := "nonzero",
        stroke               := "currentColor",
        attr("stroke-opacity") := "0.25",
        path(d := "M55 12.76L44.854 1.258C44.367.474 43.656 0 42.907 0H21.093c-.749 0-1.46.474-1.947 1.257L9 12.761V22h46v-9.24z"),
        path(d := "M41.613 15.931c0-1.605.994-2.93 2.227-2.931H55v18.137C55 33.26 53.68 35 52.05 35h-40.1C10.32 35 9 33.259 9 31.137V13h11.16c1.233 0 2.227 1.323 2.227 2.928v.022c0 1.605 1.005 2.901 2.237 2.901h14.752c1.232 0 2.237-1.308 2.237-2.913v-.007z", fill := "currentColor", attr("fill-opacity") := "0.1"),
      ),
    ),
  )

private val EmptyImpl =
  container[(image: EmptyImage, description: Option[VNode])] { (p, children) =>
    val skin  = useSkin()
    val parts = skin.empty

    val imageNode: Option[VNode] = p.image match
      case EmptyImage.Default       => Some(defaultEmptyImage)
      case EmptyImage.Simple        => Some(simpleEmptyImage)
      case EmptyImage.Hidden        => None
      case EmptyImage.Custom(node)  => Some(node)

    // No description supplied falls back to a neutral "No data" line, like AntD's default.
    val descNode: VNode = p.description.getOrElse("No data": VNode)

    // Name the region for assistive tech: the description text when it is a plain string,
    // else a generic label.
    val ariaText = p.description match
      case Some(_) => ""
      case None    => "No data"

    val imageMod: Mod = imageNode match
      case Some(node) => div(cls := parts.image, data("part") := "image", node)
      case None       => NoMod

    val footerMod: Mod =
      if children.isEmpty then NoMod
      else div(cls := parts.footer, data("part") := "footer", children)

    div(
      cls           := parts.root,
      role          := "status",
      aria("label") := (if ariaText.nonEmpty then ariaText else "Empty"),
      data("part")  := "empty",
      imageMod,
      div(cls := parts.description, data("part") := "description", descNode),
      footerMod,
    )
  }

/** The empty-state placeholder: an illustration, a `description` line, and an optional action
  * slot (the `children`, e.g. a button to clear a filter). `image` chooses the illustration
  * ([[EmptyImage]] — `Default`, `Simple`, `Hidden`, or `Custom`); `description` overrides the
  * default "No data" text with your own node. The root is a `role=status` region labelled by the
  * description; classes come from the active [[Skin]] and the structure is mirrored to `data-*`.
  *
  * {{{ Empty(description = Some(view { "No wallpapers match your filters" }))(
  *   Button(label = "Clear filters", onClick = _ => reset()),
  * ) }}} */
def Empty(
    image:       EmptyImage     = EmptyImage.Default,
    description: Option[VNode] = None,
)(children: VNode*): VNode =
  EmptyImpl((image = image, description = description))(children*)
