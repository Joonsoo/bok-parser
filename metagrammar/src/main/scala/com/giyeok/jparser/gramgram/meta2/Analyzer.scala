package com.giyeok.jparser.gramgram.meta2

import com.giyeok.jparser.GrammarHelper.{i, _}
import com.giyeok.jparser.ParseResultTree.{BindNode, SequenceNode, TerminalNode}
import com.giyeok.jparser.Symbols.Nonterminal
import com.giyeok.jparser.nparser.NGrammar
import com.giyeok.jparser.utils.{AbstractEdge, AbstractGraph, DotGraphModel, GraphUtil, Memoize}
import com.giyeok.jparser.{Grammar, Inputs, Symbols}

import scala.collection.immutable.{ListMap, ListSet}

object Analyzer {

    sealed trait UserDefinedType

    case class UserClassType(name: String, params: List[AST.NamedParam]) extends UserDefinedType

    case class UserAbstractType(name: String) extends UserDefinedType

    class Analysis private[Analyzer](val grammarAst: AST.Grammar,
                                     val grammar: Grammar,
                                     val ngrammar: NGrammar,
                                     val typeDependenceGraph: Analyzer#TypeDependenceGraph,
                                     val subclasses: Map[UserAbstractType, Set[UserClassType]],
                                     val inferredTypes: Map[String, UserDefinedType])

    class Analyzer(val grammarAst: AST.Grammar) {
        val ruleDefs: List[AST.Rule] = grammarAst.defs collect { case r: AST.Rule => r }
        val typeDefs: List[AST.TypeDef] = grammarAst.defs collect { case r: AST.TypeDef => r }

        private var userDefinedTypes = List[UserDefinedType]()
        private var superTypes = Map[UserAbstractType, Set[String]]()

        def collectTypeDefs(): Unit = {
            // TODO typeDefs
            def addTypeDefsLhs(typ: AST.ValueTypeDesc): Unit = typ match {
                case AST.OnTheFlyTypeDef(_, name, supers) =>
                    val newType = UserAbstractType(name.name.toString)
                    val newSupers = superTypes.getOrElse(newType, List()) ++ (supers map {
                        _.name.toString
                    })
                    superTypes += newType -> newSupers.toSet
                    userDefinedTypes +:= newType
                case AST.ArrayTypeDesc(_, elemType) =>
                    addTypeDefsLhs(elemType.typ)
                case _ => // do nothing
            }

            def addTypeDefsOnTheFlyTypeDefConstructExpr(expr: AST.OnTheFlyTypeDefConstructExpr): Unit = expr match {
                case AST.OnTheFlyTypeDefConstructExpr(_, tdef, params) =>
                    val newType = UserClassType(tdef.name.name.toString, params)
                    userDefinedTypes +:= newType
                    params foreach { p => addTypeDefsExpr(p.expr) }
            }

            def addTypeDefsBoundedPExpr(expr: AST.BoundedPExpr): Unit = expr match {
                case AST.BoundPExpr(_, _, e) => addTypeDefsBoundedPExpr(e)
                case e: AST.PExpr => addTypeDefsExpr(e)
                case e: AST.OnTheFlyTypeDefConstructExpr =>
                    addTypeDefsOnTheFlyTypeDefConstructExpr(e)
                case _: AST.Ref => // do nothing
            }

            def addTypeDefsExpr(expr: AST.PExpr): Unit = expr match {
                case AST.PTermParen(_, e) => addTypeDefsExpr(e)
                case AST.BoundPExpr(_, _, e) => addTypeDefsBoundedPExpr(e)
                case AST.ConstructExpr(_, _, params) => params foreach addTypeDefsExpr
                case AST.PTermSeq(_, elems) => elems foreach addTypeDefsExpr
                case AST.ConstructExpr(_, _, params) => params foreach addTypeDefsExpr
                case e: AST.OnTheFlyTypeDefConstructExpr =>
                    addTypeDefsOnTheFlyTypeDefConstructExpr(e)
                case AST.BinOpExpr(_, _, lhs, rhs) =>
                    addTypeDefsExpr(lhs)
                    addTypeDefsExpr(rhs)
                case _: AST.Ref => // do nothing
            }

            ruleDefs foreach { rule =>
                rule.lhs.typeDesc foreach { lhsType =>
                    addTypeDefsLhs(lhsType.typ)
                }
                rule.rhs foreach { rhs =>
                    rhs.elems foreach {
                        case processor: AST.PExpr =>
                            addTypeDefsExpr(processor)
                        case _ => // do nothing
                    }
                }
            }
        }

