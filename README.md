# riposte

![Maven Central](https://img.shields.io/maven-central/v/io.github.edadma/riposte_sjs1_3)
[![Last Commit](https://img.shields.io/github/last-commit/edadma/riposte)](https://github.com/edadma/riposte/commits)
![GitHub](https://img.shields.io/github/license/edadma/riposte)
![Scala Version](https://img.shields.io/badge/Scala-3.8.3-blue.svg)
![ScalaJS Version](https://img.shields.io/badge/Scala.js-1.21.0-blue.svg)

A React-inspired **frontend library for Scala.js**. Build user interfaces with function
components, hooks, and a typed DSL: an immutable `VNode` tree describes the UI, and a
reconciler diffs each new tree against the live DOM and patches only what changed.

> *riposte* — in fencing, the swift counter-thrust that follows a parry. Here: an event or
> state change comes in, the reconciler diffs (parry), and patches the DOM in answer
> (riposte). The word traces back through Italian sword-fighting to Latin *respondēre* —
> "to respond."

```scala
import io.github.edadma.riposte.*
import org.scalajs.dom

val Counter = view {
  val (count, _, update) = useState(0)
  button(onClick := (_ => update(_ + 1)), s"Count: $count")
}

@main def run(): Unit =
  render(Counter(), dom.document.getElementById("app"))
```

## 📖 Documentation

**Full documentation lives at [riposte.edadma.dev](https://riposte.edadma.dev/).**

- [Getting started](https://riposte.edadma.dev/getting-started/) — install and mount your first component
- [Guide](https://riposte.edadma.dev/guide/) — components & the DSL, hooks, shared state, routing, data fetching, forms
- [Component library (salle)](https://riposte.edadma.dev/salle/) — styled, skinnable widgets, one page per component
- [Reference](https://riposte.edadma.dev/reference/) — the published modules and their APIs

## Modules

Riposte ships as six independently published artifacts under `io.github.edadma`; the
siblings depend on the core transitively.

| Artifact          | What it gives you                                  |
|-------------------|----------------------------------------------------|
| `riposte`         | The core: components, hooks, the DSL, rendering     |
| `riposte-atoms`   | Jotai-inspired atomic shared state                  |
| `riposte-router`  | Client-side routing for single-page apps            |
| `riposte-query`   | TanStack-style async server-state cache (built on atoms) |
| `riposte-forms`   | react-hook-form-style form layer (uncontrolled fields, validation) |
| `riposte-salle`   | Styled, skinnable component library — form controls (incl. Segmented), Image/Lightbox, Carousel/Hero, Badge/Tag, Skeleton, Spinner/Progress, Empty, Descriptions, Pagination, Dropdown, Tabs, Breadcrumb, Modal/Drawer/Toast/Tooltip, Layout/Navbar/Footer shell, grid + masonry layout, theming |

```scala
libraryDependencies += "io.github.edadma" %%% "riposte"        % "0.2.1"
libraryDependencies += "io.github.edadma" %%% "riposte-atoms"  % "0.2.1"
libraryDependencies += "io.github.edadma" %%% "riposte-router" % "0.2.1"
libraryDependencies += "io.github.edadma" %%% "riposte-query"  % "0.2.1"
libraryDependencies += "io.github.edadma" %%% "riposte-forms"  % "0.2.1"
libraryDependencies += "io.github.edadma" %%% "riposte-salle"  % "0.2.1"
```

## Repository layout

- `core/` — the `riposte` library (the published artifact)
- `atoms/` — `riposte-atoms`, atomic state built on the core's `useSyncExternalStore` seam
- `router/` — `riposte-router`, client-side routing on the same public seam
- `query/` — `riposte-query`, an async server-state cache built on `riposte-atoms`
- `forms/` — `riposte-forms`, a react-hook-form-style form layer on the core's public API
- `salle/` — `riposte-salle`, a styled component library built on the core's DSL + hooks
- `demo/` — a runnable showcase (its own subproject, never published)
- `salle-demo/` — a runnable showcase for salle (its own subproject, never published)
- `salle-e2e/` — a Playwright harness driving salle components in a real browser (never published)
- the repo root is a thin aggregator; a task run there fans out to every module

## Development

```sh
sbt test              # every module's jsdom-backed suite (core + atoms + router + query + forms + salle)
sbt riposte/test      # just the library (project id is `riposte`, in core/)
sbt atoms/test        # just the atoms module
sbt router/test       # just the router module
sbt query/test        # just the query module
sbt forms/test        # just the forms module
sbt salle/test        # just the salle component library
sbt demo/fastLinkJS   # build the demo's JS
sbt salleDemo/fastLinkJS  # build the salle demo's JS
```

Tests run under a real DOM via jsdom (`npm install` fetches it). To see the demo, build
its JS, then serve `demo/` (e.g. `cd demo && python3 -m http.server`) and open `index.html`.

The salle component library also has a **Playwright** suite that drives the components in a
real Chromium (`salle-e2e/`), covering interactions jsdom can't — focus, pointer, layout:

```sh
npm run e2e        # build the harness bundle, then run the Playwright specs
npm run e2e:build  # just build the harness JS (sbt salleE2E/fastLinkJS)
npm run e2e:run    # run the specs against an already-built bundle
npm run e2e:ui     # the same, in Playwright's interactive UI
```

## License

ISC
