---
title: "Toast & Toaster"
weight: 3
---

Transient notifications, created **imperatively** — exactly what a "Downloaded" or "Added to
favourites" handler wants, rather than threading open-state through the view tree. Mount a
single `Toaster()` once anywhere in the tree, then call the `toast` API from anywhere:

```scala
// Once, near the root:
div(App(), Toaster())

// Anywhere, imperatively:
toast.success("Downloaded")
toast.error("Upload failed", description = Some(span("Check your connection.")))
val id = toast.loading("Processing…")     // sticky spinner; dismiss when done
toast.dismiss(id)
```

`toast.info`/`success`/`warning`/`error` each take a `message`, optional `description`, and
`duration` (ms; defaults to 4000). `toast.loading` is a sticky spinner with no auto-dismiss
until you `dismiss(id)` or `clear()`. The general form `toast.show(message, kind, …)` also
takes a `placement` (`ToastPlacement` — six corners/edges; each toast carries its own, so one
`Toaster` can show several stacks), `closable`, a custom `icon`, and an `onClick`. Each toast
auto-dismisses after its `duration`, **pauses on hover**, and animates out before leaving;
urgent kinds (`Error`/`Warning`) announce assertively (`role="alert"`), the rest politely.
The store behind it (`ToastStore`) is a plain external store bridged in through
[`useSyncExternalStore`](/guide/hooks/#usesyncexternalstore).
