package io.github.edadma.riposte.salle

import io.github.edadma.riposte.*
import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

// Descriptions is a pure, data-driven display component (no timers/keyboard). These specs pin the
// row-packing arithmetic (the `descriptionsRows` helper), the horizontal and vertical table
// markup, the label↔value aria wiring, the colon/border/header options, and the data-* mirror.
class DescriptionsSpec extends AnyFunSuite:

  private def host(): dom.Element =
    val c = dom.document.createElement("div")
    dom.document.body.appendChild(c)
    c

  private def root(c: dom.Element): dom.html.Element =
    c.querySelector("[data-part=descriptions]").asInstanceOf[dom.html.Element]
  private def labels(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=label]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def contents(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=content]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])
  private def dataRows(c: dom.Element): Seq[dom.html.Element] =
    val nl = c.querySelectorAll("[data-part=row]")
    (0 until nl.length).map(i => nl.item(i).asInstanceOf[dom.html.Element])

  private val items: Seq[DescriptionsItem] = Seq(
    DescItem(label = "Resolution", value = "3840×2160"),
    DescItem(label = "License", value = "CC0"),
    DescItem(label = "Downloads", value = "1,204"),
  )

  // --- descriptionsRows (pure) -----------------------------------------------------------------

  test("descriptionsRows packs items into rows of the column count"):
    val v    = items.toVector
    val rows = descriptionsRows(v, 2)
    assert(rows.length == 2)
    assert(rows(0).map(_._1) == Vector(v(0), v(1))) // same item references, in order
    assert(rows(1).map(_._1) == Vector(v(2)))

  test("descriptionsRows clamps an oversized span to the column count"):
    val rows = descriptionsRows(Vector(DescItem(label = "Wide", value = "x", span = 5)), 3)
    assert(rows.length == 1)
    assert(rows(0).head._2 == 3) // effective span clamped to columns

  test("descriptionsRows starts a new row when a span would overflow the current one"):
    val rows = descriptionsRows(
      Vector(
        DescItem(label = "A", value = "1"),
        DescItem(label = "B", value = "2", span = 2),
      ),
      2,
    )
    assert(rows.length == 2) // A fills 1 of 2; B(span 2) won't fit, so it starts its own row

  test("a filled item takes the rest of its row"):
    val rows = descriptionsRows(
      Vector(
        DescItem(label = "A", value = "1"),
        DescItem(label = "Rest", value = "r", filled = true),
      ),
      3,
    )
    assert(rows.length == 1)
    assert(rows(0)(1)._2 == 2) // 3 columns - 1 already used = 2

  // --- rendering -------------------------------------------------------------------------------

  test("renders a group wrapping a table of label/value pairs"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items))
    Scheduler.flushSync()
    assert(root(c).getAttribute("role") == "group")
    assert(c.querySelector("[data-part=table]").asInstanceOf[dom.html.Element].tagName.toLowerCase == "table")
    assert(labels(c).map(_.textContent) == Seq("Resolution:", "License:", "Downloads:"))
    assert(contents(c).map(_.textContent) == Seq("3840×2160", "CC0", "1,204"))
    r.unmount()

  test("horizontal layout uses th[scope=row] labels and one tr per row"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items, column = 3))
    Scheduler.flushSync()
    assert(labels(c).forall(_.tagName.toLowerCase == "th"))
    assert(labels(c).forall(_.getAttribute("scope") == "row"))
    assert(dataRows(c).length == 1) // all three fit in one row of 3 columns
    assert(root(c).getAttribute("data-columns") == "3")
    r.unmount()

  test("colon is appended to labels by default and omitted when colon=false"):
    val c1 = host()
    val r1 = createRoot(c1)
    r1.render(Descriptions(items = items))
    Scheduler.flushSync()
    assert(labels(c1).head.textContent == "Resolution:")
    r1.unmount()

    val c2 = host()
    val r2 = createRoot(c2)
    r2.render(Descriptions(items = items, colon = false))
    Scheduler.flushSync()
    assert(labels(c2).head.textContent == "Resolution")
    r2.unmount()

  test("a spanning item's value cell covers 2*span-1 table cells in the horizontal layout"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = Seq(DescItem(label = "Palette", value = "warm", span = 2)), column = 3))
    Scheduler.flushSync()
    assert(contents(c).head.getAttribute("colspan") == "3") // 2*2-1
    r.unmount()

  test("vertical layout stacks a row of labels above a row of values"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items, column = 3, layout = DescriptionsLayout.Vertical))
    Scheduler.flushSync()
    assert(root(c).getAttribute("data-layout") == "vertical")
    assert(labels(c).forall(_.getAttribute("scope") == "col"))
    assert(c.querySelector("[data-part=label-row]") != null)
    assert(c.querySelector("[data-part=value-row]") != null)
    r.unmount()

  test("each value cell points back to its label via aria-labelledby"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items))
    Scheduler.flushSync()
    val firstLabelId = labels(c).head.getAttribute("id")
    assert(firstLabelId != null && firstLabelId.nonEmpty)
    assert(contents(c).head.getAttribute("aria-labelledby") == firstLabelId)
    r.unmount()

  test("bordered is mirrored to data-bordered and the skin's bordered modifier"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items, bordered = true))
    Scheduler.flushSync()
    assert(root(c).getAttribute("data-bordered") == "true")
    assert(root(c).getAttribute("class").contains("salle-descriptions--bordered"))
    r.unmount()

  test("a title and extra render in the header"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items, title = Some("Details": VNode), extra = Some(span(id := "x", "edit"))))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=title]").textContent == "Details")
    assert(c.querySelector("[data-part=extra] #x") != null)
    r.unmount()

  test("SalleSkin applies its descriptions part classes"):
    val c = host()
    val r = createRoot(c)
    r.render(Descriptions(items = items))
    Scheduler.flushSync()
    assert(root(c).getAttribute("class").contains("salle-descriptions"))
    assert(labels(c).head.getAttribute("class").contains("salle-descriptions__label"))
    assert(contents(c).head.getAttribute("class").contains("salle-descriptions__content"))
    r.unmount()

  test("DaisySkin emits the Tailwind table vocabulary, with borders only when bordered"):
    val c = host()
    val r = createRoot(c)
    r.render(SkinProvider(DaisySkin)(Descriptions(items = items, bordered = true)))
    Scheduler.flushSync()
    assert(c.querySelector("[data-part=table]").getAttribute("class").contains("border-collapse"))
    assert(labels(c).head.getAttribute("class").contains("border-base-content/10"))
    assert(labels(c).head.getAttribute("class").contains("bg-base-200/50"))
    r.unmount()