        private val astToSymbols = scala.collection.mutable.Map[AST.Symbol, Symbols.Symbol]()

        private def addSymbol(ast: AST.Symbol, symbol: Symbols.Symbol): Symbols.Symbol = {
            astToSymbols get ast match {
                case Some(existing) =>
                    assert(symbol == existing)
                    existing
                case None =>
                    astToSymbols(ast) = symbol
                    symbol
            }
        }

        private def unicodeCharToChar(charNode: AST.Node): Char = charNode.node match {
            case BindNode(_, seq: SequenceNode) =>
                assert(seq.children.size == 6)
                Integer.parseInt(s"${seq.children(2).toString}${seq.children(3).toString}${seq.children(4).toString}${seq.children(5).toString}", 16).toChar
        }

        private def charNodeToChar(charNode: AST.Node): Char = charNode.node match {
            case BindNode(_, BindNode(_, TerminalNode(c))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, BindNode(_, TerminalNode(c))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, SequenceNode(_, List(BindNode(_, TerminalNode(escapeCode)), _))) =>
                escapeCode.asInstanceOf[Inputs.Character].char match {
                    case '\'' => '\''
                    case '\\' => '\\'
                    case 'b' => '\b'
                    case 'n' => '\n'
                    case 'r' => '\r'
                    case 't' => '\t'
                }
            case _ => unicodeCharToChar(charNode)
        }

        private def charChoiceNodeToChar(charNode: AST.Node): Char = charNode.node match {
            case BindNode(_, BindNode(_, TerminalNode(c))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, BindNode(_, BindNode(_, TerminalNode(c)))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, SequenceNode(_, List(BindNode(_, TerminalNode(escapeCode)), _))) =>
                escapeCode.asInstanceOf[Inputs.Character].char match {
                    case '\'' => '\''
                    case '-' => '-'
                    case '\\' => '\\'
                    case 'b' => '\b'
                    case 'n' => '\n'
                    case 'r' => '\r'
                    case 't' => '\t'
                }
            case _ => unicodeCharToChar(charNode)
        }

        private def stringCharToChar(stringCharNode: AST.Node): Char = stringCharNode.node match {
            case BindNode(_, BindNode(_, TerminalNode(c))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, BindNode(_, TerminalNode(c))) =>
                c.asInstanceOf[Inputs.Character].char
            case BindNode(_, SequenceNode(_, List(BindNode(_, TerminalNode(escapeCode)), _))) =>
                escapeCode.asInstanceOf[Inputs.Character].char match {
                    case '"' => '"'
                    case '\\' => '\\'
                    case 'b' => '\b'
                    case 'n' => '\n'
                    case 'r' => '\r'
                    case 't' => '\t'
                }
            case _ => unicodeCharToChar(stringCharNode)
        }

        private def astToSymbol(ast: AST.Symbol): Symbols.Symbol = ast match {
            case AST.JoinSymbol(_, symbol1, symbol2) =>
                val ns1 = astToSymbol(symbol1).asInstanceOf[Symbols.AtomicSymbol]
                val ns2 = astToSymbol(symbol2).asInstanceOf[Symbols.AtomicSymbol]
                addSymbol(ast, Symbols.Join(ns1, ns2))
            case AST.ExceptSymbol(_, symbol1, symbol2) =>
                val ns1 = astToSymbol(symbol1).asInstanceOf[Symbols.AtomicSymbol]
                val ns2 = astToSymbol(symbol2).asInstanceOf[Symbols.AtomicSymbol]
                addSymbol(ast, Symbols.Except(ns1, ns2))
            case AST.FollowedBy(_, symbol) =>
                val ns = astToSymbol(symbol).asInstanceOf[Symbols.AtomicSymbol]
                addSymbol(ast, Symbols.LookaheadIs(ns))
            case AST.NotFollowedBy(_, symbol) =>
                val ns = astToSymbol(symbol).asInstanceOf[Symbols.AtomicSymbol]
                addSymbol(ast, Symbols.LookaheadExcept(ns))
            case AST.Repeat(_, symbol, repeatSpec) =>
                val ns = astToSymbol(symbol).asInstanceOf[Symbols.AtomicSymbol]

                repeatSpec.toString match {
                    case "?" => addSymbol(ast, ns.opt)
                    case "*" => addSymbol(ast, ns.star)
                    case "+" => addSymbol(ast, ns.plus)
                }
            case AST.Longest(_, choices) =>
                val ns = choices.choices map {
                    astToSymbol(_).asInstanceOf[Symbols.AtomicSymbol]
                }
                addSymbol(ast, Symbols.Longest(Symbols.OneOf(ListSet(ns: _*))))
            case AST.Nonterminal(_, name) =>
                addSymbol(ast, Symbols.Nonterminal(name.toString))
            case AST.InPlaceChoices(_, choices) =>
                val ns = choices map {
                    astToSymbol(_).asInstanceOf[Symbols.AtomicSymbol]
                }
                addSymbol(ast, Symbols.OneOf(ListSet(ns: _*)))
            case AST.Paren(_, choices) =>
                val ns = choices.choices map {
                    astToSymbol(_).asInstanceOf[Symbols.AtomicSymbol]
                }
                addSymbol(ast, Symbols.OneOf(ListSet(ns: _*)))
            case AST.InPlaceSequence(_, seq) =>
                val ns = seq map {
                    astToSymbol(_).asInstanceOf[Symbols.AtomicSymbol]
                }
                addSymbol(ast, Symbols.Proxy(Symbols.Sequence(ns)))
            case AST.StringLiteral(_, value) =>
                // TODO
                proxyIfNeeded(addSymbol(ast, i(value.toString)))
            case AST.EmptySeq(_) =>
                // TODO Symbols.Proxy?
                addSymbol(ast, Symbols.Proxy(Symbols.Sequence(Seq())))
            case AST.TerminalChoice(_, choices) =>
                val charSet = choices flatMap {
                    case AST.TerminalChoiceChar(_, char) => Seq(charChoiceNodeToChar(char))
                    case AST.TerminalChoiceRange(_, start, end) =>
                        charChoiceNodeToChar(start.char) to charChoiceNodeToChar(end.char)
                }
                addSymbol(ast, Symbols.Chars(charSet.toSet))
            case AST.TerminalChar(_, value) =>
                addSymbol(ast, Symbols.ExactChar(charNodeToChar(value)))
            case AST.AnyTerminal(_) =>
                addSymbol(ast, Symbols.AnyChar)
        }

