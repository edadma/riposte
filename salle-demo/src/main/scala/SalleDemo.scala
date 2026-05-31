import io.github.edadma.riposte.*
import io.github.edadma.riposte.salle.*
import org.scalajs.dom

// A runnable showcase for salle. The whole point of the demo is the skin switch at
// the top: flipping it swaps every control below between salle's own look and
// DaisyUI's, with no change to the markup — the components stay identical, only the
// active Skin in context changes.

private val colors = Seq(
  Color.Primary, Color.Secondary, Color.Accent, Color.Info,
  Color.Success, Color.Warning, Color.Error, Color.Neutral,
)

private val variants = Seq(
  ButtonVariant.Solid, ButtonVariant.Outline, ButtonVariant.Soft,
  ButtonVariant.Ghost, ButtonVariant.Dash, ButtonVariant.Link,
)

private val sizes = Seq(Size.Xs, Size.Sm, Size.Md, Size.Lg, Size.Xl)

private val countries = Seq(
  Opt("us", "United States"),
  Opt("ca", "Canada"),
  Opt("mx", "Mexico"),
  Opt("br", "Brazil"),
  Opt("xx", "Unavailable", disabled = true),
)

// A handful of public-domain Picsum images at a wallpaper-ish ratio, to show the grid,
// lazy-load, badge and hover-overlay slots of ImageCard. The `seed` keeps each tile
// stable across reloads.
private val wallpapers = Seq(
  ("forest", "Forest ridge", "4K"),
  ("ocean", "Ocean cliffs", "5K"),
  ("desert", "Desert dunes", "4K"),
  ("city", "City at night", "8K"),
  ("aurora", "Aurora", "4K"),
  ("peaks", "Snow peaks", "6K"),
)

private def row(children: Seq[VNode]): VNode = div(cls := "demo-row", children)

private def section(title: String)(body: VNode): VNode =
  div(cls := "demo-section", h2(title), body)

private def wallpaperGrid: VNode =
  div(
    cls := "demo-grid",
    wallpapers.map { (seed, title, res) =>
      ImageCard(
        src = s"https://picsum.photos/seed/$seed/600/375",
        alt = title,
        ratio = "16/10",
        badge = Some(span(cls := "demo-res-badge", res)),
        overlay = Some(
          div(
            cls := "demo-card-actions",
            span(cls := "demo-card-title", title),
            Button("Download", color = Color.Primary, size = Size.Sm),
          ),
        ),
      )
    },
  )

// A colour block used as Col / Masonry content so the layout is visible at a glance.
private def block(label: String, height: String = ""): VNode =
  div(
    cls := "demo-block",
    style := (if height.isEmpty then Map.empty[String, String] else Map("height" -> height)),
    label,
  )

// The 24-column Row/Col system: a plain split, a gutter, an offset, and responsive
// columns (resize the window — xs full, sm half, md a third, lg a quarter).
private def gridDemo: VNode =
  div(
    cls := "demo-grid-stack",
    Row(gutterX = 16)(
      Col(span = 6)(block("span 6")),
      Col(span = 6)(block("span 6")),
      Col(span = 6)(block("span 6")),
      Col(span = 6)(block("span 6")),
    ),
    Row(gutterX = 16)(
      Col(span = 8)(block("span 8")),
      Col(span = 8, offset = 8)(block("span 8, offset 8")),
    ),
    Row(gutterX = 16)(
      Col(xs = 24, sm = 12, md = 8, lg = 6)(block("responsive")),
      Col(xs = 24, sm = 12, md = 8, lg = 6)(block("responsive")),
      Col(xs = 24, sm = 12, md = 8, lg = 6)(block("responsive")),
      Col(xs = 24, sm = 12, md = 8, lg = 6)(block("responsive")),
    ),
  )

// The measured masonry: blocks of varied heights pack into the shortest column.
private val masonryHeights = Seq("80px", "140px", "100px", "180px", "120px", "160px", "90px", "150px")

private def masonryDemo: VNode =
  Masonry(columns = 4, gap = 12)(
    masonryHeights.zipWithIndex.map { (h, i) =>
      block(s"#${i + 1}", h)
    }*,
  )

// The loading placeholders: an image tile, a paragraph of text lines, a circle (avatar),
// and a row of grid-cell SkeletonImages — what the gallery shows before thumbnails arrive.
private def skeletonDemo: VNode =
  div(
    cls := "demo-grid-stack",
    div(
      style := Map("display" -> "flex", "gap" -> "1rem", "align-items" -> "flex-start"),
      div(style := Map("flex" -> "0 0 12rem"), SkeletonImage(ratio = "16/10")),
      div(
        style := Map("flex" -> "1"),
        SkeletonText(lines = 4),
      ),
      Skeleton(width = "3.5rem", height = "3.5rem", circle = true),
    ),
    div(
      cls := "demo-grid",
      Seq.fill(4)(SkeletonImage(ratio = "16/10")),
    ),
  )

