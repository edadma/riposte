package io.github.edadma.vdom.demo

import io.github.edadma.vdom.*
import org.scalajs.dom

// A small showcase of the basics: local state via useState, event handlers,
// a controlled input, and a keyed list that adds and removes items.
object DemoApp:

  private val Counter = view("Counter") {
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

  private val TodoList = view("TodoList") {
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

  private val App = view("App") {
    div(
      h1("vdom demo"),
      Counter(),
      TodoList(),
    )
  }

  def main(args: Array[String]): Unit =
    val container = dom.document.getElementById("app")
    if container != null then render(App(), container)
