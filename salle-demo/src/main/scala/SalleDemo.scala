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
