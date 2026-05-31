package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Dropdown is a data-driven menu button. These specs pin the observable contract a
// Playwright suite will later drive: the trigger ARIA + data-* mirror, open/close by
// click, the role=menu with an item per entry (and separators for dividers), selection by
// pointer and keyboard, disabled-item skipping, keyboard navigation via the data-active /
// aria-activedescendant mirror, Escape/Tab closing, click-outside dismissal, and DaisySkin
// reskinning.
class DropdownSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private val sortItems = Seq(
    MenuItem("newest", "Newest"),
    MenuItem("popular", "Most popular"),
    MenuDivider,
    MenuItem("random", "Random", disabled = true),
    MenuItem("oldest", "Oldest"),
  )

  private def trigger(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=trigger]").asInstanceOf[dom.html.Element]

  private def keydown(el: dom.Element, k: String): Unit =
    el.dispatchEvent(
      new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true; cancelable = true }),
    )
    Scheduler.flushSync()

  test("renders a closed menu button with the trigger ARIA and data mirror"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t != null)
    assert(t.textContent.contains("Sort"))
    assert(t.getAttribute("aria-haspopup") == "menu")
    assert(t.getAttribute("aria-expanded") == "false")
    assert(c.querySelector("[role=menu]") == null) // closed: no popup
    assert(c.querySelector(".salle-dropdown").getAttribute("data-state") == "closed")

  test("clicking the trigger opens the menu with an item per entry and a separator"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    val menu = c.querySelector("[role=menu]")
    assert(menu != null)
    assert(trigger(c).getAttribute("aria-expanded") == "true")
    assert(c.querySelector(".salle-dropdown").getAttribute("data-state") == "open")
    assert(c.querySelectorAll("[role=menuitem]").length == 4) // 4 items, divider excluded
    assert(c.querySelector("[role=separator]") != null)
    // the disabled item is marked for assistive tech and styling/tests
    val random = c.querySelector("[data-key=random]")
    assert(random.getAttribute("aria-disabled") == "true")
    assert(random.getAttribute("data-disabled") == "true")

  test("choosing an item reports its key, runs its onSelect, and closes"):
    var got      = ""
    var ran      = false
    val items    = Seq(MenuItem("a", "A"), MenuItem("b", "B", onSelect = () => ran = true))
    val c        = host()
    render(Dropdown(items = items, onSelect = k => got = k)("M"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    c.querySelector("[data-key=b]").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(got == "b")
    assert(ran)
    assert(c.querySelector("[role=menu]") == null) // closed on select

  test("a disabled item cannot be chosen"):
    var got = ""
    val c   = host()
    render(Dropdown(items = sortItems, onSelect = k => got = k)("Sort"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    c.querySelector("[data-key=random]").asInstanceOf[dom.html.Element].click()
    Scheduler.flushSync()
    assert(got == "")
    assert(c.querySelector("[role=menu]") != null) // stays open

  test("keyboard: ArrowDown opens, skips the disabled item, and Enter chooses the active one"):
    var got = ""
    val c   = host()
    render(Dropdown(items = sortItems, onSelect = k => got = k)("Sort"), c)
    Scheduler.flushSync()
    val t = trigger(c)
    keydown(t, "ArrowDown") // opens, active = first (newest)
    assert(c.querySelector("[role=menu]") != null)
    assert(c.querySelector("[data-active=true]").getAttribute("data-key") == "newest")
    keydown(t, "ArrowDown") // -> popular
    assert(c.querySelector("[data-active=true]").getAttribute("data-key") == "popular")
    keydown(t, "ArrowDown") // skips divider + disabled random -> oldest
    assert(c.querySelector("[data-active=true]").getAttribute("data-key") == "oldest")
    keydown(t, "Enter")
    assert(got == "oldest")
    assert(c.querySelector("[role=menu]") == null)

  test("ArrowUp on the closed trigger opens to the last item"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    keydown(trigger(c), "ArrowUp")
    assert(c.querySelector("[data-active=true]").getAttribute("data-key") == "oldest")

  test("aria-activedescendant tracks the active item"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    keydown(trigger(c), "ArrowDown")
    val menu   = c.querySelector("[role=menu]")
    val active = c.querySelector("[data-active=true]")
    assert(menu.getAttribute("aria-activedescendant") == active.getAttribute("id"))

  test("Escape closes the menu"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=menu]") != null)
    keydown(trigger(c), "Escape")
    assert(c.querySelector("[role=menu]") == null)

  test("a press outside the control closes the menu"):
    val c = host()
    render(Dropdown(items = sortItems)("Sort"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=menu]") != null)
    dom.document.body.dispatchEvent(new dom.Event("pointerdown", new dom.EventInit { bubbles = true }))
    Scheduler.flushSync()
    assert(c.querySelector("[role=menu]") == null)

  test("disabled: the trigger does not open and the state is mirrored"):
    val c = host()
    render(Dropdown(items = sortItems, disabled = true)("Sort"), c)
    Scheduler.flushSync()
    val t = trigger(c)
    assert(t.asInstanceOf[dom.html.Button].disabled)
    assert(c.querySelector(".salle-dropdown").getAttribute("data-state") == "disabled")
    t.click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=menu]") == null)

  test("an item's icon and danger flag are reflected"):
    val items = Seq(MenuItem("del", "Delete", icon = Some(span(cls := "the-icon", "x")), danger = true))
    val c     = host()
    render(Dropdown(items = items)("M"), c)
    Scheduler.flushSync()
    trigger(c).click()
    Scheduler.flushSync()
    val item = c.querySelector("[data-key=del]")
    assert(item.getAttribute("data-danger") == "true")
    assert(item.querySelector("[data-part=icon] .the-icon") != null)

  test("DaisySkin reskins the trigger and menu via the provider"):
    val c = host()
    render(SkinProvider(DaisySkin)(Dropdown(items = sortItems)("Sort")), c)
    Scheduler.flushSync()
    assert(c.querySelector(".salle-dropdown") == null) // no salle root
    assert(c.querySelector(".dropdown") != null)
    assert(trigger(c).className.contains("btn"))
    trigger(c).click()
    Scheduler.flushSync()
    assert(c.querySelector("[role=menu]").className.contains("menu"))
