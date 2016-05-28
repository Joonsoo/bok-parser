package com.giyeok.jparser.tests.basics

import com.giyeok.jparser.Grammar
import com.giyeok.jparser.GrammarHelper._
import scala.collection.immutable.ListMap
import com.giyeok.jparser.deprecated.Parser
import com.giyeok.jparser.Inputs._
import scala.collection.immutable.ListSet
import com.giyeok.jparser.deprecated.Parser
import com.giyeok.jparser.tests.BasicParseTest
import com.giyeok.jparser.tests.Samples
import com.giyeok.jparser.tests.StringSamples
import com.giyeok.jparser.tests.AmbiguousSamples

object SimpleGrammar5 extends Grammar with StringSamples {
    val name = "Simple Grammar 5"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(n("A"), n("B"))),
        "A" -> ListSet(i("a"), e),
        "B" -> ListSet(i("b"), e))
    val startSymbol = n("S")

    val correctSamples = Set("", "a", "b", "ab")
    val incorrectSamples = Set("aa")
}

object SimpleGrammar6 extends Grammar with StringSamples {
    val name = "Simple Grammar 6"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(n("A"), n("C"))),
        "A" -> ListSet(seq(n("B"), i("a").star)),
        "B" -> ListSet(i("b"), e),
        "C" -> ListSet(seq(n("B"), i("c").star)))
    val startSymbol = n("S")

    val correctSamples = Set("", "ab", "c", "ccc", "abc", "aa", "aaabccc")
    val incorrectSamples = Set("cb")
}

object SimpleGrammar7_1 extends Grammar with StringSamples {
    val name = "Simple Grammar 7-1 (right associative)"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("Exp")),
        "Exp0" -> ListSet(
            n("Id"),
            n("Num"),
            seq(i("("), n("Num"), i(")"))),
        "Exp1" -> ListSet(
            n("Exp0"),
            seq(n("Exp0"), i("*"), n("Exp1"))),
        "Exp2" -> ListSet(
            n("Exp1"),
            seq(n("Exp1"), i("+"), n("Exp2"))),
        "Exp" -> ListSet(
            n("Exp2")),
        "Id" -> ListSet(
            elongest(chars('a' to 'z').plus)),
        "Num" -> ListSet(
            elongest(chars('0' to '9').plus)))
    val startSymbol = n("S")

    val correctSamples = Set(
        "1+2+3",
        "123+456+abc+sdf+123+wer+aasdfwer+123123",
        "123*456*abc*sdf*123*wer*aasdfwer*123123",
        "123+456*abc+sdf*123+wer*aasdfwer*123123")
    val incorrectSamples = Set("")
}

object SimpleGrammar7_2 extends Grammar with StringSamples {
    val name = "Simple Grammar 7-2 (left associative)"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("Exp")),
        "Exp0" -> ListSet(
            n("Id"),
            n("Num"),
            seq(i("("), n("Num"), i(")"))),
        "Exp1" -> ListSet(
            n("Exp0"),
            seq(n("Exp1"), i("*"), n("Exp0"))),
        "Exp2" -> ListSet(
            n("Exp1"),
            seq(n("Exp2"), i("+"), n("Exp1"))),
        "Exp" -> ListSet(
            n("Exp2")),
        "Id" -> ListSet(
            elongest(chars('a' to 'z').plus)),
        "Num" -> ListSet(
            elongest(chars('0' to '9').plus)))
    val startSymbol = n("S")

    val correctSamples = Set(
        "1+2+3",
        "123+456+abc+sdf+123+wer+aasdfwer+123123",
        "123*456*abc*sdf*123*wer*aasdfwer*123123",
        "123+456*abc+sdf*123+wer*aasdfwer*123123")
    val incorrectSamples = Set("")
}

object SimpleGrammar8 extends Grammar with StringSamples {
    val name = "Simple Grammar 8"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("A").star),
        "A" -> ListSet(
            seq(c('('), n("A"), c(')')),
            c('0')))
    val startSymbol = n("S")

    val correctSamples = Set[String](
        "(((0)))(((0)))",
        "(0)(0)(0)",
        "")
    val incorrectSamples = Set[String]()
}

object SimpleGrammar8_1 extends Grammar with StringSamples {
    val name = "Simple Grammar 8-1 (ambiguous)"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            seq(c('('), n("S"), c(')')),
            seq(i("("), n("S"), i(")")),
            c('0')))
    val startSymbol = n("S")

    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
}

object SimpleGrammar8_2 extends Grammar with StringSamples with AmbiguousSamples {
    val name = "Simple Grammar 8-2 (ambiguous)"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            seq(c('a').opt, c('a').opt, c('a').opt, c('a').opt)))
    val startSymbol = n("S")

    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String](
        "a")
}

object SimpleGrammar9 extends Grammar with StringSamples {
    val name = "Simple Grammar 9 (Infinitely ambiguous)"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            e,
            seq(n("S"), n("A"))),
        "A" -> ListSet(
            e,
            seq(n("A"), c('a'))))
    val startSymbol = n("S")

    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
}

object AsteriskNullable extends Grammar with StringSamples {
    val name = "*-nullable"
    val rules: RuleMap = ListMap(
        "S" -> ListSet((chars('a' to 'z').opt).star))
    val startSymbol = n("S")

    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
}

object SimpleGrammarSet3 {
    val grammars: Set[Grammar with Samples] = Set(
        SimpleGrammar5,
        SimpleGrammar6,
        SimpleGrammar7_1,
        SimpleGrammar7_2,
        SimpleGrammar8,
        SimpleGrammar8_1,
        SimpleGrammar8_2,
        SimpleGrammar9,
        AsteriskNullable)
}

class SimpleGrammarTestSuite3 extends BasicParseTest(SimpleGrammarSet3.grammars)
