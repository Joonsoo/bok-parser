package com.giyeok.jparser.examples.basics

import com.giyeok.jparser.Grammar
import com.giyeok.jparser.GrammarHelper._
import com.giyeok.jparser.examples.{GrammarWithExamples, StringExamples}

import scala.collection.immutable.{ListMap, ListSet}

object LookaheadExceptGrammar1 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar1 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            seq(chars('a' to 'z').star, lookahead_except(chars('a' to 'z'))),
            chars(" ")))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar2 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar2 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            seq(chars('a' to 'z').star, lookahead_except(seq(chars('a' to 'z')))),
            chars(" ")))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar3 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar3 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(seq(chars('a' to 'z').plus, lookahead_except(chars('a' to 'z'))), chars(" ")))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar3_1 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar3_1 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("T").star),
        "T" -> ListSet(seq(oneof(n("I").except(n("K")), n("K")), lookahead_except(chars('a' to 'z'))), n("WS")),
        "I" -> ListSet(chars('a' to 'z').plus),
        "K" -> ListSet(i("if"), i("for")),
        "WS" -> ListSet(chars(" ")))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def", "if abc", "ifk ifk if ifk", "if", "ifk")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar3_2 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar3_2 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("T").star),
        "T" -> ListSet(seq(oneof(n("I"), n("K")), lookahead_except(chars('a' to 'z'))), n("WS")),
        "I" -> ListSet(chars('a' to 'z').plus.except(n("K"))),
        "K" -> ListSet(i("if"), i("for")),
        "WS" -> ListSet(chars(" ")))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def", "if abc", "ifk ifk if ifk", "if", "ifk")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar4 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar4 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            n("Id"),
            n("Num"),
            n("WS"),
            n("Str"),
            chars("()+-*/.,")),
        "Id" -> ListSet(
            seq(chars('a' to 'z'), chars('a' to 'z', '0' to '9').star, lookahead_except(chars('a' to 'z', '0' to '9')))),
        "Num" -> ListSet(
            seq(chars('0' to '9'), lookahead_except(chars('0' to '9')))),
        "WS" -> ListSet(
            chars(" \t\n")),
        "Str" -> ListSet(
            seq(c('"'), chars('a' to 'z', 'A' to 'Z', '0' to '9', ' ' to ' ').star, c('"'))))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar5 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar5 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            n("Id"),
            n("Num"),
            n("WS"),
            n("Str"),
            chars("()+-*/.,")),
        "Id" -> ListSet(
            seq(chars('a' to 'z'), chars('a' to 'z').star, lookahead_except(chars('a' to 'z')))),
        "Num" -> ListSet(
            seq(chars('0' to '9'), lookahead_except(chars('0' to '9')))),
        "WS" -> ListSet(
            chars(" \t\n")),
        "Str" -> ListSet(
            seq(c('"'), chars('a' to 'z', 'A' to 'Z', '0' to '9', ' ' to ' ').star, c('"'))))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar6 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar6 - longest match"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            n("Id"),
            n("Num"),
            n("WS"),
            n("Str"),
            chars("()+-*/.,")),
        "Id" -> ListSet(
            seq(chars('a' to 'z').star, chars('a' to 'z'), lookahead_except(chars('a' to 'z')))),
        "Num" -> ListSet(
            seq(chars('0' to '9'), lookahead_except(chars('0' to '9')))),
        "WS" -> ListSet(
            chars(" \t\n")),
        "Str" -> ListSet(
            seq(c('"'), chars('a' to 'z', 'A' to 'Z', '0' to '9', ' ' to ' ').star, c('"'))))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc def")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar7 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar7"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("A").star),
        "A" -> ListSet(
            seq(chars('a' to 'z').star, lookahead_except(c(' '))),
            c(' ')))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set("abc", "abc")
    val incorrectExamples = Set[String]()
}

object LookaheadExceptGrammar9 extends Grammar with GrammarWithExamples with StringExamples {
    val name = "LookaheadExceptGrammar9"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(n("Token").star),
        "Token" -> ListSet(
            n("Name"),
            n("Keyword"),
            chars(" ()")),
        "Word" -> ListSet(
            seq(n("FirstChar"), n("SecondChar").star, lookahead_except(n("SecondChar")))),
        "Name" -> ListSet(
            n("Word").except(n("Keyword"))),
        "Keyword" -> ListSet(
            i("var"),
            i("if")),
        "FirstChar" -> ListSet(
            chars('a' to 'z', 'A' to 'Z')),
        "SecondChar" -> ListSet(
            chars('a' to 'z', 'A' to 'Z', '0' to '9')))
    val startSymbol = n("S")

    val grammar = this
    val correctExamples = Set[String]()
    val incorrectExamples = Set[String]()
}

object GrammarWithLookaheadExcept {
    // Grammar 1, 2, 7 are double-* ambiguous language
    val tests: Set[GrammarWithExamples] = Set(
        // LookaheadExceptGrammar1,
        // LookaheadExceptGrammar2,
        LookaheadExceptGrammar3,
        LookaheadExceptGrammar3_1,
        LookaheadExceptGrammar3_2,
        LookaheadExceptGrammar4,
        LookaheadExceptGrammar5,
        LookaheadExceptGrammar6,
        // LookaheadExceptGrammar7,
        LookaheadExceptGrammar9)
}