        object TypeDependenceGraph {

            sealed trait Node {
                private def boundExprString(bexpr: AST.BoundedPExpr): String = bexpr match {
                    case AST.BoundPExpr(_, ctx, expr) => s"${pexprString(ctx)}${boundExprString(expr)}"
                    case expr: AST.PExpr => pexprString(expr)
                }

                private def pexprString(pexpr: AST.PExpr): String = pexpr match {
                    case AST.BinOpExpr(_, op, lhs, rhs) => s"${pexprString(lhs)} $op ${pexprString(rhs)}"
                    case term: AST.PTerm => term match {
                        case AST.Ref(_, idx) => s"$$$idx"
                        case AST.BoundPExpr(_, ctx, expr) =>
                            s"${pexprString(ctx)}{${boundExprString(expr)}}"
                        case expr: AST.AbstractConstructExpr => expr match {
                            case AST.ConstructExpr(_, typ, params) =>
                                s"${typ.name.toString}(${params map pexprString mkString ","})"
                            case AST.OnTheFlyTypeDefConstructExpr(_, typeDef, params) =>
                                s"${typeDef.name.name.toString}(${params map { p => pexprString(p.expr) } mkString ","})"
                        }
                        case AST.PTermParen(_, expr) =>
                            s"(${pexprString(expr)})"
                        case AST.PTermSeq(_, elems) =>
                            s"[${elems map pexprString mkString ","}]"
                    }
                }

                def nodeLabel: String = this match {
                    case TypeDependenceGraph.SymbolNode(symbol) => s"Symbol(${symbol.toShortString})"
                    case TypeDependenceGraph.ExprNode(expr) => s"Expr(${pexprString(expr)})"
                    case TypeDependenceGraph.ParamNode(className, paramIdx, name) => s"Param($className, $paramIdx, $name)"
                    case typeNode: TypeDependenceGraph.TypeNode =>
                        def typeNodeToString(typ: TypeDependenceGraph.TypeNode): String =
                            typ match {
                                case TypeDependenceGraph.ClassTypeNode(className) => s"Class $className"
                                case TypeDependenceGraph.TypeArray(elemType) => s"[${typeNodeToString(elemType)}]"
                                case TypeDependenceGraph.TypeOptional(elemType) => s"${typeNodeToString(elemType)}?"
                                case TypeDependenceGraph.TypeGenArray(expr) => s"[typeof ${expr.nodeLabel}]"
                                case TypeDependenceGraph.TypeGenOptional(expr) => s"(typeof ${expr.nodeLabel})?"
                                case TypeDependenceGraph.TypeGenArrayConcatOp(op, lhs, rhs) => s"[concat typeof ${lhs.nodeLabel} $op ${rhs.nodeLabel}]"
                                case TypeDependenceGraph.TypeGenArrayElemsUnion(elems) => s"[union typeof ${elems map (_.nodeLabel) mkString ","}]"
                            }

                        typeNodeToString(typeNode)
                }
            }

