package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// The page shell: the structural frame a site lays its content into. Three pieces, each a
// thin styled wrapper over a landmark element so the look comes from the active [[Skin]] and
// the structure stays semantic and accessible:
//
//   - [[Layout]] — the flex container. Stacks its regions vertically (header / content /
//     footer) by default, or lays them in a row when it `hasSider`, so a sidebar sits beside
//     the content. Compose them: a vertical outer `Layout` with a `Header`, a horizontal inner
//     `Layout(hasSider = true)` holding a `Sider` and `Content`, then a `Footer`.
//   - [[Navbar]] — the top navigation bar: a `start` brand zone, a `center` zone, and an `end`
//     actions zone, with an optional responsive collapse that hides the center/end behind a
//     hamburger below a breakpoint.
//   - [[Footer]] — the page-foot content block (link columns, copyright), with a [[Footer.Title]]
//     for each column heading. Distinct from [[Layout.Footer]], which is just the bottom *band*;
//     a content `Footer` is what you put inside it.
//
// Everything mirrors its state to `data-*` (`data-part`, `data-has-sider`, `data-collapsed`,
// `data-narrow`, `data-open`) so specs and consumers select on stable, skin-independent hooks,
// and every region carries the right ARIA landmark role.

/** The colour treatment of a [[Layout.Sider]]: `Dark` (the default — a deeper surface that
  * reads as a distinct sidebar) or `Light` (a surface matching the content area). */
enum SiderTheme:
  case Light, Dark

  /** The lowercase modifier token (`"dark"`). */
  def token: String = toString.toLowerCase

/** The page-shell frame and its regions. [[Layout]] itself is the flex container; its
  * `Header`, `Content`, `Footer`, and `Sider` members are the regions that go inside it. Each
  * is a styled landmark element whose classes come from the active [[Skin]]. */
