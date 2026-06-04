package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*

// A full-bleed banner — the headline-over-a-photo block at the top of a landing page, the empty
// state of a section, a call-to-action strip. Wraps its `children` in a centred content box; give
// it a `bgImage` for a photographic backdrop and an `overlay` to dim that backdrop so the text
// stays legible. `minHeight` sizes the band (a viewport-height hero is `Some("100vh")`).
//
// Structure is mirrored to `data-*`: the root carries `data-part=hero` (and `data-overlay` when
// the scrim is on); the scrim `data-part=overlay`; the content box `data-part=content`. Classes
// come from the active [[Skin]].

private val HeroImpl =
  container[(
      overlay:   Boolean,
      bgImage:   Option[String],
      minHeight: Option[String],
  )] { (p, children) =>
    val skin  = useSkin()
    val parts = skin.hero

    // The backdrop image and band height are content, not a skin concern, so they ride inline
    // style; the skin styles the structure around them.
    val rootStyle: Map[String, String] =
      val withBg = p.bgImage match
        case Some(url) =>
          Map(
            "background-image"    -> s"url($url)",
            "background-size"     -> "cover",
            "background-position" -> "center",
          )
        case None => Map.empty[String, String]
      p.minHeight match
        case Some(h) => withBg + ("min-height" -> h)
        case None    => withBg

    val overlayMod: Mod =
      if p.overlay then div(cls := parts.overlay, data("part") := "overlay", aria("hidden") := true)
      else NoMod

    div(
      cls            := parts.root,
      data("part")   := "hero",
      data("overlay") := p.overlay,
      style          := rootStyle,
      overlayMod,
      div(cls := parts.content, data("part") := "content", children),
    )
  }

/** A full-bleed banner wrapping its `children` in a centred content box. `bgImage` sets a
  * photographic backdrop (cover-positioned); `overlay` dims it with a scrim so foreground text
  * stays legible; `minHeight` sizes the band (e.g. `Some("100vh")` for a viewport-tall hero).
  * Classes come from the active [[Skin]]; structure is mirrored to `data-*`.
  *
  * {{{ Hero(bgImage = Some("/banner.jpg"), overlay = true, minHeight = Some("60vh"))(
  *   h1("Wallpapers for every screen"),
  *   Button("Browse the gallery"),
  * ) }}} */
def Hero(
    overlay:   Boolean        = false,
    bgImage:   Option[String] = None,
    minHeight: Option[String] = None,
)(children: VNode*): VNode =
  HeroImpl(
    (
      overlay = overlay,
      bgImage = bgImage,
      minHeight = minHeight,
    ),
  )(children*)