            sealed trait ElemNode extends Node with Equals

            case class SymbolNode(symbol: Symbols.Symbol) extends ElemNode

            case class ExprNode(expr: AST.PExpr) extends ElemNode

            case class ParamNode(className: String, paramIdx: Int, name: String) extends ElemNode

            sealed trait TypeNode extends Node with Equals

            case class ClassTypeNode(className: String) extends TypeNode

            case class TypeArray(elemType: TypeNode) extends TypeNode

            case class TypeOptional(elemType: TypeNode) extends TypeNode

            case class TypeGenArray(typeof: ExprNode) extends TypeNode

            case class TypeGenOptional(typeof: ExprNode) extends TypeNode

            case class TypeGenArrayConcatOp(op: String, lhs: ExprNode, rhs: ExprNode) extends TypeNode

            case class TypeGenArrayElemsUnion(elems: List[ExprNode]) extends TypeNode

            object EdgeTypes extends Enumeration {
                val Is, Accepts, Extends, Has = Value
            }

            case class Edge(start: Node, end: Node, edgeType: EdgeTypes.Value) extends AbstractEdge[Node]

            class Builder() {
                private var graph = new TypeDependenceGraph(Set(), Set(), Map(), Map())

                private def addNode[T <: Node](node: T): T = {
                    graph = graph.addNode(node)
                    node
                }

                private def addEdge(edge: Edge): Edge = {
                    graph = graph.addEdge(edge)
                    edge
                }

                private def typeDescToTypeNode(typeDesc: AST.TypeDesc): TypeNode = {
                    val valueTypeNode = typeDesc.typ match {
                        case AST.ArrayTypeDesc(_, elemType) =>
                            val elemTypeNode = typeDescToTypeNode(elemType)
                            addNode(TypeArray(elemTypeNode))
                        case AST.TypeName(_, typeName) =>
                            ClassTypeNode(typeName.toString)
                        case AST.OnTheFlyTypeDef(_, name, _) =>
                            ClassTypeNode(name.name.toString)
                    }
                    if (typeDesc.optional) addNode(TypeOptional(valueTypeNode)) else valueTypeNode
                }

                private var classParamNodes = Map[String, List[ParamNode]]()

