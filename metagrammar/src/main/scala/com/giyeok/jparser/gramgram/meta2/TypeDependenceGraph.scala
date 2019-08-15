package com.giyeok.jparser.gramgram.meta2

import com.giyeok.jparser.Symbols
import com.giyeok.jparser.utils._

object TypeDependenceGraph {
    val emptyGraph = new TypeDependenceGraph(Set(), Set(), Map(), Map())

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
            if (nodeType.inferredTypes.size == 1) nodeType.inferredTypes.toList.head else UnionNodeType(Set(nodeType))
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
                ArrayType(UnionNodeType(Set(lhsType, rhsType)))
            case TypeDependenceGraph.TypeGenArrayElemsUnion(elems) =>
                val elemsType = elems map inferType
                ArrayType(UnionNodeType(elemsType.toSet))
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
        // TODO extends edge 고려 추가
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

    def cleanType(typ: TypeSpec): TypeSpec = typ match {
        case ParseNodeType | _: ClassType => typ
        case ArrayType(elemType) =>
            ArrayType(cleanType(elemType))
        case OptionalType(valueType) =>
            OptionalType(cleanType(valueType).asInstanceOf[OptionableTypeSpec])
        case UnionNodeType(types) =>
            val cleanTypes = types flatMap { nodeType =>
                nodeType.fixedType match {
                    case Some(fixedType) => Set(cleanType(fixedType))
                    case None => removeRedundantTypesFrom(nodeType.inferredTypes) map cleanType
                }
            }
            if (cleanTypes.size == 1) cleanTypes.head else UnionType(cleanTypes)
    }

    // types에서 불필요한 타입 제거. 즉, supertype A와 subtype B가 같이 포함되어 있으면 B는 제거하고 A만 남겨서 반환
    def removeRedundantTypesFrom(types: Set[TypeSpec]): Set[TypeSpec] =
        types filterNot { sub =>
            (types - sub) exists { sup => reachableBetween(sup, sub) }
        }

    def toDotGraphModel: DotGraphModel = {
        val nodesMap = (nodes.zipWithIndex map { case (n, i) =>
            n -> DotGraphModel.Node(s"n$i")(n.toString).attr("shape", "rect")
        }).toMap
        new DotGraphModel(nodesMap.values.toSet, edges.toSeq map { e => DotGraphModel.Edge(nodesMap(e.start), nodesMap(e.end)) })
    }

    def topologicalClassTypesOrder: List[ClassType] = {
        val classTypes = nodes collect { case t: ClassType => t }
        // TODO
        classTypes.toList.sortBy(_.className)
    }
}