object Layout:
  private val RootImpl =
    container[(hasSider: Boolean)] { (p, children) =>
      val skin  = useSkin()
      val parts = skin.layout(p.hasSider)
      div(
        cls               := parts.root,
        data("part")      := "layout",
        data("has-sider") := p.hasSider,
        children,
      )
    }

  /** The layout container. By default it stacks its children in a column (the usual
    * header / content / footer order); pass `hasSider = true` to lay them in a row so a
    * [[Sider]] sits beside the [[Content]]. Nest layouts to mix both axes on one page. */
  def apply(hasSider: Boolean = false)(children: VNode*): VNode =
    RootImpl((hasSider = hasSider))(children*)

  private val HeaderImpl =
    container { children =>
      val skin = useSkin()
      header(cls := skin.layout(false).header, role := "banner", data("part") := "header", children)
    }

  /** The top band of the layout — a `<header>` landmark (`role="banner"`). Put the
    * [[Navbar]] (or a brand + nav of your own) here. */
  def Header(children: VNode*): VNode = HeaderImpl(children*)

  private val ContentImpl =
    container { children =>
      val skin = useSkin()
      mainTag(cls := skin.layout(false).content, data("part") := "content", children)
    }

  /** The main content region — a `<main>` element that grows to fill the space left by the
    * header, footer, and any sider, and scrolls its overflow. */
  def Content(children: VNode*): VNode = ContentImpl(children*)

  private val FooterImpl =
    container { children =>
      val skin = useSkin()
      footer(cls := skin.layout(false).footer, role := "contentinfo", data("part") := "layout-footer", children)
    }

  /** The bottom band of the layout — a `<footer>` landmark (`role="contentinfo"`). For a
    * multi-column footer with headings, put a content [[Footer]] inside this band. */
  def Footer(children: VNode*): VNode = FooterImpl(children*)

  private val SiderImpl =
    container[
      (
          width: String,
          collapsedWidth: String,
          collapsed: Option[Boolean],
          defaultCollapsed: Boolean,
          collapsible: Boolean,
          breakpoint: Option[String],
          reverseArrow: Boolean,
          theme: SiderTheme,
          onCollapse: Boolean => Unit,
      ),
    ] { (p, children) =>
      val skin                      = useSkin()
      val parts                     = skin.sider(p.theme)
      val (collapsed, setCollapsed) = useControllable(p.collapsed, p.defaultCollapsed, p.onCollapse)

      // Responsive auto-collapse: watch the breakpoint (a query that never matches when none is
      // given, so the hook is still called unconditionally) and fold the sider when it is hit —
      // but only while uncontrolled, so a caller that owns `collapsed` stays in charge.
      val broken = useMediaQuery(p.breakpoint.getOrElse("not all"))
      useEffect(
        () =>
          if p.breakpoint.isDefined && p.collapsed.isEmpty then setCollapsed(broken)
          noCleanup
        ,
        Array(broken),
      )

      val triggerNode: Mod =
        if p.collapsible then
          // The arrow points the way a click will move the edge: toward the content when expanded
          // (collapse), away from it when collapsed (expand); `reverseArrow` flips it for a sider
          // docked on the right.
          val pointRight = collapsed != p.reverseArrow
          button(
            cls           := parts.trigger,
            typ           := "button",
            data("part")  := "sider-trigger",
            aria("label") := (if collapsed then "Expand sidebar" else "Collapse sidebar"),
            onClick       := (_ => setCollapsed(!collapsed)),
            unsafeHtml(if pointRight then SiderChevronRight else SiderChevronLeft),
          )
        else NoMod

      aside(
        cls                  := parts.root,
        data("part")         := "sider",
        data("collapsed")    := collapsed,
        data("sider-theme")  := p.theme.token,
        aria("expanded")     := !collapsed,
        style                := Map("width" -> (if collapsed then p.collapsedWidth else p.width)),
        div(cls := parts.inner, data("part") := "sider-inner", children),
        triggerNode,
      )
    }

  /** A collapsible sidebar region, sized in CSS lengths (`"200px"`, `"16rem"`). Collapse is
    * controllable — pass `collapsed` to own it (told of intent through `onCollapse`) — or
    * self-managed from `defaultCollapsed`. `collapsible` shows a trigger button that toggles
    * it; `collapsedWidth` is the folded width; `breakpoint` (a media query) auto-folds it on
    * small screens while uncontrolled; `reverseArrow` flips the trigger glyph for a
    * right-docked sider; `theme` ([[SiderTheme]]) picks the surface treatment. Place it as a
    * direct child of a `Layout(hasSider = true)`, beside the [[Content]]. */
  def Sider(
      width:            String          = "200px",
      collapsedWidth:   String          = "80px",
      collapsed:        Option[Boolean] = None,
      defaultCollapsed: Boolean         = false,
      collapsible:      Boolean         = false,
      breakpoint:       Option[String]  = None,
      reverseArrow:     Boolean         = false,
      theme:            SiderTheme      = SiderTheme.Dark,
      onCollapse:       Boolean => Unit = _ => (),
  )(children: VNode*): VNode =
    SiderImpl(
      (
        width = width,
        collapsedWidth = collapsedWidth,
        collapsed = collapsed,
        defaultCollapsed = defaultCollapsed,
        collapsible = collapsible,
        breakpoint = breakpoint,
        reverseArrow = reverseArrow,
        theme = theme,
        onCollapse = onCollapse,
      ),
    )(children*)

/** The drop-shadow depth of a [[Navbar]], mirroring the usual `none`/`sm`/`md`/`lg`/`xl`
  * scale. The token doubles as the Tailwind class under [[DaisySkin]] (`Sm` → `shadow-sm`). */
enum NavbarShadow:
  case None, Sm, Md, Lg, Xl

  /** The shadow class token (`"shadow-sm"`); empty for `None`. */
  def token: String = this match
    case None => ""
    case s    => "shadow-" + s.toString.toLowerCase

/** The corner radius of a [[Navbar]]. The token doubles as the Tailwind class under
  * [[DaisySkin]] (`Full` → `rounded-full`). */
enum NavbarRounded:
  case None, Sm, Md, Lg, Xl, Full

  /** The radius class token (`"rounded-lg"`); empty for `None`. */
  def token: String = this match
    case None => ""
    case r    => "rounded-" + r.toString.toLowerCase

