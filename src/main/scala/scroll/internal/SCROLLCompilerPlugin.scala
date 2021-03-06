package scroll.internal

import scroll.internal.util.ReflectiveHelper.simpleName

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

  private val MAX_LINE_LENGTH = 30

  private val ApplyDynamic = TermName("applyDynamic")
  private val SelectDynamic = TermName("selectDynamic")
  private val UpdateDynamic = TermName("updateDynamic")
  private val ApplyDynamicNamed = TermName("applyDynamicNamed")
  private val Wrapped = TermName("wrapped")
  private val Play = TermName("play")
  private val Transfer = TermName("transfer")
  private val To = TermName("to")
  private val Drop = TermName("drop")

  private val TypeCreator = "$typecreator"
  private val Anon = "$anon"

  private val nameMapping = mutable.Map.empty[String, String]

  private case class LoggedDynamic(t: Tree, dyn: Name, name: Tree, args: Seq[Type])

  private val loggedDynamics = mutable.ArrayBuffer.empty[LoggedDynamic]

  private sealed trait DynExtType

  private case object PlayExt extends DynExtType {
    override def toString: String = Play.toString
  }

  private case object TransferExt extends DynExtType {
    override def toString: String = Transfer.toString
  }

  private case object DropExt extends DynExtType {
    override def toString: String = Drop.toString
  }

  private case class AppliedDynExt(t: DynExtType, pos: Position, player: String, dynExt: String) {
    override def toString: String = pos.source.toString() match {
      case s if s.length >= MAX_LINE_LENGTH => s"$t: [line:${pos.line}|col:${pos.column}] at source '${s.substring(0, 19)}.../${pos.source.file.name}'"
      case s => s"$t: [line:${pos.line}|col:${pos.column}] at source '$s'"
    }
  }

  private val appliedDynExts = mutable.ArrayBuffer.empty[AppliedDynExt]

  private val playerMapping = mutable.Map.empty[String, ClassDef]

  private val config = new SCROLLCompilerPluginConfig()

  inform(s"Running the SCROLLCompilerPlugin with settings:\n${config.settings}")

  inform(s"The following fills relations are specified:\n${prettyPrintFills()}")

  def newPhase(prev: Phase): Phase = new TraverserPhase(prev)

  private def showMessage(pos: Position, m: String): Unit = config.compileTimeErrors match {
    case true => r.error(pos, m)
    case false => r.warning(pos, m)
  }

  private def getPlayerType(t: Tree): String =
    t.tpe.declarations.collectFirst {
      case m: MethodSymbol if m.name == Wrapped => m.typeSignatureIn(t.tpe) match {
        case NullaryMethodType(returnType) => simpleName(returnType.toString())
      }
    }.getOrElse {
      val s = simpleName(t.tpe.toString())
      showMessage(t.pos, s"No player for '$s' found!")
      s
    }

  private class TraverserPhase(prev: Phase) extends StdPhase(prev) {
    def apply(unit: CompilationUnit): Unit = {
      new ForeachTreeTraverser(collectDyns).traverse(unit.body)
      loggedDynamics.foreach(printLoggedDynamics)
    }
  }

  private def collectDyns(tree: Tree): Unit = tree match {
    // find all plays:
    case Apply(Apply(TypeApply(Select(t, Play), _), _), args) =>
      val TypeRef(_, _, ttp) = t.tpe
      val TypeRef(_, _, ttr) = args.head.tpe
      appliedDynExts.append(AppliedDynExt(PlayExt, t.pos, simpleName(ttp.head.toString), simpleName(ttr.head.toString)))
    // find all transfer to:
    case Apply(Apply(TypeApply(Select(Apply(Apply(TypeApply(Select(_, Transfer), _), List(role)), _), To), _), List(to)), _) =>
      val t = to.tpe
      val r = role.tpe
      appliedDynExts.append(AppliedDynExt(TransferExt, to.pos, simpleName(t.toString), simpleName(r.toString)))
    // find all drops:
    case Apply(Apply(TypeApply(Select(t, Drop), _), _), args) =>
      val TypeRef(_, _, ttp) = t.tpe
      val TypeRef(_, _, ttr) = args.head.tpe
      appliedDynExts.append(AppliedDynExt(DropExt, t.pos, simpleName(ttp.head.toString), simpleName(ttr.head.toString)))
    // find all player classes:
    case c@ClassDef(_, name, _, _) if !name.decode.contains(TypeCreator) && !name.decode.contains(Anon) =>
      playerMapping(name.decode) = c
    // find all player behavior:
    case ValDef(_, name, _, Literal(Constant(v))) =>
      nameMapping(name.decode) = sanitizeName(v.toString)
    // find all calls to Dynamic Trait:
    case Apply(Select(t, UpdateDynamic), List(name)) =>
      loggedDynamics.append(LoggedDynamic(t, UpdateDynamic, name, List.empty))
    case Apply(TypeApply(Select(t, SelectDynamic), _), List(name)) =>
      loggedDynamics.append(LoggedDynamic(t, SelectDynamic, name, List.empty))
    case Apply(Apply(TypeApply(Select(t, dyn), _), List(name)), args) if dyn == ApplyDynamicNamed || dyn == ApplyDynamic =>
      loggedDynamics.append(LoggedDynamic(t, dyn, name, args.map(_.tpe)))
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

  private def hasBehavior(pt: String, m: String, args: Seq[Type]): List[String] = {
    val p = playerMapping.get(pt) match {
      case Some(player) => List(player)
      case None => List.empty[ClassDef]
    }

    (getRoles(pt).map(r => playerMapping(r)) ++ p).collect {
      case cl if cl.symbol.typeSignature.members.exists(matchMethod(_, m, args)) => cl.name.decode
    }
  }

  private def getRoles(p: String): List[String] = {
    def rec_getRoles(rp: String): List[String] = config.getPlays.flatMap {
      case (e, rl) if e == rp => List(e, rl)
      case (pl, e) if e == rp => rec_getRoles(pl)
      case _ => List()
    }

    (rec_getRoles(p) ++ appliedDynExts.collect {
      case AppliedDynExt(et, _, pl, e) if (et == PlayExt || et == TransferExt) && pl == p => e
      case AppliedDynExt(et, _, pl, e) if (et == PlayExt || et == TransferExt) && e == p => pl
    }).distinct
  }

  private def sanitizeName(e: String): String = e.replaceAll("\"", "")

  private def prettyPrintFills(): String = config.getPlays match {
    case Nil => "\tNone found."
    case list => list.map(p => s"- '${p._1}' -> '${p._2}'").mkString("\t", "\n\t", "")
  }

  private def prettyPrintFills(p: String): String = getRoles(p).filter(_ != p) match {
    case Nil => s"For '$p' no dynamic extensions are specified."
    case list => s"For '$p' the following dynamic extensions are specified:\n\t\t" + list.map(d => s"- '$p' -> '$d'").mkString("\n\t\t")
  }

  private def prettyPrintExtensions(m: Map[String, List[AppliedDynExt]]): String =
    m.map {
      case (k, v) if v.nonEmpty => s"- '$k' may be acquired/dropped as correct dynamic extension at:\n${v.mkString("\t\t\t", "\n\t\t\t", "")}"
      case (k, v) => s"- '$k'"
    }.mkString("\t", "\n\t\t", "")

  private def prettyPrintArgs(args: Seq[Type]): String = args match {
    case Nil => ""
    case list => list.mkString("(", ", ", ")")
  }

  private def hasPlays(player: String, dynExt: String): List[AppliedDynExt] =
    appliedDynExts.filter(p => (p.player == player && p.dynExt == dynExt) || (p.player == dynExt && p.dynExt == player)).toList

  private def printLoggedDynamics(loggedDynamic: LoggedDynamic): Unit = {
    val LoggedDynamic(t, dyn, name, args) = loggedDynamic

    val pt = getPlayerType(t)
    val n = sanitizeName(name.toString)
    val b = nameMapping.getOrElse(n, n)

    val bList = hasBehavior(pt, b, args).distinct
    val hasB = bList.nonEmpty

    val outA = s"$dyn as '$b${prettyPrintArgs(args)}' detected on: '$pt'.\n\t${prettyPrintFills(pt)}"
    val out = hasB match {
      case true =>
        val fills = getRoles(pt).filter(_ != pt).diff(bList)
        val extMap = bList.map(e => {
          hasPlays(pt, e) match {
            case Nil => e -> fills.flatMap(el => hasPlays(e, el))
            case list => e -> list
          }
        }).toMap
        outA + s"\n\tMake sure at least one of the following dynamic extensions is bound:\n\t${prettyPrintExtensions(extMap)}"
      case false =>
        outA
    }
    showMessage(t.pos, out)
    if (!hasB)
      showMessage(name.pos, s"Neither '$pt', nor its dynamic extensions offer the called behavior!\n\tThis may indicate a programming error!")
  }
}
