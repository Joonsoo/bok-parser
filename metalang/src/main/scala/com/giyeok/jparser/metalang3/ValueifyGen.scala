package com.giyeok.jparser.metalang3

import com.giyeok.jparser.ParseResultTree.Node
import com.giyeok.jparser.examples.metalang3.MetaLang3Grammar
import com.giyeok.jparser.metalang2.generated.MetaGrammar3Ast
import com.giyeok.jparser.metalang2.generated.MetaGrammar3Ast.{Elem, InPlaceSequence, Rule, ngrammar}
import com.giyeok.jparser.metalang3.TypeFunc._
import com.giyeok.jparser.metalang3.codegen.ScalaGen
import com.giyeok.jparser.nparser.ParseTreeConstructor
import com.giyeok.jparser.{NGrammar, ParseForestFunc, ParseResultTree, ParsingErrors}

object ValueifyGen {

    case class IllegalGrammar(msg: String) extends Exception

    private def check(cond: Boolean, msg: String): Unit = {
        if (!cond) throw IllegalGrammar(msg)
    }

    private def condSymPathOf(condSymPath: List[Node]): String = ???

    private def unifyTypes(types: Iterable[TypeFunc]): TypeFunc = if (types.size == 1) types.head else UnionOf(types.toList)

    private def valueifyInPlaceSequence(choices: List[MetaGrammar3Ast.InPlaceSequence], condSymPath: String, input: ValueifyExpr): (ValueifyExpr, TypeFunc) = {
        def valueifyChoice(choice: InPlaceSequence) = {
            assert(choice.seq.nonEmpty)
            val bodyInput = choice.seq.last match {
                case _: MetaGrammar3Ast.Symbol => SeqElemAt(Unbind(choice, input), choice.seq.size - 1)
                case _: MetaGrammar3Ast.Processor => Unbind(choice, input)
            }
            valueify(choice.seq, choice.seq.last, condSymPath, bodyInput)
        }

        if (choices.size == 1) {
            valueifyChoice(choices.head)
        } else {
            check(condSymPath.isEmpty, "")
            val vChoices = choices.map { choice =>
                choice -> valueifyChoice(choice)
            }
            val choicesMap: Map[DerivationChoice, ValueifyExpr] = vChoices.map(choice => InPlaceSequenceChoice(choice._1) -> choice._2._1).toMap
            val returnType = unifyTypes(vChoices.map(_._2._2))
            (UnrollChoices(choicesMap), returnType)
        }
    }

    // _1는 elem 속의 refCtx
    // _2는 elem이 Longest 등인 경우 그걸 어떻게 발라먹을지
    private def getBindingContext(elem: MetaGrammar3Ast.Elem, input: ValueifyExpr): (List[Elem], ValueifyExpr) = elem match {
        case symbol@MetaGrammar3Ast.Longest(astNode, choices) if choices.choices.size == 1 =>
            getBindingContext(choices.choices.head, Unbind(symbol, input))
        case MetaGrammar3Ast.InPlaceChoices(astNode, choices) if choices.size == 1 =>
            (choices.head.seq, input)
        case _ => throw IllegalGrammar("Bind expression only can refer to Longest or InPlaceChoices with only choice")
    }