// Badges (resolution + "New"), variants, dismissible category Tags, and a row of
// CheckableTag filter chips that remember which are on.
private val badgeDemo = view {
  val (filters, setFilters, _) = useState(Set("Nature"))
  def chip(name: String): VNode =
    CheckableTag(checked = filters.contains(name), onChange = on =>
      setFilters(if on then filters + name else filters - name),
    )(name)
  div(
    cls := "demo-grid-stack",
    row(
      Seq(
        Badge(color = Color.Primary)("4K"),
        Badge(color = Color.Accent)("8K"),
        Badge(color = Color.Success, pill = true)("New"),
        Badge(color = Color.Error, dot = true)(),
      ),
    ),
    row(
      Seq(
        Badge(color = Color.Info, variant = BadgeVariant.Solid)("solid"),
        Badge(color = Color.Info, variant = BadgeVariant.Outline)("outline"),
        Badge(color = Color.Info, variant = BadgeVariant.Soft)("soft"),
        Badge(color = Color.Info, variant = BadgeVariant.Dash)("dash"),
      ),
    ),
    row(
      Seq(
        Tag(color = Color.Neutral, closable = true)("Landscape"),
        Tag(color = Color.Primary, closable = true)("Minimal"),
        Tag(color = Color.Accent, variant = BadgeVariant.Soft, closable = true)("Dark"),
      ),
    ),
    row(Seq("Nature", "Abstract", "Space", "City").map(chip)),
  )
}

// A view because the Modal is controlled — it needs local open/close state. The dialog
// portals to document.body, fades its scrim and slides its box in via usePresence, and
// closes on the button, the scrim, or Escape.
private val modalDemo = view {
  val (open, setOpen, _) = useState(false)
  div(
    Button("Open dialog", color = Color.Primary, onClick = () => setOpen(true)),
    Modal(
      open = open,
      onClose = () => setOpen(false),
      title = Some("Wallpaper details"),
      footer = Some(
        fragment(
          Button("Cancel", variant = ButtonVariant.Ghost, onClick = () => setOpen(false)),
          Button("Download", color = Color.Primary, onClick = () => setOpen(false)),
        ),
      ),
    )(
      p(
        "This dialog renders through a portal into document.body, fades its scrim and ",
        "slides its box in, traps focus inside, and closes on the button, the scrim, or Escape.",
      ),
    ),
  )
}

private def showcase: VNode =
  div(
    cls := "demo-stage",
    section("Button — colours")(
      row(colors.map(c => Button(c.toString, color = c))),
    ),
    section("Button — variants (primary)")(
      row(variants.map(v => Button(v.toString, color = Color.Primary, variant = v))),
    ),
    section("Button — sizes")(
      row(sizes.map(s => Button(s.toString, color = Color.Primary, size = s))),
    ),
    section("Input")(
      row(
        Seq(
          Input(placeholder = "Normal"),
          Input(placeholder = "Success", color = Color.Success),
          Input(placeholder = "Invalid", invalid = true),
          Input(placeholder = "Disabled", disabled = true),
        ),
      ),
    ),
    section("Select")(
      row(
        Seq(
          Select(countries, placeholder = "Pick a country"),
          Select(countries, defaultValue = "ca"),
          Select(countries, clearable = true, defaultValue = "us"),
          Select(countries, color = Color.Primary, defaultValue = "br"),
          Select(countries, size = Size.Sm),
          Select(countries, disabled = true, defaultValue = "mx"),
          Select(countries, invalid = true),
        ),
      ),
    ),
    section("Checkbox")(
      row(
        Seq(
          Checkbox(label = "Default", defaultChecked = true),
          Checkbox(label = "Success", color = Color.Success, defaultChecked = true),
          Checkbox(label = "Accent", color = Color.Accent),
          Checkbox(label = "Disabled", disabled = true),
        ),
      ),
    ),
    section("Toggle")(
      row(
        Seq(
          Toggle(label = "Off"),
          Toggle(label = "On", defaultChecked = true),
          Toggle(label = "Success", color = Color.Success, defaultChecked = true),
          Toggle(label = "Accent", color = Color.Accent, defaultChecked = true),
        ),
      ),
    ),
    section("ImageCard — wallpaper grid (lazy-loaded, hover for actions)")(
      wallpaperGrid,
    ),
    section("Grid — 24-column Row / Col (resize for responsive)")(
      gridDemo,
    ),
    section("Masonry — measured shortest-column packing")(
      masonryDemo,
    ),
    section("Skeleton — loading placeholders (image, text, avatar, grid)")(
      skeletonDemo,
    ),
    section("Badge / Tag — labels, dismissible tags, filter chips")(
      badgeDemo(),
    ),
    section("Modal — dialog over a scrim (portalled, animated)")(
      modalDemo(),
    ),
  )

private val App = view {
  val (skin, setSkin, _) = useState[Skin](SalleSkin)
  val daisy              = skin == DaisySkin
  div(
    cls := "demo-root",
    h1("salle"),
    p("A component library for riposte — the same components, a swappable skin."),
    div(
      cls := "demo-toolbar",
      span(cls := "demo-skin-label", s"Active skin: ${if daisy then "DaisyUI" else "salle (native)"}"),
      Button(
        if daisy then "Use salle skin" else "Use DaisyUI skin",
        variant = ButtonVariant.Outline,
        onClick = () => setSkin(if daisy then SalleSkin else DaisySkin),
      ),
      ThemeToggle(),
      ThemeSelect(Seq("system", "light", "dark")),
    ),
    SkinProvider(skin)(showcase),
  )
}

object SalleDemo:
  def main(args: Array[String]): Unit =
    val el = dom.document.getElementById("app")
    if el != null then render(App(), el)
