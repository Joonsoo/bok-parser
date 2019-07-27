package com.giyeok.jparser.tests

import com.giyeok.jparser.Inputs
import org.scalatest.FlatSpec
import com.giyeok.jparser.Grammar
import com.giyeok.jparser.Symbols._
import com.giyeok.jparser.ParseResultTree
import com.giyeok.jparser.ParseResultGraph
import com.giyeok.jparser.ParseResultDerivationsSet
import com.giyeok.jparser.nparser.ParseTreeConstructor
import com.giyeok.jparser.ParseResultGraphFunc
import com.giyeok.jparser.nparser.NGrammar
import com.giyeok.jparser.ParsingErrors.ParsingError
import com.giyeok.jparser.ParsingErrors
import com.giyeok.jparser.ParseForest
import com.giyeok.jparser.nparser.NGrammar.{NExcept, NJoin, NLongest, NLookaheadIs, NLookaheadSymbol, NNonterminal, NOneOf, NProxy, NRepeat, NSequence, NStart, NTerminal}

class BasicParseTest(val testsSuite: Traversable[GrammarTestCases]) extends FlatSpec {
    def log(s: String): Unit = {
        // println(s)
    }

    private def testNGrammar(ngrammar: NGrammar): Unit = {
        import NGrammar._
        assert((ngrammar.nsymbols.keySet intersect ngrammar.nsequences.keySet).isEmpty)

        val symbolIds = ngrammar.nsymbols.keySet
        val allSymbolIds = symbolIds ++ ngrammar.nsequences.keySet
        ngrammar.nsymbols.values foreach {
            case NTerminal(_, _) => // do nothing
            case d: NSimpleDerive =>
                assert(d.produces subsetOf allSymbolIds)
            case NExcept(_, _, body, except) =>
                assert((symbolIds contains body) && (symbolIds contains except))
            case NJoin(_, _, body, join) =>
                assert((symbolIds contains body) && (symbolIds contains join))
            case NLongest(_, _, body) =>
                assert(symbolIds contains body)
            case l: NLookaheadSymbol =>
                assert(symbolIds contains l.lookahead)
        }
        ngrammar.nsequences.values foreach {
            case NSequence(_, _, sequence) =>
                assert(sequence forall { ngrammar.nsymbols.keySet contains _ })
                assert(sequence forall { id => !(ngrammar.nsequences.keySet contains id) })
        }
    }

    type R = ParseResultGraph
    val resultFunc = ParseResultGraphFunc

    def parse(tests: GrammarTestCases, source: Inputs.ConcreteSource): Either[R, ParsingError] = {
        // 여기서 nparser에 테스트하고싶은 파서 종류를 지정하면 됨
        val nparser = tests.naiveParser
        nparser.parse(source) match {
            case Left(ctx) =>
                val resultOpt = new ParseTreeConstructor(resultFunc)(nparser.grammar)(ctx.inputs, ctx.history, ctx.conditionFinal).reconstruct()
                resultOpt match {
                    case Some(result) => Left(result)
                    case None => Right(ParsingErrors.UnexpectedError)
                }
            case Right(error) => Right(error)
        }
    }

    private def testCorrect(tests: GrammarTestCases, source: Inputs.ConcreteSource): Unit = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        it should s"${tests.grammar.name} properly parsed on '${source.toCleanString}'" in {
            parse(tests, source) match {
                case Left(result) => checkParse(result, tests.grammar)
                case Right(error) => fail(error.msg)
            }
        }
    }

    private def testIncorrect(tests: GrammarTestCases, source: Inputs.ConcreteSource): Unit = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        it should s"${tests.grammar.name} failed to parse on '${source.toCleanString}'" in {
            parse(tests, source) match {
                case Left(result) => fail("??")
                case Right(error) => assert(true)
            }
        }
    }

    private def testAmbiguous(tests: GrammarTestCases, source: Inputs.ConcreteSource): Unit = {
        log(s"testing ${tests.grammar.name} on ${source.toCleanString}")
        it should s"${tests.grammar.name} is ambiguous on '${source.toCleanString}'" in {
            parse(tests, source) match {
                case Left(result) => checkParse(result, tests.grammar)
                case Right(error) => fail(error.msg)
            }
        }
    }

    private def checkParse(result: ParseForest, grammar: Grammar): Unit = {
        result.trees foreach { checkParse(_, grammar) }
    }

    private def checkParse(parseTree: ParseResultTree.Node, grammar: Grammar): Unit = {
        import ParseResultTree._
        parseTree match {
            case BindNode(term: Terminal, TerminalNode(input)) =>
                assert(term.accept(input))
            case BindNode(_: NStart, body @ BindNode(bodySym, _)) =>
                assert(grammar.startSymbol == bodySym)
                checkParse(body, grammar)
            case BindNode(NNonterminal(_, Nonterminal(name), _), body @ BindNode(bodySymbol, _)) =>
                assert(grammar.rules(name) contains bodySymbol.symbol)
                checkParse(body, grammar)
            case BindNode(NJoin(_, sym: Join, _, _), body @ JoinNode(BindNode(bodySym, _), BindNode(joinSym, _))) =>
                assert(sym.sym == bodySym)
                assert(sym.join == joinSym)
                checkParse(body, grammar)
            case BindNode(NSequence(_, Sequence(seq, _), _), seqBody: SequenceNode) =>
                assert((seqBody.children map { _.asInstanceOf[BindNode].symbol }) == seq)
                checkParse(seqBody, grammar)
            case BindNode(NOneOf(_, OneOf(syms), _), body @ BindNode(bodySymbol, _)) =>
                assert(syms contains bodySymbol.asInstanceOf[AtomicSymbol])
                checkParse(body, grammar)
            case BindNode(NRepeat(_, Repeat(sym, _), _, _), body @ BindNode(bodySymbol, _)) =>
                def childrenOf(node: Node, sym: Symbol): Seq[BindNode] = node match {
                    case node @ BindNode(s, body) if s == sym => Seq(node)
                    case BindNode(s, body) => childrenOf(body, sym)
                    case s: SequenceNode => s.children flatMap { childrenOf(_, sym) }
                }
                val children = childrenOf(body, sym)
                assert(children forall { _.symbol == sym })
                checkParse(body, grammar)
            case BindNode(NExcept(_, Except(sym, _), _, _), body @ BindNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindNode(NProxy(_, Proxy(sym), _), body @ BindNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindNode(NLongest(_, Longest(sym), _), body @ BindNode(bodySym, _)) =>
                assert(sym == bodySym)
                checkParse(body, grammar)
            case BindNode(_: NLookaheadSymbol, body) =>
                // TODO: empty sequence nsymbol id?
                assert(body == SequenceNode(NSequence(-1, Sequence(Seq()), Seq()), List()))
            case node: SequenceNode =>
                node.childrenAll foreach { checkParse(_, grammar) }
            case JoinNode(body, join) =>
                checkParse(body, grammar)
                checkParse(join, grammar)
            case BindNode(symbol, body) =>
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
        testNGrammar(test.ngrammar)
        test.correctSampleInputs foreach { testCorrect(test, _) }
        test.incorrectSampleInputs foreach { testIncorrect(test, _) }
        test match {
            case samples: AmbiguousSamples =>
                samples.ambiguousSampleInputs foreach { testAmbiguous(test, _) }
            case _ =>
        }
    }
}
