package scroll.internal

import scala.tools.nsc
import nsc.Global
import nsc.plugins.{Plugin,PluginComponent}
import nsc.transform.Transform
import nsc.symtab.Flags._
import nsc.ast.TreeDSL

class SCROLLCompilerPlugin(val global: Global) extends Plugin {
  val name = "SCROLLCompilerPlugin"
  val description = "Compiler Plugin to support SCROLL"
  val components = new SCROLLCompilerPluginTransform(this, global) :: Nil
}

class SCROLLCompilerPluginTransform(plugin: Plugin, val global: Global) extends PluginComponent with Transform with TreeDSL {
  import global._

  val runsAfter = "parser" :: Nil
  val phaseName = "dynamictraitlookup"

  val ImplyName = TermName("imply")

  def newTransformer(unit: CompilationUnit) = new Transformer() {
    override def transform(tree: Tree): Tree = {
      tree match {
        case Apply(Apply(Ident(ImplyName), implicits), blocks) =>
          if (blocks.size == 1) super.transform(expandImplicits(implicits, blocks.head))
          else super.transform(tree)

        case other => super.transform(other)
      }
    }
  }

  private def expandImplicits(implicits: List[Tree], block: Tree): Tree = {
    val implicitVals = implicits.zipWithIndex map { case (imp, idx) =>
      ValDef(Modifiers(IMPLICIT), TermName("implied$" + idx), TypeTree(), imp)
    }
    Block(implicitVals, block)
  }
}