    def valueify(refCtx: List[Elem], elem: Elem, condSymPath: String, input: ValueifyExpr): (ValueifyExpr, TypeFunc) = elem match {
        case symbol: MetaGrammar3Ast.Symbol =>
            symbol match {
                case MetaGrammar3Ast.JoinSymbol(astNode, body, join) =>
                    if (condSymPath.isEmpty) {
                        valueify(refCtx, body, condSymPath, JoinBodyOf(Unbind(symbol, input)))
                    } else {
                        // TODO condSymPath.head 방향 봐서 JoinBodyOf/JoinCondOf, valueify 재귀호출 할 때는 condSymPath.tail
                        ???
                    }
                case MetaGrammar3Ast.ExceptSymbol(astNode, body, except) =>
                    if (condSymPath.isEmpty) {
                        valueify(refCtx, body, condSymPath, ExceptBodyOf(Unbind(symbol, input)))
                    } else {
                        // TODO condSymPath
                        ???
                    }
                case MetaGrammar3Ast.FollowedBy(astNode, followedBy) =>
                    // TODO condSymPath
                    (InputNode, NodeType)
                case MetaGrammar3Ast.NotFollowedBy(astNode, notFollowedBy) =>
                    // TODO condSymPath
                    (InputNode, NodeType)
                case MetaGrammar3Ast.Optional(astNode, body) =>
                    check(condSymPath.isEmpty, "")
                    val vBody = valueify(refCtx, body, condSymPath, Unbind(symbol, input))
                    (UnrollChoices(Map(EmptySeqChoice -> NullLiteral, SymbolChoice(body) -> vBody._1)), OptionalOf(vBody._2))
                case symbol@MetaGrammar3Ast.RepeatFromZero(astNode, body) =>
                    check(condSymPath.isEmpty, "")
                    val vBody = valueify(refCtx, body, condSymPath, InputNode)
                    (UnrollRepeat(0, input, vBody._1), ArrayOf(vBody._2))
                case symbol@MetaGrammar3Ast.RepeatFromOne(astNode, body) =>
                    check(condSymPath.isEmpty, "")
                    val vBody = valueify(refCtx, body, condSymPath, input)
                    (UnrollRepeat(1, input, vBody._1), ArrayOf(vBody._2))
                case MetaGrammar3Ast.InPlaceChoices(astNode, choices) =>
                    check(condSymPath.isEmpty, "")
                    valueifyInPlaceSequence(choices, condSymPath, input)
                case symbol@MetaGrammar3Ast.Longest(astNode, choices) =>
                    check(condSymPath.isEmpty, "")
                    val vChoices = valueifyInPlaceSequence(choices.choices, condSymPath, input)
                    (Unbind(symbol, vChoices._1), vChoices._2)
                case _: MetaGrammar3Ast.Terminal =>
                    check(condSymPath.isEmpty, "")
                    (InputNode, NodeType)
                case MetaGrammar3Ast.TerminalChoice(astNode, choices) =>
                    check(condSymPath.isEmpty, "")
                    (InputNode, NodeType)
                case MetaGrammar3Ast.StringSymbol(astNode, value) =>
                    (StringLiteral(StringLiteral.escape(value)), StringType)
                case nonterm@MetaGrammar3Ast.Nonterminal(astNode, name) =>
                    (MatchNonterminal(nonterm, input), TypeOfSymbol(symbol))
                case MetaGrammar3Ast.EmptySeq(astNode) =>
                    check(condSymPath.isEmpty, "")
                    (InputNode, NodeType)
            }
        case processor: MetaGrammar3Ast.Processor =>
            // TODO share ValueifyExpr
            check(condSymPath.isEmpty, "")
            processor match {
                //                case MetaGrammar3Ast.TernaryOp(astNode, cond, ifTrue, ifFalse) =>
                //                    val vCond = valueify(refCtx, cond, "", InputNode)
                //                    val vIfTrue = valueify(refCtx, ??? /*ifTrue*/ , "", InputNode)
                //                    val vIfFalse = valueify(refCtx, ??? /*ifFalse*/ , "", InputNode)
                //                    (TernaryExpr(vCond._1, vIfTrue._1, vIfFalse._1, vCond._2), unifyTypes(List(vIfTrue._2, vIfFalse._2)))
                case MetaGrammar3Ast.ElvisOp(astNode, value, ifNull) =>
                    val vValue = valueify(refCtx, value, "", input)
                    val vIfNull = valueify(refCtx, ifNull, "", input)
                    (ElvisOp(vValue._1, vIfNull._1), ElvisType(vValue._2, vIfNull._2))
                case MetaGrammar3Ast.BinOp(astNode, op, lhs, rhs) =>
                    val vLhs = valueify(refCtx, lhs, "", input)
                    val vRhs = valueify(refCtx, rhs, "", input)
                    op.sourceText match {
                        case "&&" => (BinOp(Op.BOOL_AND, vLhs._1, vRhs._1, vLhs._2, vRhs._2), BoolType)
                        case "||" => (BinOp(Op.BOOL_OR, vLhs._1, vRhs._1, vLhs._2, vRhs._2), BoolType)
                        case "==" => (BinOp(Op.EQ, vLhs._1, vRhs._1, vLhs._2, vRhs._2), BoolType)
                        case "!=" => (BinOp(Op.NE, vLhs._1, vRhs._1, vLhs._2, vRhs._2), BoolType)
                        case "+" => (BinOp(Op.ADD, vLhs._1, vRhs._1, vLhs._2, vRhs._2), AddOpType(vLhs._2, vRhs._2))
                    }
                case MetaGrammar3Ast.PrefixOp(astNode, expr, op) =>
                    val vExpr = valueify(refCtx, expr, "", input)
                    op.sourceText match {
                        case "!" => (PrefixOp(PreOp.NOT, vExpr._1, vExpr._2), BoolType)
                    }
                case ref: MetaGrammar3Ast.Ref => ref match {
                    case MetaGrammar3Ast.ValRef(astNode, idx, condSymPath) =>
                        val idxValue = idx.sourceText.toInt
                        if (idxValue >= refCtx.size) throw new IllegalGrammar("")
                        val symbolIdx = idxValue // TODO idxValue - refCtx(0~idxValue 전)까지 symbol의 갯수
                        valueify(refCtx, refCtx(idxValue), condSymPath.map(condSymPathOf).getOrElse(""),
                            SeqElemAt(input, symbolIdx))
                    case MetaGrammar3Ast.RawRef(astNode, idx, condSymPath) => ???
                }
                case MetaGrammar3Ast.ExprParen(astNode, body) =>
                    valueify(refCtx, body, "", input)
                case MetaGrammar3Ast.BindExpr(astNode, ctx, binder) =>
                    check(ctx.condSymPath.getOrElse(List()).isEmpty, "Binding context cannot have condSymPath")
                    val idx = ctx.idx.sourceText.toInt
                    check(idx < refCtx.size, "")
                    val bindingCtx = getBindingContext(refCtx(idx), input)
                    assert(bindingCtx._1.nonEmpty)
                    valueify(bindingCtx._1, bindingCtx._1.last, "", bindingCtx._2)
                case MetaGrammar3Ast.NamedConstructExpr(astNode, typeName, params) =>
                    val vParams = params.map(param => (param, valueify(refCtx, param.expr, "", input)))
                    (NamedConstructCall(typeName, vParams), ClassType(typeName))
                case MetaGrammar3Ast.FuncCallOrConstructExpr(astNode, funcName, params) =>
                    val vParams = params.getOrElse(List()).map(param => valueify(refCtx, param, "", input))
                    // TODO FuncCall or UnnamedConstructCall depending on its name
                    (FuncCall(funcName, vParams), FuncCallResultType(funcName, vParams))
                case MetaGrammar3Ast.ArrayExpr(astNode, elems) =>
                    val vElems = elems.getOrElse(List()).map(elem => valueify(refCtx, elem, "", input))
                    val arrayElemType = unifyTypes(vElems.map(_._2).toSet)
                    (ArrayExpr(vElems.map(_._1)), ArrayOf(arrayElemType))
                case literal: MetaGrammar3Ast.Literal => literal match {
                    case MetaGrammar3Ast.NullLiteral(astNode) => (NullLiteral, NullType)
                    case MetaGrammar3Ast.BoolLiteral(astNode, value) => (BoolLiteral(value.sourceText.toBoolean), BoolType)
                    // TODO Fix StringLiteral and CharLiteral value
                    case MetaGrammar3Ast.CharLiteral(astNode, value) => (CharLiteral(value.astNode.sourceText.charAt(0)), CharType)
                    case MetaGrammar3Ast.StringLiteral(astNode, value) => (StringLiteral(value.map(_.astNode.sourceText).mkString), StringType)
                }
                case MetaGrammar3Ast.CanonicalEnumValue(astNode, enumName, valueName) =>
                    (CanonicalEnumValue(enumName, valueName), EnumType(enumName))
                case MetaGrammar3Ast.ShortenedEnumValue(astNode, valueName) =>
                    (ShortenedEnumValue(valueName), UnspecifiedEnum(1)) // TODO uniqueId
            }
    }

