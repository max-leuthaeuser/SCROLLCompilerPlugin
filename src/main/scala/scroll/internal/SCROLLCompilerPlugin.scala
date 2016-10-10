package scroll.internal

import scroll.internal.util.ReflectiveHelper

import scala.tools.nsc
import nsc.{Global, Phase}
import nsc.plugins.{Plugin, PluginComponent}
import scala.collection.mutable

class SCROLLCompilerPlugin(val global: Global) extends Plugin {
  val name = "SCROLLCompilerPlugin"
  val description = "Compiler Plugin to support SCROLL"
  val components = new SCROLLCompilerPluginComponent(this, global) :: Nil
}

class SCROLLCompilerPluginComponent(plugin: Plugin, val global: Global) extends PluginComponent {

  import global._

  private val r = global.reporter

  val runsAfter = "typer" :: Nil
  val phaseName = "dynamictraitlookup"

  private val ApplyDynamic = TermName("applyDynamic")
  private val SelectDynamic = TermName("selectDynamic")
  private val UpdateDynamic = TermName("updateDynamic")
  private val ApplyDynamicNamed = TermName("applyDynamicNamed")
  private val Wrapped = TermName("wrapped")

  private val nameMapping = mutable.Map.empty[String, String]

  private val playerMapping = mutable.Map.empty[String, ClassDef]

  private val config = new SCROLLCompilerPluginConfig()

  private val availablePlayer = config.getPlays.flatMap { case (a, b) => List(a, b) }

  inform(s"Running the SCROLLCompilerPlugin with settings:\n${config.settings}")

  inform(s"Model '${config.modelFile}' was loaded.")

  def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

  private def showMessage(pos: Position, m: String): Unit = config.compileTimeErrors match {
    case true => r.error(pos, m)
    case false => r.warning(pos, m)
  }

  private def getPlayerType(t: Tree): String =
    t.tpe.declarations.collectFirst {
      case m: MethodSymbol if m.name == Wrapped => m.typeSignatureIn(t.tpe) match {
        case NullaryMethodType(returnType) => ReflectiveHelper.simpleName(returnType.toString())
      }
    }.getOrElse("No player found!")

  private class TraverserPhase(prev: Phase) extends StdPhase(prev) {
    def apply(unit: CompilationUnit): Unit = {
      // find all player classes:
      new ForeachTreeTraverser(findPlayer).traverse(unit.body)
      // handle calls to Dynamic Trait:
      new ForeachTreeTraverser(handleDynamics).traverse(unit.body)
    }
  }

  private def findPlayer(tree: Tree): Unit = tree match {
    case c@ClassDef(_, name, _, _) =>
      val n = name.decode.toString
      if (availablePlayer.contains(n)) {
        playerMapping(n) = c
      }
    case _ => ()
  }

  private def hasBehavior(c: ClassDef, m: String): Boolean = c.symbol.typeSignature.members.exists(_.name.encodedName.toString == m)

  private def getRoles(p: String): List[String] = config.getPlays.filter { case (c, r) => c == p } map (_._2)

  private def logDynamics(t: Tree, dyn: Name, name: Tree): Unit = {
    val pt = getPlayerType(t)
    val pc = playerMapping(pt)
    val rcs = getRoles(pt).map(r => playerMapping.getOrElse(r, null)).filter(_ != null)
    val b = nameMapping(name.toString)
    val hasB = (rcs :+ pc).exists(cl => hasBehavior(cl, b))
    r.warning(t.pos, s"$dyn detected on: $pt.\n\tFor that player the following roles are specified in ${config.modelFile}:\n\t${getRoles(pt).mkString(", ")}")
    if (!hasB) {
      showMessage(name.pos, s"Neither $pt, nor its allowed roles specified in ${config.modelFile} offer the called behavior!\n\tThis may indicate a programming error!")
    }
  }

  private def handleDynamics(tree: Tree): Unit = tree match {
    case ValDef(_, name, _, Literal(Constant(v))) =>
      nameMapping(name.toString) = v.toString
    case Apply(Select(t, dyn), List(name)) if dyn == UpdateDynamic =>
      logDynamics(t, dyn, name)
    case Apply(TypeApply(Select(t, dyn), _), List(name)) if dyn == ApplyDynamic || dyn == SelectDynamic || dyn == ApplyDynamicNamed =>
      logDynamics(t, dyn, name)
    case _ => ()
  }
}
