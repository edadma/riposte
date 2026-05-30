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