                def analyze(): TypeDependenceGraph = {
                    userDefinedTypes foreach {
                        case UserClassType(className, params) =>
                            val classNode = addNode(ClassTypeNode(className))
                            val paramNodes = params.zipWithIndex map { case (paramAst, paramIdx) =>
                                val paramNode = addNode(ParamNode(className, paramIdx, paramAst.name.name.toString))
                                // ClassNode --has--> ParamNode
                                addEdge(Edge(classNode, paramNode, EdgeTypes.Has))
                                paramAst.typeDesc foreach { typeDesc =>
                                    val paramType = addNode(typeDescToTypeNode(typeDesc))
                                    // ParamNode --is--> TypeNode
                                    addEdge(Edge(paramNode, paramType, EdgeTypes.Is))
                                }
                                paramNode
                            }
                            classParamNodes += className -> paramNodes
                        case typ@UserAbstractType(name) =>
                            val abstractType = addNode(ClassTypeNode(name))
                            superTypes(typ) foreach { superTyp =>
                                val subType = addNode(ClassTypeNode(superTyp))
                                // ClassTypeNode --extends--> ClassTypeNode
                                addEdge(Edge(subType, abstractType, EdgeTypes.Extends))
                            }
                    }
                    ruleDefs foreach { ruleDef =>
                        val lhsName = ruleDef.lhs.name.name.toString
                        val lhsNontermNode = addNode(SymbolNode(Symbols.Nonterminal(lhsName)))

                        ruleDef.lhs.typeDesc foreach { lhsType =>
                            val lhsTypeNode = typeDescToTypeNode(lhsType)
                            // SymbolNode --is--> TypeNode
                            addEdge(Edge(lhsNontermNode, lhsTypeNode, EdgeTypes.Is))
                        }

                        // TODO ruleDef.rhs 를 하나씩 순회하면서 Param accept expr
                        ruleDef.rhs foreach { rhs =>
                            def visitBoundExpr(node: ExprNode, ctx: List[AST.Elem], expr: AST.BoundPExpr): ExprNode = expr match {
                                case AST.BoundPExpr(_, ctxRef, boundedExpr) =>
                                    ctx(ctxRef.idx.toString.toInt) match {
                                        case processor: AST.Processor =>
                                            throw new Exception("Invalid bound context")
                                        case symbol: AST.Symbol =>
                                            symbol match {
                                                case AST.Repeat(_, repeatingSymbol, repeatSpec) =>
                                                    // val repeatingSymbolNode = addNode(SymbolNode(astToSymbol(repeatingSymbol)))
                                                    // TODO ctx 처리
                                                    val bound = repeatingSymbol match {
                                                        case AST.InPlaceChoices(_, List(choice)) => choice.seq
                                                        case AST.Longest(_, AST.InPlaceChoices(_, List(choice))) => choice.seq
                                                        case x =>
                                                            println(x)
                                                            ???
                                                    }
                                                    val elemNode = boundedExpr match {
                                                        case expr: AST.PExpr => visitExpr(bound, expr)
                                                        case expr: AST.OnTheFlyTypeDefConstructExpr => visitExpr(bound, expr)
                                                        case expr: AST.Ref => visitExpr(bound, expr)
                                                        case expr: AST.BoundPExpr =>
                                                            // TODO 첫번째 인자 node 수정
                                                            visitBoundExpr(node, bound, expr)
                                                    }
                                                    repeatSpec.toString match {
                                                        case "?" =>
                                                            val typeNode = addNode(TypeGenOptional(elemNode))
                                                            // ExprNode --is--> TypeNode
                                                            addEdge(Edge(node, typeNode, EdgeTypes.Is))
                                                            // TypeNode --accepts--> ElemNode (informative)
                                                            addEdge(Edge(typeNode, elemNode, EdgeTypes.Accepts))
                                                        case "*" | "+" =>
                                                            val typeNode = addNode(TypeGenArray(elemNode))
                                                            // ExprNode --is--> TypeNode
                                                            addEdge(Edge(node, typeNode, EdgeTypes.Is))
                                                            // TypeNode --accepts--> ElemNode (informative)
                                                            addEdge(Edge(typeNode, elemNode, EdgeTypes.Accepts))
                                                    }
                                                    node
                                                case AST.Paren(_, AST.InPlaceChoices(_, choices)) =>
                                                    // TODO
                                                    ???
                                                case AST.Longest(_, AST.InPlaceChoices(_, choices)) =>
                                                    // TODO
                                                    ???
                                                case AST.InPlaceSequence(_, seq) =>
                                                    // TODO
                                                    ???
                                                case _ =>
                                                    throw new Exception("Invalid bound context")
                                            }
                                    }
                            }

                            def visitExpr(ctx: List[AST.Elem], expr: AST.PExpr): ExprNode = {
                                val node = addNode(ExprNode(expr))
                                expr match {
                                    case AST.BinOpExpr(_, operator, operand1, operand2) =>
                                        operator.toString match {
                                            case "+" =>
                                                val op1 = visitExpr(ctx, operand1)
                                                val op2 = visitExpr(ctx, operand2)
                                                val typeNode = addNode(TypeGenArrayConcatOp(operator.toString, op1, op2))
                                                // ExprNode --is--> TypeNode
                                                addEdge(Edge(node, typeNode, EdgeTypes.Is))
                                                // TypeNode --accepts--> ExprNode (informative)
                                                addEdge(Edge(typeNode, op1, EdgeTypes.Accepts))
                                                addEdge(Edge(typeNode, op2, EdgeTypes.Accepts))
                                        }
                                    case AST.Ref(_, idx) =>
                                        // ExprNode --accepts--> ElemNode
                                        addEdge(Edge(node, visitElem(ctx, ctx(idx.toString.toInt)), EdgeTypes.Accepts))
                                    case bound: AST.BoundPExpr => visitBoundExpr(node, ctx, bound)
                                    case AST.ConstructExpr(_, typ, params) =>
                                        val className = typ.name.toString
                                        // ExprNode --is--> ClassTypeNode
                                        addEdge(Edge(node, ClassTypeNode(className), EdgeTypes.Is))
                                        // ParamNode --accepts--> ExprNode
                                        params.zipWithIndex foreach { case (paramExpr, paramIdx) =>
                                            addEdge(Edge(classParamNodes(className)(paramIdx), visitExpr(ctx, paramExpr), EdgeTypes.Accepts))
                                        }
                                    case AST.OnTheFlyTypeDefConstructExpr(_, typeDef, params) =>
                                        val className = typeDef.name.name.toString
                                        // ExprNode --is--> ClassTypeNode
                                        addEdge(Edge(node, ClassTypeNode(className), EdgeTypes.Is))
                                        // ParamNode --accepts--> ExprNode
                                        params.zipWithIndex foreach { case (paramExpr, paramIdx) =>
                                            val paramNode = classParamNodes(className)(paramIdx)
                                            assert(paramNode.name == paramExpr.name.name.toString)
                                            addEdge(Edge(paramNode, visitExpr(ctx, paramExpr.expr), EdgeTypes.Accepts))
                                        }
                                    case AST.PTermParen(_, parenExpr) =>
                                        // ExprNode --accepts--> ExprNode
                                        addEdge(Edge(node, visitExpr(ctx, parenExpr), EdgeTypes.Accepts))
                                    case AST.PTermSeq(_, elems) =>
                                        val elemNodes = elems map {
                                            visitExpr(ctx, _)
                                        }
                                        val typeNode = addNode(TypeGenArrayElemsUnion(elemNodes))
                                        // ExprNode --is--> TypeNode
                                        addEdge(Edge(node, typeNode, EdgeTypes.Is))
                                        // TypeNode --accepts--> ExprNode (informative)
                                        elemNodes foreach { seqElem =>
                                            addEdge(Edge(typeNode, seqElem, EdgeTypes.Accepts))
                                        }
                                }
                                node
                            }

                            def visitElem(ctx: List[AST.Elem], elem: AST.Elem): ElemNode = elem match {
                                case processor: AST.Processor =>
                                    processor match {
                                        case expr: AST.PExpr =>
                                            visitExpr(ctx, expr)
                                            addNode(ExprNode(expr))
                                        case AST.Ref(_, idx) =>
                                            visitElem(ctx, ctx(idx.toString.toInt))
                                    }
                                case symbol: AST.Symbol => addNode(SymbolNode(astToSymbol(symbol)))
                            }

                            val elemNodes: List[Node] = rhs.elems map { e => visitElem(rhs.elems, e) }

                            // SymbolNode --accepts--> ExprNode|SymbolNode
                            val lastElem = elemNodes.last
                            addEdge(Edge(lhsNontermNode, lastElem, EdgeTypes.Accepts))
                        }
                    }
                    graph
                }
            }

        }

