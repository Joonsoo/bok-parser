package com.giyeok.jparser.tests

import com.giyeok.jparser.Inputs
import org.scalatest.FlatSpec
import com.giyeok.jparser.Grammar
import com.giyeok.jparser.NewParser
import com.giyeok.jparser.ParseForestFunc
import com.giyeok.jparser.DerivationSliceFunc
import com.giyeok.jparser.Symbols._
import com.giyeok.jparser.ParseResultTree
import com.giyeok.jparser.ParseResultGraph
import com.giyeok.jparser.ParseResultDerivationsSet

class BasicParseTest(val testsSuite: Traversable[GrammarTestCases]) extends FlatSpec {
    def log(s: String): Unit = {
        println(s)
    }

    private def testCorrect(tests: GrammarTestCases)(source: Inputs.ConcreteSource) = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        val result = tests.parser.parse(source)
        it should s"${tests.grammar.name} properly parsed on '${source.toCleanString}'" in {
            result match {
                case Left(ctx) =>
                    assert(ctx.result.isDefined)
                    //                    val trees = ctx.result.get.trees
                    //                    if (trees.size != 1) {
                    //                        trees.zipWithIndex foreach { result =>
                    //                            log(s"=== ${result._2} ===")
                    //                            log(result._1.toHorizontalHierarchyString)
                    //                        }
                    //                    }
                    //                    assert(trees.size == 1)
                    //                    trees foreach { checkParse(_, tests.grammar) }
                    checkParse(ctx.result.get, tests.grammar)
                case Right(error) => fail(error.msg)
            }
        }
    }

    private def testIncorrect(tests: GrammarTestCases)(source: Inputs.ConcreteSource) = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        val result = tests.parser.parse(source)
        it should s"${tests.grammar.name} failed to parse on '${source.toCleanString}'" in {
            result match {
                case Left(ctx) => assert(ctx.result.isEmpty)
                case Right(_) => assert(true)
            }
        }
    }

    private def testAmbiguous(tests: GrammarTestCases)(source: Inputs.ConcreteSource) = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        val result = tests.parser.parse(source)
        it should s"${tests.grammar.name} is ambiguous on '${source.toCleanString}'" in {
            result match {
                case Left(ctx) =>
                    assert(ctx.result.isDefined)
                    //                    val trees = ctx.result.get.trees
                    //                    assert(trees.size > 1)
                    //                    trees foreach { checkParse(_, tests.grammar) }
                    checkParse(ctx.result.get, tests.grammar)
                case Right(_) => assert(false)
            }
        }
    }

    private def checkParse(parseTree: ParseResultTree.Node, grammar: Grammar): Unit = {
        import ParseResultTree._
        parseTree match {
            case TermFuncNode => ??? // should not happen
            case BindedNode(term: Terminal, TerminalNode(input)) =>
                assert(term.accept(input))
            case BindedNode(Start, body @ BindedNode(bodySym, _)) =>
                assert(grammar.startSymbol == bodySym)
                checkParse(body, grammar)
            case BindedNode(Nonterminal(name), body @ BindedNode(bodySymbol, _)) =>
                assert(grammar.rules(name) contains bodySymbol)
                checkParse(body, grammar)
            case BindedNode(sym: Join, body @ JoinNode(BindedNode(bodySym, _), BindedNode(joinSym, _))) =>
                assert(sym.sym == bodySym)
                assert(sym.join == joinSym)
                checkParse(body, grammar)
            case BindedNode(Sequence(seq, ws), seqBody: SequenceNode) =>
                assert((seqBody.children map { _.asInstanceOf[BindedNode].symbol }) == seq)
                checkParse(seqBody, grammar)
            case BindedNode(OneOf(syms), body @ BindedNode(bodySymbol, _)) =>
                assert(syms contains bodySymbol)
                checkParse(body, grammar)
            case BindedNode(Repeat(sym, _), body @ BindedNode(bodySymbol, _)) =>
                def childrenOf(node: Node, sym: Symbol): Seq[BindedNode] = node match {
                    case node @ BindedNode(s, body) if s == sym => Seq(node)
                    case BindedNode(s, body) => childrenOf(body, sym)
                    case s: SequenceNode => s.children flatMap { childrenOf(_, sym) }
                }
                val children = childrenOf(body, sym)
                assert(children forall { _.symbol == sym })
                checkParse(body, grammar)
            case BindedNode(Except(sym, _), body @ BindedNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindedNode(Proxy(sym), body @ BindedNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindedNode(Backup(sym, backup), body @ BindedNode(bodySym, _)) =>
                assert((sym == bodySym) || (backup == bodySym))
                checkParse(body, grammar)
            case BindedNode(Longest(sym), body @ BindedNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindedNode(EagerLongest(sym), body @ BindedNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindedNode(_: LookaheadIs | _: LookaheadExcept, body) =>
                assert(body == SequenceNode(List(), List()))
            case node: SequenceNode =>
                node.childrenWS foreach { checkParse(_, grammar) }
            case JoinNode(body, join) =>
                checkParse(body, grammar)
                checkParse(join, grammar)
            case BindedNode(symbol, body) =>
                println(symbol)
                ???
            case _ =>
                println(parseTree)
                ???
        }
    }

    private def checkParse(result: ParseResultDerivationsSet, grammar: Grammar): Unit = {
        // TODO
    }

    private def checkParse(parseGraph: ParseResultGraph, grammar: Grammar): Unit = {
        // TODO
    }

    testsSuite foreach { test =>
        test.correctSampleInputs foreach { testCorrect(test) }
        test.incorrectSampleInputs foreach { testIncorrect(test) }
        if (test.isInstanceOf[AmbiguousSamples]) test.asInstanceOf[AmbiguousSamples].ambiguousSampleInputs foreach { testAmbiguous(test) }
    }
}
