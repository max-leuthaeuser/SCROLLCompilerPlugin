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

  private val availablePlayer = config.getPlays.flatMap { case (a, b) => List(a, b) }.distinct

  inform(s"Running the SCROLLCompilerPlugin with settings:\n${config.settings}")

  inform(s"Model '${config.modelFile}' was loaded.")

  inform(s"The following fills relations are specified:\n${prettyPrintFills()}")

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
      // find all player behavior:
      new ForeachTreeTraverser(findBehavior).traverse(unit.body)
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

  private def findBehavior(tree: Tree): Unit = tree match {
    case ValDef(_, name, _, Literal(Constant(v))) =>
      nameMapping(name.decoded) = sanitizeName(v.toString)
    case _ => ()
  }

  private def matchMethod(m: Symbol, name: String, args: Seq[Type]): Boolean = {
    lazy val matchName = m.name.encodedName.toString == name
    lazy val params = m.asMethod.paramLists.flatten.map(_.typeSignature)
    lazy val matchParamCount = params.length == args.size
    lazy val matchArgTypes = args.zip(params).forall {
      case (a, p) => a.looselyMatches(p)
    }
    matchName && matchParamCount && matchArgTypes
  }

  private def hasBehavior(pt: String, m: String, args: Seq[Type]): List[String] =
    (getRoles(pt).map(r => playerMapping(r)) :+ playerMapping(pt)).collect {
      case cl if cl.symbol.typeSignature.members.exists(matchMethod(_, m, args)) => cl.name.decode.toString
    }

  private def getRoles(p: String): List[String] =
    config.getPlays.flatMap {
      case (e, rl) if e == p => List(e, rl)
      case (pl, e) if e == p => getRoles(pl)
      case _ => List()
    }.distinct

  private def sanitizeName(e: String): String = e.replaceAll("\"", "")

  private def prettyPrintFills(): String =
    config.getPlays.map(p => s"'${p._1}' -> '${p._2}'").mkString("\t", "\n\t", "")

  private def prettyPrintFills(p: String): String =
    getRoles(p).filter(_ != p).map(d => s"'$p' -> '$d'").mkString("\n\t\t")

  private def prettyPrintExtensions(l: List[String]): String =
    l.map(e => s"- '$e'").mkString("\t", "\n\t\t", "")

  private def prettyPrintArgs(args: Seq[Type]): String = args.isEmpty match {
    case true => ""
    case false => args.mkString("(", ", ", ")")
  }

  private def logDynamics(t: Tree, dyn: Name, name: Tree, args: Seq[Type]): Unit = {
    val pt = getPlayerType(t)
    val n = sanitizeName(name.toString)
    val b = nameMapping.getOrElse(n, n)

    val bList = hasBehavior(pt, b, args)
    val hasB = bList.nonEmpty

    val outA = s"$dyn as '$b${prettyPrintArgs(args)}' detected on: '$pt'.\n\tFor '$pt' the following dynamic extensions are specified in '${config.modelFile}':\n\t\t${prettyPrintFills(pt)}"
    val out = hasB match {
      case true =>
        outA + s"\n\tMake sure at least one of the following dynamic extensions is bound:\n\t${prettyPrintExtensions(bList.distinct)}"
      case false =>
        outA
    }
    showMessage(t.pos, out)
    if (!hasB)
      showMessage(name.pos, s"Neither '$pt', nor its dynamic extensions specified in '${config.modelFile}' offer the called behavior!\n\tThis may indicate a programming error!")
  }

  private def handleDynamics(tree: Tree): Unit = tree match {
    case Apply(Select(t, dyn), List(name)) if dyn == UpdateDynamic =>
      logDynamics(t, dyn, name, List.empty)
    case Apply(TypeApply(Select(t, dyn), _), List(name)) if dyn == SelectDynamic =>
      logDynamics(t, dyn, name, List.empty)
    case Apply(Apply(TypeApply(Select(t, dyn), _), List(name)), args) if dyn == ApplyDynamicNamed || dyn == ApplyDynamic =>
      logDynamics(t, dyn, name, args.map(_.tpe))
    case _ => ()
  }
}
