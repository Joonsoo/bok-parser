package com.giyeok.jparser.examples.metalang3

import com.giyeok.jparser.examples.{MetaLang3Example, MetaLangExamples}

object SimpleExamples extends MetaLangExamples {
    val ex1: MetaLang3Example = MetaLang3Example("Nested class construct",
        """A = B&X {MyClass(value=$0, qs=[])} | B&X Q {MyClass(value=$0, qs=$1)}
          |B = C 'b'
          |C = 'c' D
          |D = 'd' | #
          |X = 'c' 'd'* 'b'
          |Q = 'q'+ {QValue(value="hello")}
          |""".stripMargin)
        .example("cb")
        .example("cdb")
        .example("cbqq")
    val ex2: MetaLang3Example = MetaLang3Example("Raw ref",
        """A = ('b' Y 'd') 'x' {A(val=$0$1, raw=$0\\$1, raw1=\\$0)}
          |Y = 'y' {Y(value=true)}
          |""".stripMargin)
        .example("bydx")
    val ex3: MetaLang3Example = MetaLang3Example("BinOp on str",
        """A = B ' ' C {ClassA(value=str($0) + str($2))}
          |B = 'a'
          |C = 'b'
          |""".stripMargin)
        .example("a b", "ClassA(\"ab\")")
    val ex4: MetaLang3Example = MetaLang3Example("Nonterm type inference",
        """expression: Expression = term
          |    | expression WS '+' WS term {BinOp(op=str($2), lhs:Expression=$0, rhs=$4)}
          |term: Term = factor
          |    | term WS '*' WS factor {BinOp(str($2), $0, $4)}
          |factor: Factor = number
          |    | variable
          |    | '(' WS expression WS ')' {Paren(expr=$2)}
          |number: Number = '0' {Integer(value=str($0))}
          |    | '1-9' '0-9'* {Integer(str(\\$0, \\$1))}
          |variable = <'A-Za-z'+> {Variable(name=$0)}
          |WS = ' '*
          |""".stripMargin)
        .example("123+456", "BinOp(\"+\",Integer(\"123\"),Integer(\"456\"))")
    val ex5: MetaLang3Example = MetaLang3Example("Canonical enum",
        """A = "hello" {%MyEnum.Hello} | "xyz" {%MyEnum.Xyz}
          |""".stripMargin)
        .example("hello", "%MyEnum.Hello")
        .example("xyz", "%MyEnum.Xyz")
    val ex6a: MetaLang3Example = MetaLang3Example("Shortened enum",
        """A:%MyEnum = "hello" {%Hello} | "xyz" {%Xyz}
          |""".stripMargin)
        .example("hello", "%MyEnum.Hello")
        .example("xyz", "%MyEnum.Xyz")
    val ex6b: MetaLang3Example = MetaLang3Example("Shortened enum some in other nonterminal",
        """A:%MyEnum = B | "xyz" {%Xyz}
          |B = "hello" {%Hello}
          |""".stripMargin)
        .example("hello", "%MyEnum.Hello")
        .example("xyz", "%MyEnum.Xyz")
    val ex6c: MetaLang3Example = MetaLang3Example("Shortened enum in other nonterminal",
        """A:%MyEnum = B
          |B = "hello" {%Hello} | "xyz" {%Xyz}
          |""".stripMargin)
        .example("hello", "%MyEnum.Hello")
        .example("xyz", "%MyEnum.Xyz")
    val ex6d: MetaLang3Example = MetaLang3Example("Shortened enum in other nonterminal with more values",
        """A:%MyEnum = B | "qwer" {%Qwer} | "tyui" {%Tyui}
          |B = "hello" {%Hello} | "xyz" {%Xyz}
          |""".stripMargin)
        .example("hello", "%MyEnum.Hello")
        .example("xyz", "%MyEnum.Xyz")
        .example("qwer", "%MyEnum.Qwer")
        .example("tyui", "%MyEnum.Tyui")
    val ex7: MetaLang3Example = MetaLang3Example("repeat*",
        """A = ('h' 'Ee' "ll" 'Oo' {str($1) + str($3)})*
          |""".stripMargin)
        .example("hello", "[\"eo\"]")
        .example("hEllO", "[\"EO\"]")
    val ex7a: MetaLang3Example = MetaLang3Example("BindExpr on repeat*",
        """A = ('h' 'Ee' "ll" 'Oo')* {$0{str($1) + str($3)}}
          |""".stripMargin)
        .example("hello", "[\"eo\"]")
        .example("hEllO", "[\"EO\"]")
    val ex7b: MetaLang3Example = MetaLang3Example("repeat+",
        """A = ('h' 'Ee' "ll" 'Oo' {str($1) + str($3)})+ {$0}
          |""".stripMargin)
        .example("hello", "[\"eo\"]")
        .example("hEllO", "[\"EO\"]")
    val ex7c: MetaLang3Example = MetaLang3Example("BindExpr on repeat+",
        """A = ('h' 'Ee' "ll" 'Oo')+ {$0{str($1) + str($3)}}
          |""".stripMargin)
        .example("hello", "[\"eo\"]")
        .example("hEllO", "[\"EO\"]")
    val ex8: MetaLang3Example = MetaLang3Example("Simple expression with enum",
        """expression: Expression = term
          |    | expression WS ('+' {%Add}) WS term {BinOp(op:%Op=$2, lhs:Expression=$0, rhs=$4)}
          |term: Term = factor
          |    | term WS ('*' {%Mul}) WS factor {BinOp($2, $0, $4)}
          |factor: Factor = number
          |    | variable
          |    | '(' WS expression WS ')' {Paren(expr=$2)}
          |number: Number = '0' {Integer(value=str($0))}
          |    | '1-9' '0-9'* {Integer(str(\\$0, \\$1))}
          |variable = <'A-Za-z'+> {Variable(name=$0)}
          |WS = ' '*
          |""".stripMargin)
        .example("123+456", "BinOp(%Op.Add,Integer(\"123\"),Integer(\"456\"))")
        .example("0*(123+456)", "BinOp(%Op.Mul,Integer(\"0\"),Paren(BinOp(%Op.Add,Integer(\"123\"),Integer(\"456\"))))")

    override val examples: List[MetaLang3Example] =
        List(ex1, ex2, ex3, ex4, ex5, ex6a, ex6b, ex6c, ex6d, ex7, ex7a, ex7b, ex7c, ex8)
}
