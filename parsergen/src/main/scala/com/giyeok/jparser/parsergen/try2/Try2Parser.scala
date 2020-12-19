package com.giyeok.jparser.parsergen.try2

import com.giyeok.jparser.Inputs.Input
import com.giyeok.jparser.metalang3a.generated.LongestMatchAst
import com.giyeok.jparser.nparser.AcceptCondition._
import com.giyeok.jparser.nparser.ParseTreeConstructor2.Kernels
import com.giyeok.jparser.nparser.ParsingContext.Kernel
import com.giyeok.jparser.nparser.{AcceptCondition, ParseTreeConstructor2}
import com.giyeok.jparser.parsergen.try2.Try2.{KernelTemplate, PrecomputedParserData, TasksSummary}
import com.giyeok.jparser.{Inputs, NGrammar, ParseForest, ParseForestFunc}

object Try2Parser {
  def reconstructParseTree(grammar: NGrammar, finalCtx: Try2ParserContext, input: Seq[Input]): Option[ParseForest] = {
    val kernels = finalCtx.actionsHistory.map(gen => Kernels(gen.flatMap {
      case TermAction(beginGen, midGen, endGen, summary) =>
        def genOf(gen: Int) = gen match {
          case 0 => beginGen
          case 1 => midGen
          case 2 => endGen
        }

        (summary.finishedKernels ++ summary.progressedKernels.map(_._1)).map(_.kernel).map { kernel =>
          Kernel(kernel.symbolId, kernel.pointer, genOf(kernel.beginGen), genOf(kernel.endGen))
        }
      case EdgeAction(parentBeginGen, beginGen, midGen, endGen, summary, condition) =>
        // TODO accept condition으로 필터링
        def genOf(gen: Int) = gen match {
          case 0 => parentBeginGen
          case 1 => beginGen
          case 2 => midGen
          case 3 => endGen
        }

        (summary.finishedKernels ++ summary.progressedKernels.map(_._1)).map(_.kernel).map { kernel =>
          Kernel(kernel.symbolId, kernel.pointer, genOf(kernel.beginGen), genOf(kernel.endGen))
        }
    }.toSet))
    new ParseTreeConstructor2(ParseForestFunc)(grammar)(input, kernels).reconstruct()
  }

  def main(args: Array[String]): Unit = {
    //    val parserData = Try2.precomputedParserData(ExpressionGrammar.ngrammar)
    //    new Try2Parser(parserData).parse("1*2+34")
    //    val grammar = ArrayExprAst.ngrammar
    //    val valuefier = ArrayExprAst.matchStart _
    //    val input = Inputs.fromString("[a,a,a]")

    val grammar = LongestMatchAst.ngrammar
    val input = Inputs.fromString("abcdefgh   a  b c d")

    val parserData = Try2.precomputedParserData(grammar)
    val finalCtx = new Try2Parser(parserData).parse(input)
    val parseTree = reconstructParseTree(grammar, finalCtx, input).get.trees.head
    parseTree.printTree()
    //    val ast = valuefier(parseTree)
    //    println(ast)
  }
}

class Try2Parser(val parserData: PrecomputedParserData) {
  def initialCtx: Try2ParserContext = Try2ParserContext(
    List(Milestone(None, parserData.grammar.startSymbol, 0, 0, Always)),
    List(List(TermAction(0, 0, 0, parserData.byStart))))

  def parse(inputSeq: Seq[Inputs.Input]): Try2ParserContext = {
    println("=== initial")
    initialCtx.tips.foreach(t => println(t.prettyString))
    inputSeq.zipWithIndex.foldLeft(initialCtx) { (m, i) =>
      val (nextInput, gen0) = i
      val gen = gen0 + 1
      println(s"=== $gen $nextInput")
      val next = proceed(m, gen, nextInput)
      next
    }
  }

  def parse(input: String): Try2ParserContext = parse(Inputs.fromString(input))