        sealed trait TypeSpec

        sealed trait OptionableTypeSpec extends TypeSpec

        sealed trait ActualTypeSpec extends TypeSpec

        case object ParseNodeType extends OptionableTypeSpec with ActualTypeSpec

        case class ClassType(className: String) extends OptionableTypeSpec with ActualTypeSpec

        case class AbstractType(typeName: String) extends OptionableTypeSpec with ActualTypeSpec

        case class UnionType(types: Set[NodeType]) extends TypeSpec

        case class ArrayType(elemType: TypeSpec) extends OptionableTypeSpec with ActualTypeSpec

        case class OptionalType(valueType: OptionableTypeSpec) extends TypeSpec with ActualTypeSpec

        case class NodeType(fixedType: Option[TypeSpec], inferredTypes: Set[TypeSpec])

        case class Extends(superType: TypeSpec, subType: TypeSpec) extends AbstractEdge[TypeSpec] {
            val start: TypeSpec = superType
            val end: TypeSpec = subType
        }

        class TypeHierarchyGraph(val nodes: Set[TypeSpec], val edges: Set[Extends],
                                 val edgesByStart: Map[TypeSpec, Set[Extends]],
                                 val edgesByEnd: Map[TypeSpec, Set[Extends]])
            extends AbstractGraph[TypeSpec, Extends, TypeHierarchyGraph] {

            def createGraph(nodes: Set[TypeSpec], edges: Set[Extends], edgesByStart: Map[TypeSpec, Set[Extends]], edgesByEnd: Map[TypeSpec, Set[Extends]]): TypeHierarchyGraph =
                new TypeHierarchyGraph(nodes, edges, edgesByStart, edgesByEnd)

            def unrollArrayAndOptionals: TypeHierarchyGraph = {
                // Array(Type A) --> Array(Type B) 이나 Optional(Type A) --> Optional(Type B) 엣지가 있으면 Type A --> Type B 노드 추가
                var unrolled = this
                unrolled
            }

            def pruneRedundantEdges: TypeHierarchyGraph = {
                var cleaned = this
                edges foreach { edge =>
                    val paths = GraphUtil.pathsBetween[TypeSpec, Extends, TypeHierarchyGraph](cleaned, edge.start, edge.end)
                    if (paths.edges.size > 1) {
                        cleaned = cleaned.removeEdge(edge)
                    }
                }
                cleaned
            }

            def removeRedundantTypesFrom(types: List[TypeSpec]): List[TypeSpec] = {
                // types에서 불필요한 타입 제거. 즉, supertype A와 subtype B가 같이 포함되어 있으면 B는 제거하고 A만 남겨서 반환
                ???
            }

            def toDotGraphModel: DotGraphModel = {
                val nodesMap = (nodes.zipWithIndex map { case (n, i) =>
                    n -> DotGraphModel.Node(s"n$i")(n.toString).attr("shape", "rect")
                }).toMap
                new DotGraphModel(nodesMap.values.toSet, edges.toSeq map { e => DotGraphModel.Edge(nodesMap(e.start), nodesMap(e.end)) })
            }
        }

