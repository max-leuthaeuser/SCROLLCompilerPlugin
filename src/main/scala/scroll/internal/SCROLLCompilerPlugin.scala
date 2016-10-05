package scroll.internal

import scala.tools.nsc
import nsc.{Global, Phase}
import nsc.plugins.{Plugin, PluginComponent}

class SCROLLCompilerPlugin(val global: Global) extends Plugin {
  val name = "SCROLLCompilerPlugin"
  val description = "Compiler Plugin to support SCROLL"
  val components = new SCROLLCompilerPluginComponent(this, global) :: Nil
}

class SCROLLCompilerPluginComponent(plugin: Plugin, val global: Global) extends PluginComponent {
  // TODO: check players for play relations

  import global._

  val runsAfter = "typer" :: Nil
  val phaseName = "dynamictraitlookup"

  private val ApplyDynamic = TermName("applyDynamic")
  private val SelectDynamic = TermName("selectDynamic")
  private val UpdateDynamic = TermName("updateDynamic")
  private val ApplyDynamicNamed = TermName("applyDynamicNamed")

  private val Wrapped = TermName("wrapped")

  private val config = new SCROLLCompilerPluginConfig()

  inform(s"Running the SCROLLCompilerPlugin with settings:\n${config.settings}")

  inform(s"Model '${config.modelFile}' was loaded.")

  inform("" + config.getPlays)

  def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

  private def showMessage(pos: Position, m: String): Unit = config.compileTimeErrors match {
    case true => globalError(pos, m)
    case false => warning(pos, m)
  }

  private class TraverserPhase(prev: Phase) extends StdPhase(prev) {
    def apply(unit: CompilationUnit) {
      newTraverser().traverse(unit.body)
    }
  }

  private def newTraverser(): Traverser = new ForeachTreeTraverser(check)

  private def getPlayerType(t: Tree): String =
    t.tpe.declarations.collectFirst {
      case m: MethodSymbol if m.name == Wrapped => m.typeSignatureIn(t.tpe) match {
        case NullaryMethodType(returnType) => returnType.toString()
      }
    }.getOrElse("No player found!")

  private def check(tree: Tree): Unit =
    tree match {
      case a@Select(t, dyn)
        if dyn == ApplyDynamic ||
          dyn == SelectDynamic ||
          dyn == UpdateDynamic ||
          dyn == ApplyDynamicNamed =>
        showMessage(a.pos, s"$dyn detected on: ${getPlayerType(t)}")
      case _ => ()
    }
}
