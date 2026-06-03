package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom

// A row of tabs that switches between panels of content — categories, settings sections,
// the facets of a detail view. Data-driven: you pass the `items` (each a key, a label, and
// the panel content), salle renders the `role=tablist` strip and the active `role=tabpanel`
// and handles selection, keyboard, and the ARIA wiring. Selection is controllable (pass
// `activeKey` to own it) or self-managed (seed it with `defaultActiveKey`).
//
// Keyboard follows the WAI-ARIA tabs pattern with *automatic activation*: only the active
// tab is in the tab order (roving `tabindex`), Left/Right move focus to the adjacent enabled
// tab and activate it (wrapping, skipping disabled), and Home/End jump to the first/last
// enabled tab. Disabled tabs carry the native `disabled` attribute, so they are skipped by
// both pointer and keyboard.
//
// State is mirrored to `data-*`: the root carries `data-part=tabs` + `data-variant` +
// `data-position` + `data-active` (the active key); the strip `data-part=tablist`; each tab
// `data-part=tab` with `data-key`, `data-active`, `data-disabled`; the panel `data-part=tabpanel`
// with `data-key`.

/** The visual style of a [[Tabs]] strip, mirroring DaisyUI's tab variants: `Box` (each tab a
  * filled box), `Border` (an underline indicator under the active tab — the default), `Lift`
  * (the active tab lifts to meet the panel). */
enum TabsVariant:
  case Box, Border, Lift

  /** The lowercase modifier token (`"box"`). */
  def token: String = toString.toLowerCase

/** Where the tab strip sits relative to its panel: `Top` (the default) or `Bottom`. Only the
  * DOM order and the panel's margin side change; the strip itself is unaffected. */
enum TabsPosition:
  case Top, Bottom

  /** The lowercase modifier token (`"top"`). */
  def token: String = toString.toLowerCase

/** One tab and its panel: `key` identifies it (reported to [[Tabs]]'s `onChange` and used in
  * the ARIA wiring), `label` is the strip button's content, `content` is the panel shown when
  * the tab is active, `icon` is an optional leading slot, and `disabled` removes it from
  * pointer and keyboard selection. Build with [[Tab]]. */
type TabItem = (
    key: String,
    label: VNode,
    content: VNode,
    icon: Option[VNode],
    disabled: Boolean,
)

/** Build a [[Tabs]] item: a `key`, the strip `label`, the panel `content`, an optional leading
  * `icon`, and whether it is `disabled`. */
def Tab(
    key:      String,
    label:    VNode,
    content:  VNode,
    icon:     Option[VNode] = None,
    disabled: Boolean       = false,
): TabItem =
  (key = key, label = label, content = content, icon = icon, disabled = disabled)

