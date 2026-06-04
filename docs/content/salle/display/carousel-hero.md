---
title: "Carousel & Hero"
weight: 8
---

Two featured-banner pieces: a `Carousel` that rotates through full-width slides, and a `Hero`
full-bleed band for a headline over a photo.

## Carousel

A rotating strip of slides — the featured-wallpaper banner on a landing page, a product
gallery, an onboarding sequence. Data-driven like [`Tabs`](/salle/navigation/tabs/): pass the
`slides` and salle renders a `role=region` carousel that advances between them, with arrows,
indicator dots, keyboard, autoplay, and pointer-swipe:

```scala
Carousel(
  slides = Seq(
    Image(src = "/a.jpg", alt = "A"),
    Image(src = "/b.jpg", alt = "B"),
  ),
  autoplay = true,
  effect = CarouselEffect.Fade,   // Scrollx (default) | Fade
)
```

The current slide is controllable via `activeIndex` (told through `onChange`) or self-managed
from `defaultActiveIndex`. `effect` chooses slide-vs-cross-fade; `infinite` (default) wraps
past the ends (and is what autoplay rides; without it the arrows disable at the first/last
slide); `vertical` stacks the slides; `arrows`/`dots` toggle the controls; `autoplay`
advances every `autoplaySpeed` ms (pausing on hover when `pauseOnHover`); `speed` is the
transition duration.

It's fully keyboard-driven (Left/Right, or Up/Down when `vertical`, with the region focused)
and swipeable. Accessibility: `role=region` + `aria-roledescription=carousel`, an
`aria-live=polite` track, per-slide `role=group` with `aria-label="Slide n of N"`, and a
`role=tablist` of dots. State is mirrored to `data-*` (`data-active-index`/`data-effect`/
`data-vertical` on the root; `data-active` per slide and dot).

## Hero

A full-bleed banner wrapping its `children` in a centred content box — the headline-over-a-
photo block at the top of a landing page, or a call-to-action strip:

```scala
Hero(bgImage = Some("/banner.jpg"), overlay = true, minHeight = Some("60vh"))(
  h1("Wallpapers for every screen"),
  Button("Browse the gallery"),
)
```

`bgImage` sets a cover-positioned photographic backdrop; `overlay` dims it with a scrim so
foreground text stays legible; `minHeight` sizes the band (e.g. `Some("100vh")` for a
viewport-tall hero). Structure is mirrored to `data-*` (`data-part` on the root, scrim, and
content; `data-overlay` when the scrim is on).
