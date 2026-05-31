# riposte

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
- [Guide](https://riposte.edadma.dev/guide/) — components & the DSL, hooks, shared state, routing
- [Reference](https://riposte.edadma.dev/reference/) — the published modules and their APIs

## Modules

Riposte ships as three independently published artifacts under `io.github.edadma`; the two
siblings depend on the core transitively.

| Artifact          | What it gives you                                  |
|-------------------|----------------------------------------------------|
| `riposte`         | The core: components, hooks, the DSL, rendering     |
| `riposte-atoms`   | Jotai-inspired atomic shared state                  |
| `riposte-router`  | Client-side routing for single-page apps            |
| `salle`           | Styled, skinnable component library (Button, Input, Checkbox, Toggle) |

```scala
libraryDependencies += "io.github.edadma" %%% "riposte"        % "0.0.1"
libraryDependencies += "io.github.edadma" %%% "riposte-atoms"  % "0.0.1"
libraryDependencies += "io.github.edadma" %%% "riposte-router" % "0.0.1"
libraryDependencies += "io.github.edadma" %%% "salle"          % "0.0.1"
```

## Repository layout

- `core/` — the `riposte` library (the published artifact)
- `atoms/` — `riposte-atoms`, atomic state built on the core's `useSyncExternalStore` seam
- `router/` — `riposte-router`, client-side routing on the same public seam
- `salle/` — `salle`, a styled component library built on the core's DSL + hooks
- `demo/` — a runnable showcase (its own subproject, never published)
- `salle-demo/` — a runnable showcase for salle (its own subproject, never published)
- the repo root is a thin aggregator; a task run there fans out to every module

## Development

```sh
sbt test              # every module's jsdom-backed suite (core + atoms + router + salle)
sbt riposte/test      # just the library (project id is `riposte`, in core/)
sbt atoms/test        # just the atoms module
sbt router/test       # just the router module
sbt salle/test        # just the salle component library
sbt demo/fastLinkJS   # build the demo's JS
sbt salleDemo/fastLinkJS  # build the salle demo's JS
```

Tests run under a real DOM via jsdom (`npm install` fetches it). To see the demo, build
its JS, then serve `demo/` (e.g. `cd demo && python3 -m http.server`) and open `index.html`.

## License

ISC
