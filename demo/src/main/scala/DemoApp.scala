import io.github.edadma.vdom.*
import org.scalajs.dom

// A small showcase of the basics: local state via useState, event handlers,
// a controlled input, and a keyed list that adds and removes items. Lives in
// the default (empty) package — it's a self-contained app, imported by nobody.
object DemoApp:

  val Counter = view {
    val (count, _, update) = useState(0)
    div(
      cls := "card",
      h2("Counter"),
      p(s"Count: $count"),
      button(onClick := (_ => update(_ - 1)), "−"),
      span(" "),
      button(onClick := (_ => update(_ + 1)), "+"),
      span(" "),
      button(onClick := (_ => update(_ => 0)), "reset"),
    )
  }

  val TodoList = view {
    val (items, setItems, updateItems) = useState(Vector("milk", "eggs", "coffee"))
    val (draft, setDraft, _)           = useState("")

    def add(): Unit =
      val t = draft.trim
      if t.nonEmpty then
        updateItems(_ :+ t)
        setDraft("")

    div(
      cls := "card",
      h2("Todo (keyed list)"),
      div(
        input(
          value := draft,
          placeholder := "add an item…",
          onInput := (e => setDraft(targetValue(e))),
          onKeyDown := (e => if e.asInstanceOf[dom.KeyboardEvent].key == "Enter" then add()),
        ),
        span(" "),
        button(onClick := (_ => add()), "add"),
      ),
      when(items.isEmpty)(p(cls := "muted", "Nothing yet — add one above.")),
      ul(
        items.map { item =>
          li(
            key := item,
            span(item),
            span(" "),
            button(onClick := (_ => setItems(items.filterNot(_ == item))), "✕"),
          )
        }
      ),
    )
  }

  // A child component that takes props. The tuple is destructured in the lambda
  // so the body uses bare `label`/`value` (no field access), while the call
  // sites below still pass named literals — a named tuple conforms to its
  // unnamed counterpart, so `(label = …, value = …)` fits `(String, Int)`.
  val Stat: Component[(String, Int)] = component {
    case (label, value) =>
      div(
        cls := "stat",
        span(cls := "label", label),
        span(": "),
        strong(value),
      )
  }

  // A parent that renders the props-taking child twice, feeding each its own
  // values from local state. Bumping a counter re-renders the parent, which
  // passes new props down and re-renders just that Stat.
  val Dashboard = view {
    val (clicks, _, bumpClicks) = useState(0)
    val (likes,  _, bumpLikes)  = useState(0)
    div(
      cls := "card",
      h2("Dashboard (child component with props)"),
      Stat((label = "Clicks", value = clicks)),
      Stat((label = "Likes", value = likes)),
      div(
        button(onClick := (_ => bumpClicks(_ + 1)), "click"),
        span(" "),
        button(onClick := (_ => bumpLikes(_ + 1)), "like"),
      ),
    )
  }

  // Holds a handle to the live <input> through `ref`, then reaches for it
  // imperatively in a click handler — the thing local state can't do.
  val FocusCard = view {
    val inputRef = useRef[dom.html.Input | Null](null)
    div(
      cls := "card",
      h2("Ref (imperative focus)"),
      input(ref := inputRef, placeholder := "press focus →"),
      span(" "),
      button(
        onClick := { _ =>
          val node = inputRef.current
          if node != null then
            node.focus()
            node.select()
        },
        "focus",
      ),
    )
  }

  val App = view {
    div(
      h1("vdom demo"),
      Counter(),
      TodoList(),
      Dashboard(),
      FocusCard(),
    )
  }

  def main(args: Array[String]): Unit =
    val container = dom.document.getElementById("app")
    if container != null then render(App(), container)