  private class ProceedProcessor(var genActions: List[GenAction] = List()) {
    private def transformTermActionCondition(condition: AcceptCondition, parentGen: Int, beginGen: Int, endGen: Int): AcceptCondition = {
      def genOf(gen: Int) = gen match {
        case 0 => parentGen
        case 1 => beginGen
        case 2 => endGen
        case 3 => endGen + 1
      }

      condition match {
        case AcceptCondition.Always | AcceptCondition.Never => condition
        case AcceptCondition.And(conditions) => conjunct(conditions.map(transformTermActionCondition(_, parentGen, beginGen, endGen)).toSeq: _*)
        case AcceptCondition.Or(conditions) => disjunct(conditions.map(transformTermActionCondition(_, parentGen, beginGen, endGen)).toSeq: _*)
        case AcceptCondition.NotExists(beginGen, endGen, symbolId) => AcceptCondition.NotExists(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.Exists(beginGen, endGen, symbolId) => AcceptCondition.Exists(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.Unless(beginGen, endGen, symbolId) => AcceptCondition.Unless(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.OnlyIf(beginGen, endGen, symbolId) => AcceptCondition.OnlyIf(genOf(beginGen), genOf(endGen), symbolId)
      }
    }

    def proceed(ctx: Try2ParserContext, gen: Int, input: Inputs.Input): List[Milestone] = ctx.tips.flatMap { tip =>
      val parentGen = tip.parent.map(_.gen).getOrElse(0)
      val termActions = parserData.termActions(tip.kernelTemplate)
      termActions.find(_._1.contains(input)) match {
        case Some((_, action)) =>
          genActions +:= TermAction(parentGen, tip.gen, gen, action.tasksSummary)
          // action.appendingMilestones를 뒤에 덧붙인다
          val appended = action.appendingMilestones.map { appending =>
            val kernelTemplate = appending._1
            val acceptCondition = transformTermActionCondition(appending._2, parentGen, tip.gen, gen)
            Milestone(Some(tip), kernelTemplate.symbolId, kernelTemplate.pointer, gen,
              AcceptCondition.conjunct(tip.acceptCondition, acceptCondition))
          }
          // action.startNodeProgressConditions가 비어있지 않으면 tip을 progress 시킨다
          val reduced = progressTip(tip, gen,
            materializeStartProgressConditions(parentGen, tip.gen, gen, action.startNodeProgressConditions)
              .map(conjunct(_, tip.acceptCondition)))
          appended ++ reduced
        case None => List()
      }
    }

    private def materializeStartProgressConditions(parentGen: Int, beginGen: Int, endGen: Int, conditions: List[AcceptCondition]): List[AcceptCondition] =
      conditions.map(transformEdgeActionCondition(_, -1, parentGen, beginGen, endGen))

    private def transformEdgeActionCondition(condition: AcceptCondition, parentBeginGen: Int, parentGen: Int, beginGen: Int, endGen: Int): AcceptCondition = {
      def genOf(gen: Int) = gen match {
        case 0 => parentBeginGen
        case 1 => parentGen
        case 2 => beginGen
        case 3 => endGen
        case 4 => endGen + 1
      }

      condition match {
        case AcceptCondition.Always | AcceptCondition.Never => condition
        case AcceptCondition.And(conditions) => conjunct(conditions.map(transformEdgeActionCondition(_, parentBeginGen, parentGen, beginGen, endGen)).toSeq: _*)
        case AcceptCondition.Or(conditions) => disjunct(conditions.map(transformEdgeActionCondition(_, parentBeginGen, parentGen, beginGen, endGen)).toSeq: _*)
        case AcceptCondition.NotExists(beginGen, endGen, symbolId) => AcceptCondition.NotExists(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.Exists(beginGen, endGen, symbolId) => AcceptCondition.Exists(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.Unless(beginGen, endGen, symbolId) => AcceptCondition.Unless(genOf(beginGen), genOf(endGen), symbolId)
        case AcceptCondition.OnlyIf(beginGen, endGen, symbolId) => AcceptCondition.OnlyIf(genOf(beginGen), genOf(endGen), symbolId)
      }
    }

    private def progressTip(tip: Milestone, gen: Int, acceptConditions: List[AcceptCondition]): List[Milestone] =
      acceptConditions.flatMap { condition =>
        // (tip.parent-tip) 사이의 엣지에 대한 edge action 실행
        tip.parent match {
          case Some(parent) =>
            val parentBeginGen = parent.parent.map(_.gen).getOrElse(0)
            val edgeAction = parserData.edgeProgressActions((parent.kernelTemplate, tip.kernelTemplate))
            genActions +:= EdgeAction(parentBeginGen, parent.gen, tip.gen, gen, edgeAction.tasksSummary, condition)
            // TODO tip.acceptCondition은 이미 그 뒤에 붙었던 milestone에서 처리됐으므로 무시해도 될듯?
            // tip은 지워지고 tip.parent - edgeAction.appendingMilestones 가 추가됨
            val appended = edgeAction.appendingMilestones.map { appending =>
              val appendingCondition = transformEdgeActionCondition(appending._2, parentBeginGen, parent.gen, tip.gen, gen)
              Milestone(Some(parent), appending._1.symbolId, appending._1.pointer, gen,
                conjunct(condition, appendingCondition))
            }
            // edgeAction.startNodeProgressConditions에 대해 위 과정 반복 수행
            val propagated = progressTip(parent, gen,
              materializeStartProgressConditions(parent.gen, tip.gen, gen, edgeAction.startNodeProgressConditions)
                .map(conjunct(condition, _)))
            appended ++ propagated
          case None =>
            // 파싱 종료
            // TODO 어떻게 처리하지?
            List()
        }
      }
  }

  private def evaluateAcceptCondition(milestones: List[Milestone], acceptCondition: AcceptCondition,
                                      gen: Int, genActions: List[GenAction]): AcceptCondition =
    acceptCondition match {
      case AcceptCondition.Always => Always
      case AcceptCondition.Never => Never
      case AcceptCondition.And(conditions) =>
        val evaluated = conditions.map(evaluateAcceptCondition(milestones, _, gen, genActions))
        conjunct(evaluated.toSeq: _*)
      case AcceptCondition.Or(conditions) =>
        disjunct(conditions.map(evaluateAcceptCondition(milestones, _, gen, genActions)).toSeq: _*)
      case AcceptCondition.NotExists(_, endGen, _) if gen < endGen => acceptCondition
      case AcceptCondition.NotExists(beginGen, endGen, symbolId) =>
        // genAction을 통해서 symbolId가 (beginGen..endGen+) 에서 match될 수 있으면 매치되는 조건을,
        // genAction을 통해서는 이 accept condition을 확인할 수 없으면 그대로 반환
        // TODO genAction을 통해서는 이 accept condition을 확인할 수 있는 경우는 전체 milestone들을 확인해야 알 수 있음..
        // -> milestone들 중에 milestone.gen이 beginGen과 같고, 해당 milestone에서 derive돼서 이 symbolId가 나올 수 있으면 아직 미확정
        val metaConditions0 = genActions.filter(_.endGen >= endGen).flatMap {
          case termAction: TermAction =>
            val metaKernel = if (termAction.beginGen == beginGen) Kernel(symbolId, 1, 0, 2) else Kernel(symbolId, 1, 1, 2)
            // 여기서 symbol은 항상 atomic symbol이므로 progress되면 바로 finish되기 때문에 progressed는 고려할 필요 없을듯.
            termAction.summary.finishedKernels.filter(_.kernel == metaKernel).map(_.condition)
          case edgeAction: EdgeAction =>
            val metaKernel = if (edgeAction.beginGen == beginGen) Kernel(symbolId, 1, 1, 3) else Kernel(symbolId, 1, 2, 3)
            // 여기서도 마찬가지로 symbol은 항상 atomic이므로 finish만 고려하면 됨
            edgeAction.summary.finishedKernels.filter(_.kernel == metaKernel).map(_.condition)
        }.distinct
          .map(_.neg)

        // TODO needToPreserve가 좀 이상함..
        def needToPreserve(milestone: Milestone): Boolean = {
          if (milestone.gen == beginGen) {
            parserData.derivedGraph(milestone.kernelTemplate).nodes
              .exists(_.kernel == Kernel(symbolId, 0, 0, 0))
          } else if (milestone.gen > beginGen) {
            milestone.parent.exists(needToPreserve)
          } else false
        }

        val metaConditions = if (milestones.exists(needToPreserve)) acceptCondition +: metaConditions0 else metaConditions0
        conjunct(metaConditions: _*)
      case AcceptCondition.Exists(beginGen, endGen, symbolId) =>
        val effectiveActions = genActions.filter(_.endGen >= endGen)
        ???
      case AcceptCondition.Unless(beginGen, endGen, symbolId) =>
        val effectiveActions = genActions.filter(_.endGen == endGen)
        ???
      case AcceptCondition.OnlyIf(beginGen, endGen, symbolId) =>
        val effectiveActions = genActions.filter(_.endGen == endGen)
        ???
    }

  def proceed(ctx: Try2ParserContext, gen: Int, input: Inputs.Input): Try2ParserContext = {
    val processor = new ProceedProcessor()
    val milestones0 = processor.proceed(ctx, gen, input)
    // TODO processor.genActions를 바탕으로 milestones 필터링.
    // TODO -> 그런데 milestone의 tip에 있지 않은 컨디션들은? "tip이 아닌 마일스톤의 컨디션도 고려해야 하는지" 역시 문법의 특성으로 얻어내서 별도로 처리할 수 있지 않을까
    //    println("  ** before evaluating accept condition")
    milestones0.foreach(t => println(t.prettyString))
    val milestones = milestones0.flatMap { milestone =>
      val newCond = evaluateAcceptCondition(milestones0, milestone.acceptCondition, gen, processor.genActions)
      if (newCond == Never) None else Some(milestone.copy(acceptCondition = newCond))
    }
    println("  ** after evaluating accept condition")
    milestones.foreach(t => println(t.prettyString))
    Try2ParserContext(milestones, ctx.actionsHistory :+ processor.genActions)
  }
}

case class Milestone(parent: Option[Milestone], symbolId: Int, pointer: Int, gen: Int, acceptCondition: AcceptCondition) {
  def kernelTemplate: KernelTemplate = KernelTemplate(symbolId, pointer)

  private def myself = s"($symbolId $pointer $gen ${acceptCondition})"

  def prettyString: String = parent match {
    case Some(value) => s"${value.prettyString} $myself"
    case None => myself
  }
}

sealed trait GenAction {
  val beginGen: Int
  val midGen: Int
  val endGen: Int
  val summary: TasksSummary
}

case class TermAction(beginGen: Int, midGen: Int, endGen: Int, summary: TasksSummary) extends GenAction

case class EdgeAction(parentBeginGen: Int, beginGen: Int, midGen: Int, endGen: Int, summary: TasksSummary, condition: AcceptCondition) extends GenAction

// TODO edge action - 체인 관계를 어떻게..?

case class Try2ParserContext(tips: List[Milestone], actionsHistory: List[List[GenAction]])
