import sbt._

/** Build-time codegen that bakes salle's stylesheet into the compiled artifact.
  *
  * salle is always Scala.js, and a Scala.js Maven jar carries only compiled output —
  * a browser can't read a `.css` out of it. So instead of shipping the CSS as a
  * separate asset, we embed it as a string constant the library injects at runtime
  * ([[io.github.edadma.riposte.salle.SalleStyles]]). The hand-written CSS in
  * `salle/css/` stays the source of truth; this just assembles it.
  */
object SalleCss {

  private val ImportRe = """@import\s+url\(["']\./parts/([^"']+)["']\)""".r

  /** Inline `salle.css`'s `@import`ed parts (in declared order) into one stylesheet,
    * wrapped in the single `@layer salle { … }` the separate imports produced. */
  def assemble(cssDir: File): String = {
    val index = IO.read(cssDir / "salle.css")
    val parts = ImportRe.findAllMatchIn(index).map(_.group(1)).toList
    val body  = parts.map(name => IO.read(cssDir / "parts" / name)).mkString("\n")
    "@layer salle {\n" + body + "\n}\n"
  }

  /** Emit `SalleCssContent.scala` holding the assembled stylesheet as a string.
    * Only rewrites when the content changed, so an unchanged build doesn't churn the
    * generated source (and force a needless recompile of dependents). */
  def generate(cssDir: File, sourceManaged: File): Seq[File] = {
    val out =
      sourceManaged / "io" / "github" / "edadma" / "riposte" / "salle" / "SalleCssContent.scala"
    val css = assemble(cssDir)
    val src =
      "package io.github.edadma.riposte.salle\n\n" +
        "// GENERATED — do not edit by hand. Assembled from salle/css/ by project/SalleCss.scala.\n" +
        "private[salle] object SalleCssContent {\n" +
        "  val css: String =\n" +
        cssExpr(css) + "\n" +
        "}\n"
    if (!out.exists || IO.read(out) != src) IO.write(out, src)
    Seq(out)
  }

  /** Render the CSS as a Scala expression. A single string constant in a class file
    * can't exceed 65535 UTF-8 bytes and the stylesheet is larger, so split it on line
    * boundaries into sub-limit chunks and join them at runtime with `Seq(...).mkString`.
    * (Joining literal chunks with `+` would be constant-folded back into one oversized
    * constant — the runtime `mkString` is what keeps each constant under the limit.)
    * Each chunk is a raw `"""…"""` so CSS backslashes/quotes stay literal, padded with
    * newlines so a line ending in `"` can't collide with the closing delimiter. */
  private def cssExpr(css: String): String = {
    val literals = chunks(css).map(c => "\"\"\"\n" + c + "\n\"\"\"")
    "    scala.collection.immutable.Seq(\n      " +
      literals.mkString(",\n      ") +
      "\n    ).mkString"
  }

  private def chunks(css: String): List[String] = {
    val maxBytes = 60000
    val out      = scala.collection.mutable.ListBuffer.empty[String]
    val cur      = new StringBuilder
    var curBytes = 0
    css.split("\n", -1).foreach { line =>
      val lineBytes = line.getBytes("UTF-8").length + 1
      if (cur.nonEmpty && curBytes + lineBytes > maxBytes) {
        out += cur.toString; cur.setLength(0); curBytes = 0
      }
      if (cur.nonEmpty) cur.append("\n")
      cur.append(line)
      curBytes += lineBytes
    }
    out += cur.toString
    out.toList
  }
}
