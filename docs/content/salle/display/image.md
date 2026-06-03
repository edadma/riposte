---
title: "Image & Lightbox"
weight: 2
---

A previewable image and the full-size viewer it opens. `Image` loads a source (fading in
on load, falling back and then to an error placeholder if it fails) and, when `preview` is
on, becomes clickable — a click (or Enter/Space) opens a `Lightbox`: a single image floated
above the page over a dimming scrim.

```scala
Image(
  src = thumbUrl,
  alt = "Sunset over the bay",
  fit = ImageFit.Cover,   // Cover | Contain | Fill | ScaleDown
  width = "240px",        // optional CSS sizes
  rounded = true,
)
```

`fallback` is tried if `src` fails; `preview = false` makes the image non-interactive;
`width`/`height` are optional CSS sizes; `rounded` rounds the corners. State is mirrored to
`data-state` (`loading`/`loaded`/`error`).

## The lightbox

The viewer is rendered through a [`portal`](/guide/components/) into `document.body`, so it
escapes any `overflow`/`transform`/`z-index` trap of the gallery that opened it. It mounts
and fades in on open, fades out and *then* unmounts on close (via
[`usePresence`](/guide/hooks/)), moves focus into the overlay on open and restores it to the
opener on close. Escape closes; with more than one image, ArrowLeft/ArrowRight navigate.

`Lightbox` is *controlled* — you own `open` and close on `onClose` — and is what `Image`
drives for you. Reach for it directly to float your own set:

```scala
val (open, setOpen, _) = useState(false)

button(onClick := (_ => setOpen(true)), "View gallery")
Lightbox(
  open = open,
  items = Vector(
    Preview("/a.jpg", "First"),
    Preview("/b.jpg", "Second"),
  ),
  onClose = () => setOpen(false),
)
```

`items` is a `Vector[PreviewItem]` (build one with `Preview(src, alt)`); the shown image is
`index` (controlled) or `defaultIndex` (uncontrolled), reported through `onIndexChange`.
With more than one item it shows previous/next controls and an `n / total` counter, and
arrows navigate — wrapping past the ends when `loop` (default), clamping otherwise.
`zoomable` (default) lets a click on the image toggle a zoomed view; `closeOnEsc` and
`maskClosable` (both default on) govern the dismiss gestures; `exitMs` is the close-fade
duration. Lifecycle state is mirrored to `data-state` (`enter`/`open`/`exit`) and each
control carries a stable `data-part` (`close`/`prev`/`next`/`counter`/`image`).

## A shared viewer across a gallery

`ImagePreviewGroup` wires a set of `Image`s into one navigable lightbox: clicking any
previewable image opens a single shared viewer positioned at that image, with previous/next
stepping through them all in mount order.

```scala
ImagePreviewGroup() (
  Image(src = "/a.jpg", alt = "First"),
  Image(src = "/b.jpg", alt = "Second"),
  Image(src = "/c.jpg", alt = "Third"),
)
```

`zoomable`, `loop`, and `exitMs` are forwarded to the shared lightbox. Outside a group, each
`Image` opens its own one-image lightbox instead.