private val TabsImpl =
  component[(
      items:            Vector[TabItem],
      activeKey:        Option[String],
      defaultActiveKey: Option[String],
      variant:          TabsVariant,
      size:             Size,
      position:         TabsPosition,
      onChange:         String => Unit,
  )] { p =>
    val skin  = useSkin()
    val parts = skin.tabs(p.variant, p.size, p.position)
    val items = p.items

    // Uncontrolled selection seeds from `defaultActiveKey`, falling back to the first item.
    val seed = p.defaultActiveKey.orElse(items.headOption.map(_.key)).getOrElse("")
    val (active, setActive) = useControllable(p.activeKey, seed, p.onChange)

    val base           = useId()
    def tabId(k:   String) = base + "-tab-" + k
    def panelId(k: String) = base + "-panel-" + k

    def isEnabled(i: Int): Boolean =
      i >= 0 && i < items.length && !items(i).disabled

    def firstEnabled: Int = items.indexWhere(!_.disabled)
    def lastEnabled:  Int = items.lastIndexWhere(!_.disabled)

    // Step to the next enabled tab in `dir` (+1/-1), wrapping; `from` may be any index.
    def nextEnabled(from: Int, dir: Int): Int =
      if items.isEmpty then -1
      else
        var i = from
        var n = 0
        while n < items.length do
          i = (i + dir + items.length) % items.length
          if isEnabled(i) then return i
          n += 1
        from

    // Activate a tab and move real focus to it — automatic activation, so selection follows
    // focus. The tab element exists regardless of the roving tabindex, so focusing by id works
    // before the re-render flips its tabindex to 0.
    def moveTo(i: Int): Unit =
      if isEnabled(i) then
        val k = items(i).key
        setActive(k)
        val el = dom.document.getElementById(tabId(k))
        if el != null then el.asInstanceOf[dom.html.Element].focus()

    val onTabKey: Int => dom.KeyboardEvent => Unit = idx =>
      e =>
        e.key match
          case "ArrowRight" =>
            e.preventDefault(); moveTo(nextEnabled(idx, 1))
          case "ArrowLeft" =>
            e.preventDefault(); moveTo(nextEnabled(idx, -1))
          case "Home" =>
            e.preventDefault(); moveTo(firstEnabled)
          case "End" =>
            e.preventDefault(); moveTo(lastEnabled)
          case _ => ()

    val tabsNodes: Seq[VNode] = items.zipWithIndex.map { (item, i) =>
      val isActive = item.key == active
      val iconNode: Mod = item.icon match
        case Some(ic) => span(cls := parts.icon, data("part") := "icon", aria("hidden") := true, ic)
        case None     => NoMod
      button(
        cls := (parts.tab
          + (if isActive then " " + parts.active else "")
          + (if item.disabled then " " + parts.disabled else "")),
        id               := tabId(item.key),
        typ              := "button",
        role             := "tab",
        data("part")     := "tab",
        data("key")      := item.key,
        data("active")   := isActive,
        data("disabled") := item.disabled,
        aria("selected") := (if isActive then "true" else "false"),
        aria("controls") := panelId(item.key),
        tabIndex         := (if isActive then "0" else "-1"),
        disabled         := item.disabled,
        onClick          := (_ => if !item.disabled then setActive(item.key)),
        onKeyDown        := onTabKey(i),
        iconNode,
        span(item.label),
      )
    }

    val listNode =
      div(cls := parts.list, role := "tablist", data("part") := "tablist", tabsNodes)

    val panelNode: VNode =
      items.find(_.key == active) match
        case Some(item) =>
          div(
            cls                := parts.panel,
            role               := "tabpanel",
            id                 := panelId(item.key),
            data("part")       := "tabpanel",
            data("key")        := item.key,
            aria("labelledby") := tabId(item.key),
            tabIndex           := "0",
            item.content,
          )
        case None => VEmpty

    val ordered: Seq[VNode] =
      if p.position == TabsPosition.Top then Seq(listNode, panelNode)
      else Seq(panelNode, listNode)

    div(
      cls              := parts.root,
      data("part")     := "tabs",
      data("variant")  := p.variant.token,
      data("position") := p.position.token,
      data("active")   := active,
      ordered,
    )
  }

/** A data-driven tab set: pass the `items` ([[Tab]]) and salle renders the `role=tablist`
  * strip plus the active `role=tabpanel`. Selection is controllable via `activeKey` (the
  * caller owns it, told of intent through `onChange`) or self-managed from `defaultActiveKey`
  * (defaulting to the first item). `variant` picks the look ([[TabsVariant]]), `size` scales
  * the strip, and `position` places it above or below its panel. Fully keyboard-driven with
  * roving `tabindex` and automatic activation; classes come from the active [[Skin]] and state
  * is mirrored to `data-*`. The compound (children-as-panels) pattern is intentionally out of
  * scope for now. */
def Tabs(
    items:            Seq[TabItem],
    activeKey:        Option[String] = None,
    defaultActiveKey: Option[String] = None,
    variant:          TabsVariant    = TabsVariant.Border,
    size:             Size           = Size.Md,
    position:         TabsPosition   = TabsPosition.Top,
    onChange:         String => Unit = _ => (),
): VNode =
  TabsImpl(
    (
      items = items.toVector,
      activeKey = activeKey,
      defaultActiveKey = defaultActiveKey,
      variant = variant,
      size = size,
      position = position,
      onChange = onChange,
    ),
  )