        class TypeDependenceGraph private(val nodes: Set[TypeDependenceGraph.Node],
                                          val edges: Set[TypeDependenceGraph.Edge],
                                          val edgesByStart: Map[TypeDependenceGraph.Node, Set[TypeDependenceGraph.Edge]],
                                          val edgesByEnd: Map[TypeDependenceGraph.Node, Set[TypeDependenceGraph.Edge]])
            extends AbstractGraph[TypeDependenceGraph.Node, TypeDependenceGraph.Edge, TypeDependenceGraph] {

            override def createGraph(nodes: Set[TypeDependenceGraph.Node], edges: Set[TypeDependenceGraph.Edge],
                                     edgesByStart: Map[TypeDependenceGraph.Node, Set[TypeDependenceGraph.Edge]],
                                     edgesByEnd: Map[TypeDependenceGraph.Node, Set[TypeDependenceGraph.Edge]]): TypeDependenceGraph =
                new TypeDependenceGraph(nodes, edges, edgesByStart, edgesByEnd)

            private val visited = scala.collection.mutable.Set[TypeDependenceGraph.Node]()
            private val typeTypeMemo = Memoize[TypeDependenceGraph.TypeNode, ActualTypeSpec]()
            private val elemTypeMemo = Memoize[TypeDependenceGraph.ElemNode, NodeType]()

            private def finalTypeOf(nodeType: NodeType): TypeSpec = nodeType.fixedType match {
                case Some(fixedType) => fixedType
                case None =>
                    if (nodeType.inferredTypes.size == 1) nodeType.inferredTypes.toList.head else UnionType(Set(nodeType))
            }

            private def createTypeSpec(typeNode: TypeDependenceGraph.TypeNode): ActualTypeSpec = typeTypeMemo(typeNode) {
                typeNode match {
                    case TypeDependenceGraph.ClassTypeNode(className) => ClassType(className)
                    case TypeDependenceGraph.TypeArray(elemType) => ArrayType(createTypeSpec(elemType))
                    case TypeDependenceGraph.TypeOptional(elemType) => OptionalType(createTypeSpec(elemType).asInstanceOf[OptionableTypeSpec])
                    case TypeDependenceGraph.TypeGenArray(typeof) => ArrayType(finalTypeOf(inferType(typeof)))
                    case TypeDependenceGraph.TypeGenOptional(typeof) => OptionalType(finalTypeOf(inferType(typeof)).asInstanceOf[OptionableTypeSpec])
                    case TypeDependenceGraph.TypeGenArrayConcatOp(_, lhs, rhs) =>
                        val lhsType = inferType(lhs)
                        val rhsType = inferType(rhs)
                        ArrayType(UnionType(Set(lhsType, rhsType)))
                    case TypeDependenceGraph.TypeGenArrayElemsUnion(elems) =>
                        val elemsType = elems map inferType
                        ArrayType(UnionType(elemsType.toSet))
                }
            }

            def inferType(elemNode: TypeDependenceGraph.ElemNode): NodeType = elemTypeMemo(elemNode) {
                if (visited contains elemNode) {
                    throw new Exception("Conflicting type hierarchy")
                }
                visited += elemNode

                val fixedTypeDecorators = edgesByStart(elemNode) filter {
                    _.edgeType == TypeDependenceGraph.EdgeTypes.Is
                }
                if (fixedTypeDecorators.size >= 2) {
                    // TODO 이게 가능함?
                    throw new Exception("Duplicate type decorators")
                }

                val fixedType = if (fixedTypeDecorators.isEmpty) None else {
                    Some(createTypeSpec(fixedTypeDecorators.head.end.asInstanceOf[TypeDependenceGraph.TypeNode]))
                }

                val inferredTypes = edgesByStart(elemNode) filter {
                    _.edgeType == TypeDependenceGraph.EdgeTypes.Accepts
                } flatMap { e =>
                    val t = inferType(e.end.asInstanceOf[TypeDependenceGraph.ElemNode])
                    t.fixedType.toSet ++ t.inferredTypes
                }

                elemNode match {
                    case _: TypeDependenceGraph.SymbolNode | _: TypeDependenceGraph.ParamNode =>
                        // SymbolNode --is--> TypeNode
                        // SymbolNode --accepts--> ElemNode
                        if (edgesByStart(elemNode).isEmpty) NodeType(Some(ParseNodeType), Set()) else {
                            NodeType(fixedType, inferredTypes)
                        }
                    case _: TypeDependenceGraph.ExprNode =>
                        assert(edgesByStart(elemNode).nonEmpty)
                        NodeType(fixedType, inferredTypes)
                }
            }

            def typeHierarchyGraph: TypeHierarchyGraph = {
                var hierarchyGraph = new TypeHierarchyGraph(Set(), Set(), Map(), Map())
                nodes collect {
                    case node: TypeDependenceGraph.ElemNode =>
                        val nodeType = inferType(node)
                        if (nodeType.fixedType.isDefined) {
                            val fixedType = nodeType.fixedType.get
                            hierarchyGraph = hierarchyGraph.addNode(fixedType)
                            nodeType.inferredTypes foreach { inferredType =>
                                if (fixedType != inferredType) {
                                    hierarchyGraph = hierarchyGraph.addEdgeSafe(Extends(fixedType, inferredType))
                                }
                            }
                        }
                }
                hierarchyGraph
            }

            def toDotGraphModel: DotGraphModel = {
                val nodesMap = (nodes.zipWithIndex map { case (node, idx) =>
                    val nodeId = s"n$idx"
                    val dotNode0 = DotGraphModel.Node(nodeId)(node.nodeLabel)
                    val dotNode = node match {
                        case _: TypeDependenceGraph.SymbolNode => dotNode0.attr("shape", "rect")
                        case _: TypeDependenceGraph.TypeNode => dotNode0.attr("shape", "tab")
                        case _ => dotNode0
                    }

                    node -> dotNode
                }).toMap
                val edges = this.edges map { edge =>
                    DotGraphModel.Edge(nodesMap(edge.start), nodesMap(edge.end)).attr("label", edge.edgeType.toString)
                }
                new DotGraphModel(nodesMap.values.toSet, edges.toSeq)
            }
        }

