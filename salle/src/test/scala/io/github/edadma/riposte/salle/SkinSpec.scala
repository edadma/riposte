package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// The skin seam: with no provider a component uses SalleSkin, and a single
// SkinProvider at the top re-skins every component in the subtree to a different
// class vocabulary — the property that lets an app switch to DaisyUI without
// changing any view code. Also pins the intent→class mapping for both skins.
class SkinSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def classesUnder(skin: Option[Skin], node: VNode): dom.DOMTokenList =
    val c    = host()
    val tree = skin.fold(node)(s => SkinProvider(s)(node))
    render(tree, c)
    Scheduler.flushSync()
    c.querySelector("button").classList

  private def hasClass(cl: dom.DOMTokenList, name: String): Boolean =
    (0 until cl.length).exists(i => cl.item(i) == name)

  test("with no provider a button uses the salle skin"):
    val cl = classesUnder(None, Button(ButtonProps("Save", color = Color.Primary)))
    assert(cl.contains("salle-btn"))
    assert(cl.contains("salle-btn--primary"))
    assert(!cl.contains("btn-primary"))

  test("DaisySkin maps each axis to a DaisyUI class"):
    val cl = classesUnder(
      Some(DaisySkin),
      Button(ButtonProps("Save", color = Color.Primary, variant = ButtonVariant.Outline, size = Size.Sm)),
    )
    assert(cl.contains("btn"))
    assert(cl.contains("btn-primary"))
    assert(cl.contains("btn-outline"))
    assert(cl.contains("btn-sm"))
    assert(!cl.contains("salle-btn"))

  test("default colour and solid variant emit no modifier under DaisySkin"):
    val cl = classesUnder(Some(DaisySkin), Button(ButtonProps("Save")))
    assert(cl.contains("btn"))
    assert(cl.contains("btn-md"))
    assert(!cl.contains("btn-primary"))
    assert(!hasClass(cl, "btn-solid")) // Solid is the base look, never a class
    assert(!hasClass(cl, "btn-default"))

  test("one provider re-skins every button in the subtree"):
    val c = host()
    render(
      SkinProvider(DaisySkin)(
        div(
          Button(ButtonProps("a", color = Color.Primary)),
          Button(ButtonProps("b", color = Color.Error, variant = ButtonVariant.Ghost)),
        ),
      ),
      c,
    )
    Scheduler.flushSync()
    val buttons = c.querySelectorAll("button")
    assert(buttons.length == 2)
    (0 until buttons.length).foreach { i =>
      assert(buttons(i).asInstanceOf[dom.Element].classList.contains("btn"))
    }
