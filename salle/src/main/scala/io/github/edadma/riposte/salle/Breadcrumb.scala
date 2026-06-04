package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// The trail that tells you where a page sits in the hierarchy — Home › Nature › Forests — and
// lets you jump back up it. Data-driven like [[Tabs]] and [[Segmented]]: pass the `items` (each a
// label, an optional `href` or `onClick`, and an optional leading icon) and salle renders the
// `nav` landmark, the ordered list, the links, and the separators. The last item is the current
// page: it is rendered as plain text (not a link) and carries `aria-current=page`.
//
// Separators are the skin's job by default — SalleSkin draws a "/" before each item past the
// first, DaisySkin uses DaisyUI's built-in chevron. Pass a `separator` node to override it; the
// component then renders explicit separator items and tells the skin (via the `customSeparator`
// flag) to suppress its automatic one.
//
// State is mirrored to `data-*`: the root carries `data-part=breadcrumb`; each crumb
// `data-part=crumb` (with `data-current` on the last); each explicit separator `data-part=separator`.

/** One crumb in a [[Breadcrumb]] trail: `label` is its content, `href` makes it a link, `onClick`
  * a click handler (a crumb with neither is plain text — typically the current page), and `icon`
  * an optional leading slot. Build with [[Crumb]]. */
type BreadcrumbItem = (
    label: VNode,
    href: Option[String],
    onClick: Option[() => Unit],
    icon: Option[VNode],
)

/** Build a [[Breadcrumb]] crumb: a `label`, an optional `href` (renders a link), an optional
  * `onClick` (a click handler — gets a focusable link that does not navigate), and an optional
  * leading `icon`. */
def Crumb(
    label:   VNode,
    href:    Option[String]       = None,
    onClick: Option[() => Unit]   = None,
    icon:    Option[VNode]        = None,
): BreadcrumbItem =
  (label = label, href = href, onClick = onClick, icon = icon)

private val BreadcrumbImpl =
  component[(
      items:     Vector[BreadcrumbItem],
      separator: Option[VNode],
      ariaLabel: String,
  )] { p =>
    val skin   = useSkin()
    val custom = p.separator.isDefined
    val parts  = skin.breadcrumb(custom)
    val items  = p.items
    val last   = items.length - 1

    val crumbNodes: Seq[VNode] = items.zipWithIndex.flatMap { (it, i) =>
      val isLast = i == last

      val content: VNode = it.icon match
        case Some(ic) => span(cls := parts.label, span(cls := parts.icon, aria("hidden") := true, ic), it.label)
        case None     => it.label

      // A crumb links when it has an href or an onClick; the current page (last) is plain text.
      val inner: VNode =
        if isLast then content
        else
          (it.href, it.onClick) match
            case (Some(h), oc) =>
              a(cls := parts.link, href := h, oc.map(f => onClick := ((_: dom.MouseEvent) => f())).getOrElse(NoMod), content)
            case (None, Some(f)) =>
              // No destination, just an action: keep it focusable but stop the "#" from navigating.
              a(cls := parts.link, href := "#", onClick := ((e: dom.MouseEvent) => { e.preventDefault(); f() }), content)
            case (None, None) =>
              content

      val crumb = li(
        cls            := parts.item,
        data("part")   := "crumb",
        data("current") := isLast,
        (if isLast then aria("current") := "page" else NoMod),
        inner,
      )

      if custom && !isLast then
        Seq(
          crumb,
          li(cls := parts.separator, data("part") := "separator", aria("hidden") := true, p.separator.get),
        )
      else Seq(crumb)
    }

    nav(
      cls              := parts.root,
      aria("label")    := p.ariaLabel,
      data("part")     := "breadcrumb",
      data("custom-sep") := custom,
      ul(crumbNodes),
    )
  }

/** A breadcrumb trail: pass the `items` ([[Crumb]]) and salle renders a `nav` landmark wrapping an
  * ordered list of crumbs. The last item is the current page (plain text, `aria-current=page`);
  * earlier items with an `href`/`onClick` are links. By default the active [[Skin]] draws the
  * separators; pass `separator` to render your own between every pair. `ariaLabel` names the
  * landmark. Classes come from the skin; structure is mirrored to `data-*`. */
def Breadcrumb(
    items:     Seq[BreadcrumbItem],
    separator: Option[VNode]      = None,
    ariaLabel: String             = "Breadcrumb",
): VNode =
  BreadcrumbImpl((items = items.toVector, separator = separator, ariaLabel = ariaLabel))
