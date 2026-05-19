package scroll.internal

import dotty.tools.dotc._
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}

class SCROLLCompilerPlugin extends StandardPlugin {
  val name: String                 = "SCROLLCompilerPlugin"
  override val description: String = "Compiler plugin that highlights SCROLL dynamic dispatch sites"

  override def initialize(options: List[String])(using Context): List[PluginPhase] =
    List(new SCROLLCompilerPluginPhase)
}

private class SCROLLCompilerPluginPhase extends PluginPhase {
  import tpd.*

  override val phaseName: String        = "scrollDynamicLookup"
  override val runsAfter: Set[String]   = Set(Pickler.name)
  override val runsBefore: Set[String]  = Set(Staging.name)

  private val DynamicNames = Set("applyDynamic", "applyDynamicNamed", "selectDynamic", "updateDynamic")

  override def transformApply(tree: Apply)(using Context): Tree = {
    tree match {
      case Apply(Select(receiver, dyn), nameArg :: Nil) if DynamicNames.contains(dyn.show) =>
        warn(receiver, dyn.show, extractName(nameArg), tree)
      case Apply(Apply(Select(receiver, dyn), nameArgs), _) if DynamicNames.contains(dyn.show) =>
        warn(receiver, dyn.show, nameArgs.headOption.flatMap(extractName), tree)
      case _ =>
    }
    tree
  }

  private def extractName(tree: Tree): Option[String] = tree match {
    case Literal(Constant(name: String)) => Some(name)
    case NamedArg(_, value)              => extractName(value)
    case _                               => None
  }

  private def warn(receiver: Tree, dyn: String, member: Option[String], tree: Tree)(using Context): Unit = {
    val receiverType = simpleName(receiver.tpe.show)
    val memberName   = member.getOrElse("<unknown>")
    report.warning(s"$dyn as '$memberName' detected on '$receiverType'.", tree.sourcePos)
  }

  private def simpleName(fullName: String): String =
    fullName.split("[.$]").lastOption.getOrElse(fullName)
}
