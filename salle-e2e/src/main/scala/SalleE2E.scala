import io.github.edadma.riposte.*
import io.github.edadma.riposte.salle.*
import org.scalajs.dom

// The Playwright harness app. Unlike the showcase demo (which crams every component onto
// one page), this mounts exactly ONE component fixture, chosen by the page's `?case=` query
// param, so a spec gets an isolated, unambiguous DOM. `?skin=daisy` re-skins the fixture;
// the default is the native SalleSkin (no network needed). Each fixture gives its trigger
// elements stable ids so the specs select them deterministically.
//
// Build:  sbt salleE2E/fastLinkJS   (emits target/.../salle-e2e-fastopt/main.js)
// Run:    npx playwright test       (see playwright.config.ts — it serves the repo root)

object SalleE2E:
  def main(args: Array[String]): Unit =
    val params       = new dom.URLSearchParams(dom.window.location.search)
    val caseName     = Option(params.get("case")).getOrElse("index")
    val skin: Skin   = if Option(params.get("skin")).contains("daisy") then DaisySkin else SalleSkin
    val el           = dom.document.getElementById("app")
    if el != null then render(SkinProvider(skin)(fixtureFor(caseName)), el)

  private def fixtureFor(name: String): VNode = name match
    case "tooltip"   => tooltipFixture()
    case "tabs"      => tabsFixture()
    case "modal"     => modalFixture()
    case "dropdown"  => dropdownFixture()
    case "imagecard" => imageCardFixture()
    case "lightbox"  => lightboxFixture()
    case "navbar"    => navbarFixture()
    case "drawer"    => drawerFixture()
    case _           => indexFixture

  // ---- tooltip: real hover/focus timing (the jsdom specs fake the Timers seam) ----------
  private val tooltipFixture = view {
    div(
      id := "harness",
      style := Map("padding" -> "4rem"),
      Tooltip(tip = "Helpful hint")(
        button(id := "tt-trigger", typ := "button", "Hover me"),
      ),
    )
  }

  // ---- tabs: real focus + roving tabindex under arrow keys ------------------------------
  private val tabsFixture = view {
    div(
      id := "harness",
      style := Map("padding" -> "2rem"),
      Tabs(items =
        Seq(
          Tab(key = "a", label = "Alpha", content = p(id := "panel-a", "Panel A")),
          Tab(key = "b", label = "Beta", content = p(id := "panel-b", "Panel B")),
          Tab(key = "c", label = "Gamma", content = p(id := "panel-c", "Panel C"), disabled = true),
          Tab(key = "d", label = "Delta", content = p(id := "panel-d", "Panel D")),
        ),
      ),
    )
  }

  // ---- modal: real focus move/trap/restore + Esc ---------------------------------------
  private val modalFixture = view {
    val (open, setOpen, _) = useState(false)
    div(
      id := "harness",
      style := Map("padding" -> "2rem"),
      button(id := "open", typ := "button", onClick := (_ => setOpen(true)), "Open dialog"),
      Modal(open = open, onClose = () => setOpen(false), title = Some("Dialog title"))(
        p("Body text"),
        button(id := "field-1", typ := "button", "First"),
        button(id := "field-2", typ := "button", "Last"),
      ),
    )
  }

  // ---- dropdown: real click-outside dismissal ------------------------------------------
  private val dropdownFixture = view {
    div(
      id := "harness",
      style := Map("padding" -> "2rem"),
      Dropdown(items =
        Seq(
          MenuItem("edit", "Edit"),
          MenuItem("dup", "Duplicate"),
          MenuDivider,
          MenuItem("del", "Delete", danger = true),
        ),
      )(span("Actions")),
      p(id := "outside", "outside the menu"),
    )
  }

  // ---- imagecard: real IntersectionObserver lazy-load on scroll -------------------------
  // A 1×1 transparent PNG data URI keeps the load network-free; the card sits far below the
  // fold so it is out of view until the spec scrolls to it.
  private val pixel =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMCAQAB3m1aAAAAAElFTkSuQmCC"
  private val imageCardFixture = view {
    div(
      id := "harness",
      div(style := Map("height" -> "1500px"), "scroll down"),
      div(
        id := "card-wrap",
        style := Map("width" -> "240px"),
        ImageCard(src = pixel, alt = "test image", ratio = "1/1"),
      ),
    )
  }

  // ---- lightbox: real click-to-open, arrow-key nav, click-to-zoom, Esc, focus move ------
  // Three distinct SVG data-URI images in a preview group, so a click opens one shared,
  // navigable lightbox. Data URIs load instantly in a real browser (no network) so each
  // image becomes previewable at once.
  private def swatch(fill: String): String =
    "data:image/svg+xml," +
      s"%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='200'%20height='200'%3E" +
      s"%3Crect%20width='200'%20height='200'%20fill='$fill'/%3E%3C/svg%3E"
  private val lightboxFixture = view {
    div(
      id := "harness",
      style := Map("padding" -> "2rem", "display" -> "flex", "gap" -> "1rem"),
      ImagePreviewGroup()(
        Image(src = swatch("crimson"), alt = "L1", width = "120px"),
        Image(src = swatch("seagreen"), alt = "L2", width = "120px"),
        Image(src = swatch("steelblue"), alt = "L3", width = "120px"),
      ),
    )
  }

  // ---- navbar: real responsive collapse driven by the viewport width --------------------
  // A collapsible navbar whose center/end zones fold behind the hamburger below the 768px
  // breakpoint. matchMedia only reports a real value in a browser, so the collapse is a
  // browser-only behaviour the jsdom specs can't reach — Playwright resizes the viewport.
  private val navbarFixture = view {
    div(
      id := "harness",
      Navbar(
        start = span(id := "nav-brand", "Brand"),
        center = a(id := "nav-link", href := "#", "Gallery"),
        end = button(id := "nav-action", typ := "button", "Sign in"),
        collapsible = true,
      ),
      p(style := Map("padding" -> "1rem"), "page content below the bar"),
    )
  }

  // ---- drawer: real focus move/trap/restore + Esc + slide-in from the edge --------------
  private val drawerFixture = view {
    val (open, setOpen, _) = useState(false)
    div(
      id := "harness",
      style := Map("padding" -> "2rem"),
      button(id := "open", typ := "button", onClick := (_ => setOpen(true)), "Open drawer"),
      Drawer(
        open = open,
        onClose = () => setOpen(false),
        title = Some("Filters"),
        placement = DrawerPlacement.Right,
      )(
        p("Filter the gallery"),
        button(id := "field-1", typ := "button", "First"),
        button(id := "field-2", typ := "button", "Last"),
      ),
    )
  }

  // ---- index: a plain links page, handy when opening the harness by hand ----------------
  private val indexFixture =
    div(
      id := "harness",
      style := Map("padding" -> "2rem", "font-family" -> "system-ui, sans-serif"),
      h1("salle e2e harness"),
      p("Append ?case=<name> (and optional &skin=daisy):"),
      ul(
        li(a(href := "?case=tooltip", "tooltip")),
        li(a(href := "?case=tabs", "tabs")),
        li(a(href := "?case=modal", "modal")),
        li(a(href := "?case=dropdown", "dropdown")),
        li(a(href := "?case=imagecard", "imagecard")),
        li(a(href := "?case=lightbox", "lightbox")),
        li(a(href := "?case=navbar", "navbar")),
        li(a(href := "?case=drawer", "drawer")),
      ),
    )
