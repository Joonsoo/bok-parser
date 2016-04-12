package com.giyeok.moonparser

import com.giyeok.moonparser.ParseTree._
import com.giyeok.moonparser.Symbols._

trait GraphDataStructure {
    this: Parser =>

    type Node = SymbolProgress
    type TerminalNode = SymbolProgressTerminal
    type NonterminalNode = SymbolProgressNonterminal

    sealed abstract class DeriveEdge {
        val start: NonterminalNode
        val end: Node
        val nodes = Set(start, end)

        def endTo(end: Node): Boolean = (this.end == end)

        def toShortString: String
        override def toString = toShortString
    }
    case class SimpleEdge(start: NonterminalNode, end: Node) extends DeriveEdge {
        def toShortString = s"${start.toShortString} -> ${end.toShortString}"
    }
    case class JoinEdge(start: JoinProgress, end: Node, constraint: Node, endConstraintReversed: Boolean) extends DeriveEdge {
        override val nodes = Set(start, end, constraint)
        override def endTo(end: Node): Boolean = super.endTo(end) || end == this.constraint
        def toShortString = s"${start.toShortString} -> ${end.toShortString} & ${constraint.toShortString}${if (endConstraintReversed) " (reverse)" else ""}"
    }

    sealed trait Reverter {
        def toShortString: String = toString
    }
    sealed trait PreReverter extends Reverter
    sealed trait WorkingReverter extends Reverter

    sealed trait DeriveReverter extends PreReverter with WorkingReverter {
        val targetEdge: SimpleEdge
        def withNewTargetEdge(newTargetEdge: SimpleEdge): DeriveReverter
    }
    sealed trait LiftReverter extends PreReverter {
        val targetLifting: Lifting
    }
    sealed trait NodeKillReverter extends WorkingReverter {
        val targetNode: Node
    }

    sealed trait ReverterTrigger extends Reverter
    sealed trait ConditionalTrigger extends ReverterTrigger
    sealed trait LiftTriggered extends ConditionalTrigger {
        val trigger: Node
    }
    sealed trait MultiLiftTriggered extends ConditionalTrigger {
        // 이 트리거가 "모두" 만족되어야 말동
        val triggers: Set[Node]
    }
    sealed trait AlwaysTriggered extends ReverterTrigger

    case class LiftTriggeredDeriveReverter(trigger: Node, targetEdge: SimpleEdge) extends LiftTriggered with DeriveReverter {
        def withNewTargetEdge(newTargetEdge: SimpleEdge) = LiftTriggeredDeriveReverter(trigger, newTargetEdge)
    }
    case class LiftTriggeredLiftReverter(trigger: Node, targetLifting: Lifting) extends LiftTriggered with LiftReverter
    case class AlwaysTriggeredLiftReverter(trigger: Node, targetLifting: Lifting) extends AlwaysTriggered with LiftReverter
    case class MultiLiftTriggeredNodeKillReverter(triggers: Set[Node], targetNode: Node) extends MultiLiftTriggered with NodeKillReverter

    implicit class AugEdges(edges: Set[DeriveEdge]) {
        def simpleEdges: Set[SimpleEdge] = edges collect { case e: SimpleEdge => e }

        def incomingEdgesOf(node: Node): Set[DeriveEdge] = edges filter { _.end == node }
        def incomingSimpleEdgesOf(node: Node): Set[SimpleEdge] = simpleEdges filter { _.end == node }
        def outgoingEdgesOf(node: Node): Set[DeriveEdge] = edges filter { _.start == node }
        def outgoingSimpleEdgesOf(node: Node): Set[SimpleEdge] = simpleEdges filter { _.start == node }

        def rootsOf(node: Node): Set[DeriveEdge] = {
            def trackRoots(queue: List[SymbolProgress], cc: Set[DeriveEdge]): Set[DeriveEdge] =
                queue match {
                    case node +: rest =>
                        val incomings = incomingEdgesOf(node) -- cc
                        trackRoots(rest ++ (incomings.toList map { _.start }), cc ++ incomings)
                    case List() => cc
                }
            trackRoots(List(node), Set())
        }
    }

}
