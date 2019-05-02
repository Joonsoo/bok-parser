package com.giyeok.jparser.examples

import com.giyeok.jparser.Grammar
import com.giyeok.jparser.gramgram.MetaGrammar

object SimpleGrammars {
    val arrayGrammar: Grammar = MetaGrammar.translateForce("SimpleArrayGrammar",
        """S = '[' WS elems WS ']'
          |elems = elem | elem WS ',' WS elems
          |elem = 'a'
          |WS = # | ' ' WS
        """.stripMargin)

    val array0Grammar: Grammar = MetaGrammar.translateForce("SimpleArray0Grammar",
        """S = '[' [WS E [WS ',' WS E]*]? WS ']'
          |E = 'a'
          |WS = ' '*
        """.stripMargin)

    val arrayOrObjectGrammar: Grammar = MetaGrammar.translateForce("SimpleArrayOrObjectGrammar",
        """S = '[' WS elems WS ']' | '{' WS elems WS '}'
          |elems = elem | elem WS ',' WS elems
          |elem = 'a'
          |WS = # | ' ' WS
        """.stripMargin)

    val earley1970ae: Grammar = MetaGrammar.translateForce("Earley 1970 AE",
        """E = T | E '+' T
          |T = P | T '*' P
          |P = 'a'
        """.stripMargin)

    val knuth1965_24: Grammar = MetaGrammar.translateForce("Knuth 1965 Grammar 24",
        """S = # | 'a' A 'b' S | 'b' B 'a' S
          |A = # | 'a' A 'b' A
          |B = # | 'b' B 'a' B
        """.stripMargin)

    val lexer1: Grammar = MetaGrammar.translateForce("SimpleLexerGrammar1",
        """S = T*
          |T = Kw | Id | P
          |Kw = "if"&W
          |Id = W-Kw
          |W = <{a-z}+>
          |P = ' '
        """.stripMargin)

    val lexer2: Grammar = MetaGrammar.translateForce("SimpleLexerGrammar2",
        """S = T*
          |T = Kw | Id | P
          |Kw = "xyz"&W
          |Id = W-Kw
          |W = <{a-z}+>
          |P = ' '
        """.stripMargin)

    val lexer2_1: Grammar = MetaGrammar.translateForce("SimpleLexerGrammar2_1",
        """S = T*
          |T = Kw | Id | P
          |Kw = "xyz"&W
          |Id = W-Kw
          |W = <({a-w} | 'x' | 'y' | 'z')+>
          |P = ' '
        """.stripMargin)

    val weird: Grammar = MetaGrammar.translateForce("WeirdGrammar1",
        """S = A B C | D E F
          |A = 'a'
          |D = 'a'
          |B = 'x' 'y'
          |E = 'x' 'y' 'z'
          |C = 'z'
          |F = 'q'
        """.stripMargin)
}