        def analyze(): Analysis = {
            collectTypeDefs()
            // TODO check name conflict in userDefinedTypes
            // TODO make sure no type has name the name "Node"

            // userDefinedTypes foreach println

            ruleDefs foreach { rule =>
                astToSymbol(rule.lhs.name)
                rule.rhs foreach { rhs =>
                    rhs.elems foreach {
                        case elemAst: AST.Symbol =>
                            astToSymbol(elemAst)
                        case _ => // do nothing
                    }
                }
            }

            val grammar = new Grammar {
                override val name: String = "Intermediate"

                private def rhsToSeq(rhs: AST.RHS): Symbols.Sequence = Symbols.Sequence(rhs.elems collect {
                    case sym: AST.Symbol =>
                        astToSymbol(sym).asInstanceOf[Symbols.AtomicSymbol]
                })

                override val rules: RuleMap = ListMap[String, ListSet[Symbols.Symbol]](ruleDefs map { ruleDef =>
                    val x = ListSet[Symbols.Symbol](ruleDef.rhs map rhsToSeq: _*)
                    ruleDef.lhs.name.name.toString -> x
                }: _*)
                override val startSymbol: Nonterminal = Symbols.Nonterminal(ruleDefs.head.lhs.name.name.toString)
            }
            val ngrammar = NGrammar.fromGrammar(grammar)

            val typeDependenceGraph = new TypeDependenceGraph.Builder().analyze()

            typeDependenceGraph.nodes foreach println
            println()
            typeDependenceGraph.edgesByStart foreach { edges =>
                edges._2 foreach println
            }

            typeDependenceGraph.nodes collect {
                case node: TypeDependenceGraph.ElemNode =>
                    val nodeType = typeDependenceGraph.inferType(node)
                    println(s"${node.nodeLabel}: $nodeType")
            }

            val typeHierarchyGraph0 = typeDependenceGraph.typeHierarchyGraph
            println(typeHierarchyGraph0.toDotGraphModel.printDotGraph())
            val typeHierarchyGraph = typeHierarchyGraph0.pruneRedundantEdges
            println(typeHierarchyGraph.toDotGraphModel.printDotGraph())

            println(userDefinedTypes)

            new Analysis(grammarAst, grammar, ngrammar, typeDependenceGraph, Map(), Map())
        }
    }

    def analyze(grammar: AST.Grammar): Analysis = {
        new Analyzer(grammar).analyze()
    }
}
