package com.giyeok.jparser.gramgram.meta2

import com.giyeok.jparser.ParseForestFunc
import com.giyeok.jparser.nparser.{NaiveParser, ParseTreeConstructor}

object MetaGrammar2 {
    val parser = new NaiveParser(GrammarDef.oldGrammar)

    def grammarSpecToAST(grammar: String): Option[AST.Grammar] = {
        val result = parser.parse(grammar)

        result match {
            case Left(ctx) =>
                val reconstructor = new ParseTreeConstructor(ParseForestFunc)(GrammarDef.oldGrammar)(ctx.inputs, ctx.history, ctx.conditionFinal)
                reconstructor.reconstruct() match {
                    case Some(parseForest) =>
                        assert(parseForest.trees.size == 1)
                        val tree = parseForest.trees.head

                        Some(ASTifier.matchGrammar(tree))
                    case None =>
                        println("Incomplete input")
                        None
                }
            case Right(error) =>
                println(error)
                None
        }
    }

    def main(args: Array[String]): Unit = {
        val expressionGrammar =
            """expression: @Expression = term
              |    | expression '+' term {@BinOp(op=$1, lhs:Expression=$0, rhs=$2)}
              |term: @Term = factor
              |    | term '*' factor {BinOp($1, $0, $2)}
              |factor: @Factor = number
              |    | variable
              |    | '(' expression ')' {@Paren(expr=$1)}
              |number: @Number = '0' {@Integer(value=$0)}
              |    | '1-9' '0-9'* {Integer([$0, $1])}
              |variable = <'A-Za-z'+> {@Variable(name=$0)}
              |list = '[' expression (',' expression)* ']' {@List(elems=[$1] + $2$1)}
              |something: [[@Something]]? = 'a'
            """.stripMargin

        val ast = grammarSpecToAST(expressionGrammar)

        println(ast)

        val analysis = Analyzer.analyze(ast.get)

        val dotGraph = analysis.typeDependenceGraph.toDotGraphModel

        println(dotGraph.printDotGraph())

        // 문법이 주어지면
        // 1a. processor가 없는 문법 텍스트
        // 1b. NGrammar 정의하는 스칼라 코드(new NGrammar(...))
        // 1c. (나중엔) 제너레이트된 파서
        // 2. 정의된 타입들을 정의하는 자바 코드
        // 3. ParseForest를 주면 프로세서로 처리해서 가공한 값으로 만들어주는 자바 코드
    }
}