private val NavbarImpl =
  component[
    (
        start: VNode,
        center: VNode,
        end: VNode,
        color: Color,
        sticky: Boolean,
        shadow: NavbarShadow,
        rounded: NavbarRounded,
        collapsible: Boolean,
        breakpoint: String,
    ),
  ] { p =>
    val skin               = useSkin()
    val parts              = skin.navbar(p.color, p.sticky, p.shadow, p.rounded)
    val (open, setOpen, _) = useState(false)

    // Below the breakpoint the bar is "narrow" and (when collapsible) the center/end zones fold
    // behind the toggle. matchMedia is absent in jsdom, so `narrow` is false there — the bar
    // stays in its full-width form for unit tests; the collapse is exercised in the browser e2e.
    val narrow = useMediaQuery(p.breakpoint)
    useEffect(
      () =>
        if !narrow then setOpen(false)
        noCleanup
      ,
      Array(narrow),
    )

    val toggleNode: Mod =
      if p.collapsible then
        button(
          cls              := parts.toggle,
          typ              := "button",
          data("part")     := "toggle",
          aria("label")    := "Toggle navigation menu",
          aria("expanded") := open,
          onClick          := (_ => setOpen(!open)),
          unsafeHtml(NavbarMenuIcon),
        )
      else NoMod

    nav(
      cls                 := parts.root,
      role                := "navigation",
      data("part")        := "navbar",
      data("narrow")      := narrow,
      data("open")        := open,
      data("collapsible") := p.collapsible,
      data("sticky")      := p.sticky,
      div(cls := parts.start, data("part") := "start", p.start),
      toggleNode,
      div(cls := parts.center, data("part") := "center", p.center),
      div(cls := parts.end, data("part") := "end", p.end),
    )
  }

/** The top navigation bar — a `<nav>` landmark with three zones: `start` (the brand/logo),
  * `center`, and `end` (actions). `color` tints the bar ([[Color]]), `sticky` pins it to the
  * top of the viewport, `shadow` ([[NavbarShadow]]) and `rounded` ([[NavbarRounded]]) shape it.
  * When `collapsible`, a hamburger toggle appears below `breakpoint` (a media query) and the
  * `center`/`end` zones fold behind it until opened; in environments without `matchMedia`
  * (jsdom) the bar stays full-width. Classes come from the active [[Skin]]; state is mirrored
  * to `data-*`. */
def Navbar(
    start:       VNode         = VEmpty,
    center:      VNode         = VEmpty,
    end:         VNode         = VEmpty,
    color:       Color         = Color.Default,
    sticky:      Boolean       = false,
    shadow:      NavbarShadow  = NavbarShadow.None,
    rounded:     NavbarRounded = NavbarRounded.None,
    collapsible: Boolean       = false,
    breakpoint:  String        = "(max-width: 768px)",
): VNode =
  NavbarImpl(
    (
      start = start,
      center = center,
      end = end,
      color = color,
      sticky = sticky,
      shadow = shadow,
      rounded = rounded,
      collapsible = collapsible,
      breakpoint = breakpoint,
    ),
  )

/** The page-foot content block: a `<footer>` of link columns, copyright, and the like, with a
  * [[Footer.Title]] for each column heading. `center` centres the content; `horizontal` lays
  * the columns in a row (versus stacked). This is the *content* footer — to anchor it to the
  * bottom of a page shell, put it inside a [[Layout.Footer]] band. */
object Footer:
  private val RootImpl =
    container[(center: Boolean, horizontal: Boolean)] { (p, children) =>
      val skin  = useSkin()
      val parts = skin.footer(p.center, p.horizontal)
      footer(
        cls                := parts.root,
        data("part")       := "footer",
        data("center")     := p.center,
        data("horizontal") := p.horizontal,
        children,
      )
    }

  /** The content footer. `center` centres the columns; `horizontal` lays them in a row. */
  def apply(center: Boolean = false, horizontal: Boolean = false)(children: VNode*): VNode =
    RootImpl((center = center, horizontal = horizontal))(children*)

  private val TitleImpl =
    container { children =>
      val skin = useSkin()
      h6(cls := skin.footer(false, false).title, data("part") := "footer-title", children)
    }

  /** A column heading inside a [[Footer]] — an `<h6>` styled as the section label. */
  def Title(children: VNode*): VNode = TitleImpl(children*)

// Feather "menu" (the hamburger), drawn with currentColor; injected as trusted static
// innerHTML so it parses into correctly-namespaced SVG nodes.
private val NavbarMenuIcon =
  """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>"""

// Feather "chevron-left"/"chevron-right" for the sider's collapse trigger.
private val SiderChevronLeft =
  """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"></polyline></svg>"""

private val SiderChevronRight =
  """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"></polyline></svg>"""