    private def valueifyRule(rule: Rule) = {
        val mappers = rule.rhs.map { rhs =>
            RightHandSideChoice(rhs) -> valueify(rhs.elems, rhs.elems.last, "", InputNode)
        }.toMap
        val mappings = mappers.map(mapper => mapper._1 -> mapper._2._1)
        val returnType = unifyTypes(mappers.map(_._2._2))
        (UnrollChoices(mappings.toMap), returnType)
    }

    def parse(text: String): ParseResultTree.Node = {
        MetaGrammar3Ast.parse(text) match {
            case Left(ctx) =>
                val tree = new ParseTreeConstructor(ParseForestFunc)(ngrammar)(ctx.inputs, ctx.history, ctx.conditionFinal).reconstruct()
                tree match {
                    case Some(forest) if forest.trees.size == 1 =>
                        forest.trees.head
                    case Some(forest) =>
                        throw new Exception(ParsingErrors.AmbiguousParse("Ambiguous Parse: " + forest.trees.size).toString)
                    case None =>
                        val expectedTerms = ctx.nextGraph.nodes.flatMap { node =>
                            node.kernel.symbol match {
                                case NGrammar.NTerminal(_, term) => Some(term)
                                case _ => None
                            }
                        }
                        throw new Exception(ParsingErrors.UnexpectedEOF(expectedTerms, text.length).toString)
                }
            case Right(error) => throw new Exception(error.toString)
        }
    }

    def main(args: Array[String]): Unit = {
        MetaLang3Grammar.inMetaLang3
        val example =
            """Rule = LHS WS '=' WS (RHS (WS '|' WS RHS)* {[$0] + $1}) {Rule(lhs=$0, rhs=$4)}
              |""".stripMargin
        val defs = MetaGrammar3Ast.parseAst(example).left.get.defs
        val rule = defs.head.asInstanceOf[Rule]
        val v = valueifyRule(rule)

        val analysis = new AnalysisResult()
        val scalaGen = new ScalaGen(analysis)
        scalaGen.matchFuncFor(rule.lhs.name, v._1, v._2).codes.foreach(println)
    }
}
