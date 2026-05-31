package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// A button that opens a small menu of actions — a sort menu, an account menu, a row's
// "⋯" overflow. Data-driven: you pass the `items`, salle renders the trigger and the
// `role=menu` popup and handles open/close, keyboard, and dismissal. It is the same
// dropdown machinery as [[Select]] (a trigger owning a popup of options) but for *actions*
// rather than a chosen value, so it has no selected state — picking an item runs it and
// closes.
//
// Keyboard follows the menu-button pattern: the trigger opens on Enter/Space/ArrowDown
// (and ArrowUp, landing on the last item); inside the menu, Up/Down move the highlight
// (skipping disabled items and dividers, wrapping), Home/End jump to the ends, Enter/Space
// run the highlighted item, Escape closes and returns focus to the trigger, Tab closes.
// Highlight tracks via `aria-activedescendant` + a `data-active` mirror (the same approach
// as Select), so it is assertable without real DOM focus.
//
// State is mirrored to `data-*`: the root carries `data-part=dropdown` + `data-state`
// (open|closed|disabled); the trigger `data-part=trigger`; the menu `data-part=menu`; each
// entry `data-part` (item|divider) with `data-key`, `data-active`, `data-disabled`,
// `data-danger` on items.

/** One entry in a [[Dropdown]] menu: an actionable [[MenuEntry.Item]] or a [[MenuEntry.Divider]]
  * rule between groups. Build items with [[MenuItem]] and dividers with [[MenuDivider]]. */
enum MenuEntry:
  case Item(key: String, label: VNode, icon: Option[VNode], disabled: Boolean, danger: Boolean, onSelect: () => Unit)
  case Divider

/** Build a [[Dropdown]] menu item. `key` identifies it (reported to the dropdown's
  * `onSelect`); `label` is its content; `icon` is an optional leading slot; `danger` styles
  * a destructive action; `disabled` skips it for keyboard and pointer; `onSelect` runs when
  * it is chosen (in addition to the dropdown-level `onSelect`). */
def MenuItem(
    key:      String,
    label:    VNode,
    icon:     Option[VNode] = None,
    disabled: Boolean       = false,
    danger:   Boolean       = false,
    onSelect: () => Unit    = () => (),
): MenuEntry =
  MenuEntry.Item(key, label, icon, disabled, danger, onSelect)

/** A horizontal rule separating groups of [[Dropdown]] items. */
val MenuDivider: MenuEntry = MenuEntry.Divider

