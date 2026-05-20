package scroll.internal

import dotty.tools.dotc.ast.tpd.*
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.Flags
import dotty.tools.dotc.core.Names.Name
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.core.Types.*
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.report
import dotty.tools.dotc.util.SourcePosition
import scroll.internal.util.ReflectiveHelper.simpleName

import scala.collection.mutable

class SCROLLCompilerPlugin extends StandardPlugin:
  val name: String        = "SCROLLCompilerPlugin"
  val description: String = "Compiler Plugin to support SCROLL"

  override def initialize(options: List[String])(using Context): List[PluginPhase] =
    DynamicTraitLookupPhase() :: Nil

class DynamicTraitLookupPhase extends PluginPhase:

  override val phaseName: String = "dynamictraitlookup"
  override val runsAfter: Set[String] = Set("typer")

  private val MAX_LINE_LENGTH = 30

  private val ApplyDynamic      = "applyDynamic"
  private val SelectDynamic     = "selectDynamic"
  private val UpdateDynamic     = "updateDynamic"
  private val ApplyDynamicNamed = "applyDynamicNamed"
  private val Wrapped           = "wrapped"
  private val Play              = "play"
  private val Transfer          = "transfer"
  private val To                = "to"
  private val Drop              = "drop"

  private val TypeCreator = "$typecreator"
  private val Anon        = "$anon"

  private val nameMapping = mutable.Map.empty[String, String]

  private case class LoggedDynamic(t: Tree, dyn: String, name: Tree, args: Seq[Type])

  private val loggedDynamics = mutable.ArrayBuffer.empty[LoggedDynamic]

  private sealed trait DynExtType

  private case object PlayExt extends DynExtType {
    override def toString: String = Play
  }

  private case object TransferExt extends DynExtType {
    override def toString: String = Transfer
  }

  private case object DropExt extends DynExtType {
    override def toString: String = Drop
  }

  private case class AppliedDynExt(t: DynExtType, pos: SourcePosition, player: String, dynExt: String) {
    override def toString: String = pos.source.toString match {
      case s if s.length >= MAX_LINE_LENGTH =>
        s"$t: [line:${pos.line}|col:${pos.column}] at source '${s.substring(0, 19)}.../${pos.source.file.name}'"
      case s =>
        s"$t: [line:${pos.line}|col:${pos.column}] at source '$s'"
    }
  }

  private val appliedDynExts = mutable.ArrayBuffer.empty[AppliedDynExt]

  private val playerMapping = mutable.Map.empty[String, Symbol]

  private val config = new SCROLLCompilerPluginConfig()

  private var bootstrapped = false

  private def bootstrap()(using Context): Unit =
    if !bootstrapped then
      bootstrapped = true
      report.inform(s"Running the SCROLLCompilerPlugin with settings:\n${config.settings}")
      report.inform(s"The following fills relations are specified:\n${prettyPrintFills()}")

  private def showMessage(pos: SourcePosition, m: String)(using Context): Unit =
    if config.compileTimeErrors then report.error(m, pos)
    else report.warning(m, pos)

  private val WrappedName = "wrapped".toTermName

  private def getPlayerType(t: Tree)(using Context): String =
    val wrapped = t.tpe.member(WrappedName)
    if wrapped.exists then
      simpleName(wrapped.info.widen.show)
    else
      val s = simpleName(t.tpe.show)
      showMessage(t.sourcePos, s"No player for '$s' found!")
      s

  private def collectDyns(tree: Tree)(using Context): Unit = tree match
    case Apply(TypeApply(Select(qual, tplay), _), role :: Nil) if isTermName(tplay, Play) =>
      appliedDynExts.append(
        AppliedDynExt(PlayExt, qual.sourcePos, simpleName(qual.tpe.show), simpleName(role.tpe.show))
      )
    case Apply(Select(qual, tplay), role :: Nil) if isTermName(tplay, Play) =>
      appliedDynExts.append(
        AppliedDynExt(PlayExt, qual.sourcePos, simpleName(qual.tpe.show), simpleName(role.tpe.show))
      )
    case Apply(Select(Apply(Select(Apply(Select(_, transfer), role :: Nil), toe), to :: Nil), _), _)
        if isTermName(transfer, Transfer) && isTermName(toe, To) =>
      appliedDynExts.append(
        AppliedDynExt(TransferExt, to.sourcePos, simpleName(to.tpe.show), simpleName(role.tpe.show))
      )
    case Apply(TypeApply(Select(qual, tdrop), _), role :: Nil) if isTermName(tdrop, Drop) =>
      appliedDynExts.append(
        AppliedDynExt(DropExt, qual.sourcePos, simpleName(qual.tpe.show), simpleName(role.tpe.show))
      )
    case Apply(Select(qual, tdrop), role :: Nil) if isTermName(tdrop, Drop) =>
      appliedDynExts.append(
        AppliedDynExt(DropExt, qual.sourcePos, simpleName(qual.tpe.show), simpleName(role.tpe.show))
      )
    case td: TypeDef if td.symbol.isClass && !isSyntheticName(td.name) =>
      playerMapping(td.name.toString) = td.symbol
    case ValDef(name, _, Literal(Constant(v))) =>
      nameMapping(name.toString) = sanitizeName(v.toString)
    case Apply(Select(qual, dyn), name :: Nil) if isTermName(dyn, UpdateDynamic) =>
      loggedDynamics.append(LoggedDynamic(qual, UpdateDynamic, name, Nil))
    case Apply(TypeApply(Select(qual, dyn), _), name :: Nil) if isTermName(dyn, SelectDynamic) =>
      loggedDynamics.append(LoggedDynamic(qual, SelectDynamic, name, Nil))
    case Apply(Apply(TypeApply(Select(qual, dyn), _), name :: Nil), args)
        if isTermName(dyn, ApplyDynamicNamed) || isTermName(dyn, ApplyDynamic) =>
      loggedDynamics.append(LoggedDynamic(qual, dyn.toString, name, args.map(_.tpe)))
    case _ => ()

  private def isTermName(name: Name, expected: String): Boolean =
    name.toString == expected

  private def isSyntheticName(name: Name): Boolean =
    val decoded = name.toString
    decoded.contains(TypeCreator) || decoded.contains(Anon)

  private def looselyMatch(arg: Type, param: Type)(using Context): Boolean =
    val a = arg.widen.dealias
    val p = param.widen.dealias
    a =:= p || a <:< p || p <:< a

  private def matchMethod(m: Symbol, name: String, args: Seq[Type])(using Context): Boolean =
    val matchName       = m.name.toString == name
    val params          = m.paramSymss.flatten.map(_.info)
    val matchParamCount = params.length == args.length
    val matchArgTypes   = args.zip(params).forall { case (a, p) => looselyMatch(a, p) }
    matchName && m.is(Flags.Method) && matchParamCount && matchArgTypes

  private def hasBehavior(pt: String, m: String, args: Seq[Type])(using Context): List[String] =
    val players =
      playerMapping.get(pt).toList ++ getRoles(pt).flatMap(playerMapping.get)
    players.collect {
      case sym if sym.info.decls.exists(d => matchMethod(d, m, args)) =>
        sym.name.toString
    }

  private def getRoles(p: String): List[String] =
    def rec_getRoles(rp: String): List[String] = config.getPlays.flatMap {
      case (e, rl) if e == rp => List(e, rl)
      case (pl, e) if e == rp => rec_getRoles(pl)
      case _                  => Nil
    }

    (rec_getRoles(p) ++ appliedDynExts.collect {
      case AppliedDynExt(et, _, pl, e) if (et == PlayExt || et == TransferExt) && pl == p => e
      case AppliedDynExt(et, _, pl, e) if (et == PlayExt || et == TransferExt) && e == p => pl
    }).distinct

  private def sanitizeName(e: String): String = e.replaceAll("\"", "")

  private def prettyPrintFills(): String = config.getPlays match
    case Nil  => "\tNone found."
    case list => list.map(p => s"- '${p._1}' -> '${p._2}'").mkString("\t", "\n\t", "")

  private def prettyPrintFills(p: String): String = getRoles(p).filter(_ != p) match
    case Nil =>
      s"For '$p' no dynamic extensions are specified."
    case list =>
      s"For '$p' the following dynamic extensions are specified:\n\t\t" +
        list.map(d => s"- '$p' -> '$d'").mkString("\n\t\t")

  private def prettyPrintExtensions(m: Map[String, List[AppliedDynExt]]): String =
    m.map {
      case (k, v) if v.nonEmpty =>
        s"- '$k' may be acquired/dropped as correct dynamic extension at:\n${v.mkString("\t\t\t", "\n\t\t\t", "")}"
      case (k, _) => s"- '$k'"
    }.mkString("\t", "\n\t\t", "")

  private def prettyPrintArgs(args: Seq[Type]): String = args match
    case Nil  => ""
    case list => list.mkString("(", ", ", ")")

  private def hasPlays(player: String, dynExt: String): List[AppliedDynExt] =
    appliedDynExts.filter(p => (p.player == player && p.dynExt == dynExt) || (p.player == dynExt && p.dynExt == player)).toList

  private def printLoggedDynamics(loggedDynamic: LoggedDynamic)(using Context): Unit =
    val LoggedDynamic(t, dyn, name, args) = loggedDynamic

    val pt = getPlayerType(t)
    val n  = sanitizeName(name.toString)
    val b  = nameMapping.getOrElse(n, n)

    val bList = hasBehavior(pt, b, args).distinct
    val hasB  = bList.nonEmpty

    val outA = s"$dyn as '$b${prettyPrintArgs(args)}' detected on: '$pt'.\n\t${prettyPrintFills(pt)}"
    val out = hasB match
      case true =>
        val fills  = getRoles(pt).filter(_ != pt).diff(bList)
        val extMap = bList.map { e =>
          hasPlays(pt, e) match
            case Nil  => e -> fills.flatMap(el => hasPlays(e, el))
            case list => e -> list
        }.toMap
        outA + s"\n\tMake sure at least one of the following dynamic extensions is bound:\n\t${prettyPrintExtensions(extMap)}"
      case false => outA
    showMessage(t.sourcePos, out)
    if !hasB then
      showMessage(
        name.sourcePos,
        s"Neither '$pt', nor its dynamic extensions offer the called behavior!\n\tThis may indicate a programming error!"
      )

  override def transformUnit(tree: Tree)(using Context): Tree =
    bootstrap()
    val acc = new TreeAccumulator[Unit]:
      def apply(x: Unit, t: Tree)(using Context): Unit =
        collectDyns(t)
        foldOver(x, t)
    acc((), tree)
    loggedDynamics.foreach(printLoggedDynamics)
    tree
