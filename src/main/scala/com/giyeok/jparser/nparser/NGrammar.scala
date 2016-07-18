package com.giyeok.jparser.nparser

import com.giyeok.jparser.Grammar
import com.giyeok.jparser.Symbols

// Numbered Grammar
case class NGrammar(nsymbols: Map[Int, NGrammar.NAtomicSymbol], nsequences: Map[Int, NGrammar.Sequence], startSymbol: Int) {
    def symbolOf(id: Int): NGrammar.NSymbol = {
        nsymbols get id match {
            case Some(nsymbol) => nsymbol
            case None => nsequences(id)
        }
    }
}

object NGrammar {
    sealed trait NSymbol { val symbol: Symbols.Symbol }
    sealed trait NAtomicSymbol extends NSymbol

    case class Terminal(symbol: Symbols.Terminal) extends NAtomicSymbol
    case class Start(produces: Set[Int]) extends NAtomicSymbol { val symbol = Symbols.Start }
    case class Nonterminal(symbol: Symbols.Nonterminal, produces: Set[Int]) extends NAtomicSymbol
    case class OneOf(symbol: Symbols.OneOf, produces: Set[Int]) extends NAtomicSymbol
    case class Proxy(symbol: Symbols.Proxy, produce: Int) extends NAtomicSymbol
    case class Repeat(symbol: Symbols.Repeat, produces: Set[Int]) extends NAtomicSymbol
    case class Join(symbol: Symbols.Join, body: Int, join: Int) extends NAtomicSymbol
    case class Except(symbol: Symbols.Except, body: Int, except: Int) extends NAtomicSymbol
    case class Longest(symbol: Symbols.Longest, body: Int) extends NAtomicSymbol
    case class EagerLongest(symbol: Symbols.EagerLongest, body: Int) extends NAtomicSymbol

    sealed trait NLookaheadSymbol extends NAtomicSymbol {
        val symbol: Symbols.Lookahead
        val lookahead: Int
    }
    case class LookaheadIs(symbol: Symbols.LookaheadIs, lookahead: Int) extends NLookaheadSymbol
    case class LookaheadExcept(symbol: Symbols.LookaheadExcept, lookahead: Int) extends NLookaheadSymbol

    // case class Compaction(symbols: Seq[Int]) extends NSymbol

    case class Sequence(symbol: Symbols.Sequence, sequence: Seq[Int]) extends NSymbol

    def fromGrammar(grammar: Grammar): NGrammar = {
        var newId = 0
        val symbolsMap = scala.collection.mutable.Map[Symbols.Symbol, Int]()
        val nsymbols = scala.collection.mutable.Map[Int, NAtomicSymbol]()
        val nsequences = scala.collection.mutable.Map[Int, Sequence]()

        def numberOf(symbol: Symbols.Symbol): Int =
            symbolsMap get symbol match {
                case Some(nsymbol) => nsymbol
                case None =>
                    newId += 1
                    symbolsMap(symbol) = newId
                    symbol match {
                        case symbol: Symbols.AtomicSymbol =>
                            nsymbols(newId) = symbol match {
                                case Symbols.Start => Start(Set(numberOf(grammar.startSymbol)))
                                case symbol: Symbols.Terminal => Terminal(symbol)
                                case symbol: Symbols.Nonterminal => Nonterminal(symbol, grammar.rules(symbol.name) map { numberOf _ })
                                case symbol: Symbols.OneOf => OneOf(symbol, symbol.syms map { numberOf _ })
                                case symbol: Symbols.Proxy => Proxy(symbol, numberOf(symbol.sym))
                                case symbol: Symbols.Repeat => Repeat(symbol, Set(numberOf(symbol.baseSeq), numberOf(symbol.repeatSeq)))
                                case symbol: Symbols.Join => Join(symbol, numberOf(symbol.sym), numberOf(symbol.join))
                                case symbol: Symbols.Except => Except(symbol, numberOf(symbol.sym), numberOf(symbol.except))
                                case symbol: Symbols.Longest => Longest(symbol, numberOf(symbol.sym))
                                case symbol: Symbols.EagerLongest => EagerLongest(symbol, numberOf(symbol.sym))
                                case symbol: Symbols.LookaheadIs => LookaheadIs(symbol, numberOf(symbol.lookahead))
                                case symbol: Symbols.LookaheadExcept => LookaheadExcept(symbol, numberOf(symbol.except))
                            }
                        case symbol: Symbols.Sequence =>
                            nsequences(newId) = Sequence(symbol, symbol.seq map { numberOf _ })
                    }
                    newId
            }
        val startSymbolId = numberOf(grammar.startSymbol)
        NGrammar(nsymbols.toMap, nsequences.toMap, startSymbolId)
    }
}