private val DropdownImpl =
  container[(items: Vector[MenuEntry], disabled: Boolean, onSelect: String => Unit)] { (p, children) =>
    val skin  = useSkin()
    val parts = skin.dropdown

    val (open, setOpen, _)     = useState(false)
    val (active, setActive, _) = useState(-1)

    val wrapper        = useRef[dom.Element | Null](null)
    val base           = useId()
    val triggerId      = base + "-trigger"
    val menuId         = base + "-menu"
    def itemId(i: Int) = base + "-item-" + i.toString

    val items = p.items

    // Only non-disabled Item entries take focus; dividers and disabled items are skipped.
    def isSelectable(i: Int): Boolean =
      i >= 0 && i < items.length && (items(i) match
        case MenuEntry.Item(_, _, _, d, _, _) => !d
        case MenuEntry.Divider                => false)

    def firstSelectable: Int =
      items.indexWhere {
        case MenuEntry.Item(_, _, _, d, _, _) => !d
        case _                                => false
      }

    def lastSelectable: Int =
      items.lastIndexWhere {
        case MenuEntry.Item(_, _, _, d, _, _) => !d
        case _                                => false
      }

    // Step to the next selectable entry in `dir` (+1/-1), wrapping; `from` may be -1.
    def nextSelectable(from: Int, dir: Int): Int =
      if items.isEmpty then -1
      else
        var i = from
        var n = 0
        while n < items.length do
          i = (i + dir + items.length) % items.length
          if isSelectable(i) then return i
          n += 1
        from

    def refocusTrigger(): Unit =
      val el = dom.document.getElementById(triggerId)
      if el != null then el.asInstanceOf[dom.html.Element].focus()

    def openMenu(toLast: Boolean): Unit =
      setOpen(true)
      setActive(if toLast then lastSelectable else firstSelectable)

    def closeMenu(refocus: Boolean): Unit =
      setOpen(false)
      setActive(-1)
      if refocus then refocusTrigger()

    def commit(i: Int): Unit =
      if isSelectable(i) then
        items(i) match
          case MenuEntry.Item(key, _, _, _, _, onSel) =>
            p.onSelect(key)
            onSel()
            closeMenu(refocus = true)
          case MenuEntry.Divider => ()

    useClickOutside(wrapper, open, () => closeMenu(refocus = false))

    // The trigger opens the menu and, while open, also drives navigation (the test and a
    // keyboard user both keep focus on the trigger, using aria-activedescendant). Enter/Space
    // commit the active item when open; ArrowDown/Up move; Escape closes.
    val onTriggerKey: dom.KeyboardEvent => Unit = e =>
      if !p.disabled then
        e.key match
          case "ArrowDown" =>
            e.preventDefault()
            if open then setActive(nextSelectable(active, 1)) else openMenu(toLast = false)
          case "ArrowUp" =>
            e.preventDefault()
            if open then setActive(nextSelectable(active, -1)) else openMenu(toLast = true)
          case "Home" =>
            if open then { e.preventDefault(); setActive(firstSelectable) }
          case "End" =>
            if open then { e.preventDefault(); setActive(lastSelectable) }
          case "Enter" | " " | "Spacebar" =>
            e.preventDefault()
            if open then commit(active) else openMenu(toLast = false)
          case "Escape" =>
            if open then { e.preventDefault(); closeMenu(refocus = false) }
          case "Tab" =>
            if open then closeMenu(refocus = false) // let focus move on
          case _ => ()

    val triggerNode =
      button(
        cls              := parts.trigger,
        id               := triggerId,
        typ              := "button",
        data("part")     := "trigger",
        aria("haspopup") := "menu",
        aria("expanded") := (if open then "true" else "false"),
        aria("controls") := menuId,
        disabled         := p.disabled,
        onClick := (_ => if !p.disabled then (if open then closeMenu(refocus = false) else openMenu(toLast = false))),
        onKeyDown := onTriggerKey,
        children,
        span(cls := parts.arrow, aria("hidden") := "true", unsafeHtml(DropdownCaret)),
      )

    val menuNode: Mod =
      if open then
        val rows: Seq[VNode] = items.zipWithIndex.map { (entry, i) =>
          entry match
            case MenuEntry.Divider =>
              li(cls := parts.divider, role := "separator", data("part") := "divider", aria("hidden") := true)
            case MenuEntry.Item(key, label, icon, itemDisabled, danger, _) =>
              val iconNode: Mod = icon match
                case Some(ic) => span(cls := parts.icon, data("part") := "icon", ic)
                case None     => NoMod
              li(
                cls              := parts.item,
                id               := itemId(i),
                role             := "menuitem",
                data("part")     := "item",
                data("key")      := key,
                data("active")   := (i == active),
                data("disabled") := itemDisabled,
                data("danger")   := danger,
                aria("disabled") := (if itemDisabled then "true" else "false"),
                onMouseDown      := (e => e.preventDefault()), // keep focus on the trigger
                onMouseEnter     := (_ => if !itemDisabled then setActive(i)),
                onClick          := (_ => commit(i)),
                iconNode,
                span(label),
              )
        }
        ul(
          cls                      := parts.menu,
          id                       := menuId,
          role                     := "menu",
          aria("labelledby")       := triggerId,
          aria("activedescendant") := (if active >= 0 then itemId(active) else ""),
          rows,
        )
      else NoMod

    div(
      cls           := parts.root,
      ref           := wrapper,
      data("part")  := "dropdown",
      data("state") := (if p.disabled then "disabled" else if open then "open" else "closed"),
      triggerNode,
      menuNode,
    )
  }

/** A menu button: a trigger (its `label` content is the `children`) that opens a menu of
  * `items` ([[MenuItem]] / [[MenuDivider]]). Choosing an item runs its `onSelect` and reports
  * its key to the dropdown-level `onSelect`, then closes. `disabled` greys the whole control.
  * It is a fully keyboard-driven `role=menu` (arrows/Home/End/Enter/Escape/Tab) with
  * click-outside dismissal; classes come from the active [[Skin]] and state is mirrored to
  * `data-*`. Submenus and hover-to-open are intentionally out of scope for now. */
def Dropdown(
    items:    Seq[MenuEntry],
    disabled: Boolean        = false,
    onSelect: String => Unit = _ => (),
)(label: VNode*): VNode =
  DropdownImpl((items = items.toVector, disabled = disabled, onSelect = onSelect))(label*)

// Feather chevron-down, drawn with currentColor; injected as trusted static innerHTML so it
// parses into correctly-namespaced SVG nodes.
private val DropdownCaret =
  """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="6 9 12 15 18 9"></polyline></svg>"""
