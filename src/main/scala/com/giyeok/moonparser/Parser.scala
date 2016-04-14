package com.giyeok.moonparser

case class ParseResult(parseNode: ParseTree.ParseNode[Symbols.Symbol])

class Parser(val grammar: Grammar)
        extends SymbolProgresses
        with GraphDataStructure
        with ParsingErrors {
    import Inputs._
    import Symbols.Symbol

    sealed trait Lifting {
        val before: SymbolProgress
        val after: SymbolProgress
        def toShortString: String
    }
    case class TermLifting(before: SymbolProgressTerminal, after: SymbolProgressTerminal, by: Input) extends Lifting {
        def toShortString = s"${before.toShortString} => ${after.toShortString} (by ${by.toShortString})"
    }
    case class NontermLifting(before: SymbolProgressNonterminal, after: SymbolProgressNonterminal, by: SymbolProgress, edge: DeriveEdge) extends Lifting {
        def toShortString = s"${before.toShortString} => ${after.toShortString} (by ${by.toShortString})"
    }
    // DeriveReverter에서 각 Lifting이 어떤 DeriveEdge를 거쳐서 나온 것인지 알 필요가 있음.

    case class VerboseProceedLog(
        activatedReverters: Set[WorkingReverter],
        terminalLiftings: Set[TermLifting],
        liftings: Set[Lifting],
        newNodes: Set[Node],
        newEdges: Set[DeriveEdge],
        newReverters: Set[PreReverter],
        proceededEdges: Map[SimpleEdge, SimpleEdge],
        roots: Set[DeriveEdge],
        revertersLog: Map[Reverter, String],
        finalNodes: Set[Node],
        finalEdges: Set[DeriveEdge],
        finalReverters: Set[WorkingReverter],
        revertedNodes: Set[Node],
        revertedEdges: Set[DeriveEdge],
        liftBlockedNodes: Set[Node])

    val logConfs = Map[String, Boolean](
        "PCG" -> true,
        "expand" -> false,
        "proceedTerminal" -> false,
        "initialPC" -> false,
        "reverters" -> false,
        "reverterKill" -> false)
    def logging(logType: String)(block: => Unit): Unit = {
        logConfs get logType match {
            case Some(true) => block
            case Some(false) => // do nothing
            case None =>
                throw new Exception("Unknown log type: " + logType)
        }
    }
    def logging(logType: String, str: String): Unit = {
        logging(logType) {
            println(str)
        }
    }

    sealed trait ExpandTask
    case class DeriveTask(node: NonterminalNode) extends ExpandTask
    case class LiftTask(lifting: Lifting) extends ExpandTask

    case class ExpandResult(liftings: Set[Lifting], nodes: Set[Node], edges: Set[DeriveEdge], reverters: Set[PreReverter], proceededEdges: Map[SimpleEdge, SimpleEdge]) {
        // proceededEdges: 이전 세대의 DeriveEdge중 내용이 바뀌어서 추가되는 DeriveEdge
        def withNode(newNode: Node) = ExpandResult(liftings, nodes + newNode, edges, reverters, proceededEdges)
        def withNodes(newNodes: Set[Node]) = ExpandResult(liftings, nodes ++ newNodes, edges, reverters, proceededEdges)
        def withLifting(newLifting: Lifting) = ExpandResult(liftings + newLifting, nodes, edges, reverters, proceededEdges)
        def withLiftings(newLiftings: Set[Lifting]) = ExpandResult(liftings ++ newLiftings, nodes, edges, reverters, proceededEdges)
        def withProceededEdge(newProceededEdge: (SimpleEdge, SimpleEdge)) = ExpandResult(liftings, nodes, edges, reverters, proceededEdges + newProceededEdge)
        def withProceededEdges(newProceededEdges: Map[SimpleEdge, SimpleEdge]) = ExpandResult(liftings, nodes, edges, reverters, proceededEdges ++ newProceededEdges)
        def withEdges(newEdges: Set[DeriveEdge]) = ExpandResult(liftings, nodes, edges ++ newEdges, reverters, proceededEdges)
        def withReverters(newReverters: Set[PreReverter]) = ExpandResult(liftings, nodes, edges, reverters ++ newReverters, proceededEdges)
    }

    def expand(oldNodes: Set[Node], oldEdges: Set[DeriveEdge], liftBlockedNodes: Set[Node], newGenId: Int, queue: List[ExpandTask]): ExpandResult = {
        case class Qcc(queue: List[ExpandTask], cc: ExpandResult) {
            // Queue and CC
            def withReverters(newReverters: Set[PreReverter]): Qcc = {
                val reverterTriggers = newReverters collect {
                    case x: LiftTriggered => x.trigger
                    case x: AliveTriggered => x.trigger
                }
                val newTriggerNodes = (reverterTriggers -- oldNodes -- cc.nodes)
                logging("reverters") {
                    println(reverterTriggers)
                    if (!newTriggerNodes.isEmpty) {
                        println(newTriggerNodes)
                    }
                }
                val newReverterTriggerDeriveTasks = newTriggerNodes collect { case x: NonterminalNode => x } map { DeriveTask(_) }
                Qcc(newReverterTriggerDeriveTasks.toList ++ queue, cc.withNodes(reverterTriggers).withReverters(newReverters))
            }
            def withLiftings(newLiftings: Set[Lifting]): Qcc = {
                val treatedLiftings = newLiftings filterNot { liftBlockedNodes contains _.before }
                val newLiftTasks = treatedLiftings map { LiftTask(_) }
                Qcc(newLiftTasks.toList ++: queue, cc.withLiftings(treatedLiftings))
            }
            def withLiftingsAndReverters(newLiftings: Set[Lifting], newReverters: Set[PreReverter]): Qcc = {
                withLiftings(newLiftings).withReverters(newReverters)
            }
            def withNewNodesAndEdges(newNodes: Set[Node], newEdges: Set[DeriveEdge]): Qcc = {
                val newDeriveTasks = newNodes collect { case n: NonterminalNode => DeriveTask(n) }
                val newCC = cc.withNodes(newNodes).withEdges(newEdges)
                Qcc(newDeriveTasks.toList ++: queue, newCC)
            }
            def withNewLiftedNodeAndProceededEdges(liftedNode: NonterminalNode, proceededEdges: Map[SimpleEdge, SimpleEdge]): Qcc = {
                Qcc(DeriveTask(liftedNode) +: queue, cc.withNode(liftedNode).withEdges(proceededEdges.values.toSet).withProceededEdges(proceededEdges))
            }
        }

        def expand0(queue: List[ExpandTask], allTasksCC: Set[ExpandTask], cc: ExpandResult): (Set[ExpandTask], ExpandResult) = {
            val allEdges: Set[DeriveEdge] = oldEdges ++ cc.edges
            logging("expand") {
                println("left queues:")
                queue foreach { q => println(s"  $q") }
            }

            // TODO queue에 중복된 아이템 2개 들어가지 않도록 수정
            queue match {
                case task +: rest => task match {
                    case DeriveTask(node) =>
                        // `node`로부터 derive 처리
                        logging("expand", s"DeriveTask($node)")
                        assert(cc.nodes contains node)

                        var nextQcc = Qcc(rest, cc)

                        // `node`에서 derive할 수 있는 edge를 모두 찾고, 이미 처리된 edge는 제외
                        val (derivedEdges: Set[DeriveEdge], derivedReverters: Set[PreReverter]) = node.derive(newGenId)
                        val newDerivedEdges: Set[DeriveEdge] = derivedEdges -- cc.edges
                        val newDerivedReverters: Set[PreReverter] = derivedReverters -- cc.reverters
                        // `newNode`에서는 cc.nodes를 빼면 안됨. 이걸 빼면 아래 "이미 처리된 노드가 lift된 경우"가 확인이 안됨
                        val newNodes: Set[Node] = newDerivedEdges.flatMap(_.nodes)

                        nextQcc = nextQcc.withNewNodesAndEdges(newNodes, newDerivedEdges).withReverters(newDerivedReverters)

                        logging("expand") {
                            newDerivedEdges foreach { edge =>
                                println("  " + edge)
                            }
                        }

                        // nullable한 것들은 바로 lift처리한다
                        val (newLiftings, newReverters): (Set[Lifting], Set[PreReverter]) = {
                            val lifts = newDerivedEdges collect {
                                case e: SimpleEdge if e.end.canFinish => node.lift(e.end, e)
                            }
                            (lifts map { _._1 }, lifts flatMap { _._2 })
                        }

                        logging("expand") {
                            newLiftings foreach { lifting =>
                                println("  " + lifting)
                            }
                        }

                        nextQcc = nextQcc.withLiftingsAndReverters(newLiftings, newReverters)

                        // 새로 만들어진 노드가 이미 처리된 노드인 경우, 이미 처리된 노드가 lift되었을 경우를 확인해서 처리(SimpleGrammar6 참고)
                        val allNodes = allEdges flatMap { _.nodes }
                        val alreadyProcessedNodes: Set[NonterminalNode] = newNodes.intersect(allNodes) collect { case n: NonterminalNode => n }
                        val (alreadyProcessedNodesLifting, alreadyProcessedReverters): (Set[Lifting], Set[PreReverter]) = {
                            val x = alreadyProcessedNodes map { n =>
                                val lifters: Set[(Node, DeriveEdge)] = cc.liftings collect { case NontermLifting(before, _, by, edge) if before == n => (by, edge) }
                                val lifts: Set[(Lifting, Set[PreReverter])] = lifters map { se => n.lift(se._1, se._2) }
                                (lifts map { _._1 }, lifts flatMap { _._2 })
                            }
                            (x flatMap { _._1 }, x flatMap { _._2 })
                        }
                        nextQcc = nextQcc.withLiftingsAndReverters(alreadyProcessedNodesLifting, alreadyProcessedReverters)

                        expand0(nextQcc.queue, allTasksCC + task, nextQcc.cc)

                    case LiftTask(TermLifting(before, after, by)) =>
                        // terminal element가 lift되는 경우 처리
                        logging("expand", s"TermLiftTask($before, $after, $by)")

                        // terminal element는 항상 before는 비어있고 after는 한 글자로 차 있어야 하며, 정의상 둘 다 derive가 불가능하다.
                        assert(!before.canFinish)
                        assert(after.canFinish)

                        // 또 이번에 생성된 terminal element가 바로 lift되는 것은 불가능하므로 before는 반드시 oldGraph 소속이어야 한다.
                        assert(oldNodes contains before)

                        var nextQcc = Qcc(rest, cc)

                        allEdges.incomingEdgesOf(before) foreach { edge =>
                            edge match {
                                case e: SimpleEdge =>
                                    val (lifting, newReverters): (Lifting, Set[PreReverter]) = e.start.lift(after, e)
                                    nextQcc = nextQcc.withLiftingsAndReverters(Set(lifting), newReverters)
                                case e: JoinEdge =>
                                    val constraint: Option[Lifting] = cc.liftings.find { _.before == e.constraint }
                                    if (constraint.isDefined) {
                                        val lifting = e.start.liftJoin(after, constraint.get.after, e)
                                        nextQcc = nextQcc.withLiftings(Set(lifting))
                                    }
                            }
                        }

                        expand0(nextQcc.queue, allTasksCC + task, nextQcc.cc)

                    case LiftTask(NontermLifting(before, after, by, _)) =>
                        // nonterminal element가 lift되는 경우 처리
                        // 문제가 되는 lift는 전부 여기 문제
                        logging("expand", s"NontermLiftTask($before, $after, $by)")

                        var nextQcc = Qcc(rest, cc)

                        val incomingDeriveEdges = allEdges.incomingEdgesOf(before)

                        // lift된 node, 즉 `after`가 derive를 갖는 경우
                        // - 이런 경우는, `after`가 앞으로도 추가로 처리될 가능성이 있다는 의미
                        // - 따라서 새 그래프에 `after`를 추가해주고, `before`를 rootTip에 추가해서 추가적인 처리를 준비해야 함
                        val (afterDerives, afterReverters): (Set[DeriveEdge], Set[PreReverter]) = after.derive(newGenId)
                        // (afterDerives.isEmpty) 이면 (afterReverters.isEmpty) 이다
                        assert(!afterDerives.isEmpty || afterReverters.isEmpty)
                        if (!afterDerives.isEmpty) {
                            logging("expand", "  hasDerives")

                            val proceededEdges: Map[SimpleEdge, SimpleEdge] = (incomingDeriveEdges map { edge =>
                                edge match {
                                    case e: SimpleEdge =>
                                        (e -> SimpleEdge(e.start, after))
                                    case e: JoinEdge =>
                                        // should never be called (because of proxy)
                                        println(before, after)
                                        println(e)
                                        throw new java.lang.AssertionError("should not happen")
                                }
                            }).toMap
                            nextQcc = nextQcc.withNewLiftedNodeAndProceededEdges(after, proceededEdges).withReverters(afterReverters)
                        }

                        // lift된 node, 즉 `after`가 canFinish인 경우
                        // - 이런 경우는, `after`가 (derive가 가능한가와는 무관하게) 완성된 상태이며, 이 노드에 영향을 받는 다른 노드들을 lift해야 한다는 의미
                        // - 따라서 `after`를 바라보고 있는 노드들을 lift해서 LiftTask를 추가해주어야 함
                        if (after.canFinish) {
                            logging("expand", "  isCanFinish")
                            incomingDeriveEdges foreach { edge =>
                                assert(before == edge.end)
                                edge match {
                                    case e: SimpleEdge =>
                                        val (lifting, newReverters) = e.start.lift(after, e)
                                        nextQcc = nextQcc.withLiftingsAndReverters(Set(lifting), newReverters)
                                    case e: JoinEdge =>
                                        val constraintLifted = cc.liftings filter { _.before == e.constraint }
                                        if (!constraintLifted.isEmpty) {
                                            // println(before, after)
                                            // println(e)
                                            // println(constraintLifted)
                                            val liftings = constraintLifted map { constraint =>
                                                if (!e.endConstraintReversed) e.start.liftJoin(after, constraint.after, e)
                                                else e.start.liftJoin(constraint.after, after, e)
                                            }
                                            nextQcc = nextQcc.withLiftings(liftings)
                                        }
                                    // just ignore if the constraint is not matched
                                }
                            }
                        }

                        expand0(nextQcc.queue, allTasksCC + task, nextQcc.cc)
                }
                case List() => (allTasksCC, cc)
            }
        }
        val initialLiftings: Set[Lifting] = (queue collect { case LiftTask(lifting) => lifting }).toSet
        val initialNodes: Set[Node] = (queue collect { case DeriveTask(node) => node }).toSet
        val (allTasks, result) = expand0(queue, Set(), ExpandResult(initialLiftings, initialNodes, Set(), Set(), Map()))
        logging("PCG") {
            println(newGenId)
            println(allTasks.filter(_.isInstanceOf[DeriveTask]).size, allTasks.filter(_.isInstanceOf[DeriveTask]))
            println(allTasks.size, allTasks)
        }
        // nullable한 node는 바로 lift가 되어서 바로 proceededEdges에 추가될 수 있어서 아래 assert는 맞지 않음
        // assert((result.proceededEdges map { _._1 }).toSet subsetOf oldEdges)
        // assert((result.proceededEdges map { _._2.start }).toSet subsetOf oldNodes)
        assert((result.proceededEdges map { _._2 }).toSet subsetOf result.edges)
        assert((result.proceededEdges map { _._2.end }).toSet subsetOf result.nodes)
        assert(result.proceededEdges forall { oldNew => (oldNew._1.start == oldNew._2.start) })
        // assert(result.proceededEdges forall { oldNodes contains _._1.start }) // 이건 만족해야되지 않나?

        // oldNodes의 노드들로부터 derive되는 경우가 있으면 안됨
        assert(((allTasks collect { case DeriveTask(node) => node }).asInstanceOf[Set[Node]] intersect oldNodes).isEmpty)
        result
    }

    def rootTipsOfProceededEdges(proceededEdges: Map[SimpleEdge, SimpleEdge]): Set[Node] =
        proceededEdges.keySet map { _.start }

    def proceedReverters(oldReverters: Set[WorkingReverter], newReverters: Set[PreReverter], liftings: Set[Lifting], proceededEdges: Map[SimpleEdge, SimpleEdge]): Set[WorkingReverter] = {
        val nontermLiftings: Set[NontermLifting] = liftings collect { case nl: NontermLifting => nl }
        var newLiftReverters: Set[LiftReverter] = newReverters collect { case lr: LiftReverter => lr }

        val newDeriveReverters: Set[DeriveReverter] = newReverters collect { case dr: DeriveReverter => dr }
        val oldDeriveReverters: Set[DeriveReverter] = oldReverters collect { case dr: DeriveReverter => dr }

        // DeriveReveter에 대한 처리
        //  - DeriveReverter는 LiftTriggeredDeriveReverter밖에 없음
        def proceedDeriveReverters(queue: List[DeriveReverter], cc: Set[DeriveReverter]): Set[DeriveReverter] = queue match {
            case (reverter: LiftTriggeredDeriveReverter) +: rest =>
                // 제거 대상인 DeriveEdge로 인해 발생한 Lifting도 삭제 대상으로 포함
                newLiftReverters ++= (nontermLiftings filter { _.edge == reverter.targetEdge } map { lifting => LiftTriggeredLiftReverter(reverter.trigger, lifting) })
                // proceededEdges의 정보를 바탕으로 기존의 DeriveEdge에서 변경된 DeriveEdge가 있는 경우 변경된 DeriveEdge도 revert 대상으로 추가
                proceededEdges get reverter.targetEdge match {
                    case Some(newEdge) =>
                        val newReverter = LiftTriggeredDeriveReverter(reverter.trigger, newEdge)
                        if (cc contains newReverter) {
                            proceedDeriveReverters(rest, cc)
                        } else {
                            proceedDeriveReverters(newReverter +: rest, cc + newReverter)
                        }
                    case None => proceedDeriveReverters(rest, cc)
                }
            case List() => cc
        }
        val initDeriveReverters = newDeriveReverters ++ oldDeriveReverters
        val workingDeriveReverters: Set[DeriveReverter] = proceedDeriveReverters(initDeriveReverters.toList, initDeriveReverters)

        // LiftReverter에 대한 처리(LiftReverter 확산)
        //  - ParseNode에 심볼 정보가 포함되어 있기 때문에 같은 ParseNode를 by로 가진 Lifting이나 SymbolProgress가 생성되는 경로는 유일하다
        //  - chain lift 경로같은걸 liftPath같은 형태로 저장할 수도 있겠지만, 어차피 ParseNode의 계층 구조를 따라서 DeriveEdge들을 쫓아 가면 마찬가지 결과가 나올 것이다(TODO 이건 나중에 해볼것)
        // deriveRevertersFromLiftReverters는 targetLift.after에 의해 derive된 DeriveEdge가 있으면 그 엣지들을 포함한 것인데 이게 정말 필요한지는 확인해봐야 함(어차피 lift 제거시에 다 처리돼서 필요 없을 것 같긴 함)
        // var deriveRevertersFromLiftReverters: Set[DeriveReverter] = ???
        def proceedLiftReverters(queue: List[LiftReverter], cc: Set[LiftReverter]): Set[LiftReverter] = queue match {
            case reverter +: rest =>
                // 제거 대상인 targetLift로 인해 발생한 Lifting도 삭제 대상으로 포함 - `by` ParseNode가 동일하다는 건 해당 리프팅이 생성된 경로가 targetLift를 마지막으로 거쳤음을 의미한다
                val newTargets: Set[NontermLifting] = nontermLiftings filter { _.by == reverter.targetLifting.after }
                val newReverters0: Set[LiftReverter] = newTargets map { newTarget => reverter.withNewTargetLifting(newTarget) }
                val newReverters = newReverters0 -- cc
                proceedLiftReverters(newReverters.toList ++: rest, cc ++ newReverters)
            case List() => cc
        }
        val initLiftReverters = newLiftReverters
        val treatedLiftReverters: Set[LiftReverter] = proceedLiftReverters(newLiftReverters.toList, newLiftReverters) filter { _.targetLifting.after.canFinish }

        // LiftReverter -> NodeKillReverter 변환
        // val liftingsByAfter: Map[Node, Set[Lifting]] = liftings groupBy { _.after } // 이 조건 비교는 불필요할듯
        val liftRevertersByLiftingAfter: Map[Node, Set[LiftReverter]] = treatedLiftReverters groupBy { _.targetLifting.after }
        val workingNodeKillReverters: Set[NodeKillReverter] = (liftRevertersByLiftingAfter map { kv =>
            val (affectedNode, liftReverters) = kv
            val triggers: Set[ReverterTrigger] = liftReverters map {
                _ match {
                    case x: LiftTriggeredLiftReverter => LiftTrigger(x.trigger)
                    case x: AliveTriggeredLiftReverter => AliveTrigger(x.trigger)
                }
            }
            // 실은 MultiLift도 필요 없을지 몰라
            MultiTriggeredNodeKillReverter(triggers, affectedNode)
        }).toSet

        // 기존의 NodeKillReverter -> 새 NodeKillReverter로 변환
        // TODO

        // TemporaryLiftBlockedReverter는 그냥 그대로 가면 되나?
        // TODO 일단 그냥 가게 해놨는데 잘 보고 필요하면 고칠것
        val workingTempLiftBlockReverters: Set[TemporaryLiftBlockReverter] =
            (oldReverters collect { case x: TemporaryLiftBlockReverter => x }) ++
                (newReverters collect { case x: TemporaryLiftBlockReverter => x })

        workingDeriveReverters ++ workingNodeKillReverters ++ workingTempLiftBlockReverters
    }

    def collectResultCandidates(liftings: Set[Lifting]): Set[Node] =
        liftings map { _.after } filter { _.symbol == grammar.startSymbol } collect {
            case n: SymbolProgressNonterminal if n.derivedGen == 0 && n.canFinish => n
        }

    // 이 프로젝트 전체에서 asInstanceOf가 등장하는 경우는 대부분이 Set이 invariant해서 추가된 부분 - covariant한 Set으로 바꾸면 없앨 수 있음
    case class ParsingContext(gen: Int, nodes: Set[Node], edges: Set[DeriveEdge], reverters: Set[WorkingReverter], resultCandidates: Set[SymbolProgress]) {
        logging("reverters") {
            println(s"- Reverters @ $gen")
            reverters foreach { r =>
                println(r)
            }
        }

        def proceedTerminal1(next: Input): Set[TermLifting] =
            (nodes flatMap {
                case s: SymbolProgressTerminal => (s proceedTerminal next) map { TermLifting(s, _, next) }
                case _ => None
            })
        def proceedTerminalVerbose(next: Input): (Either[(ParsingContext, VerboseProceedLog), ParsingError]) = {
            // `nextNodes` is actually type of `Set[(SymbolProgressTerminal, SymbolProgressTerminal)]`
            // but the invariance of `Set` of Scala, which I don't understand why, it is defined as Set[(SymbolProgress, SymbolProgress)]
            logging("proceedTerminal") {
                println(s"**** New Generation $gen -> ${gen + 1}")

                edges foreach { edge => println(edge.toShortString) }
                println()
            }

            val terminalLiftings0: Set[TermLifting] = proceedTerminal1(next)
            if (terminalLiftings0.isEmpty) {
                Right(ParsingErrors.UnexpectedInput(next))
            } else {
                val nextGenId = gen + 1

                val expand0 = expand(nodes, edges, Set(), nextGenId, terminalLiftings0.toList map { lifting => LiftTask(lifting) })

                var revertersLog = Map[Reverter, String]()

                val ExpandResult(liftings0, newNodes0, newEdges0, newReverters0, proceededEdges0) = expand0

                assert(terminalLiftings0.asInstanceOf[Set[Lifting]] subsetOf liftings0)
                val rootNodes0: Set[Node] = rootTipsOfProceededEdges(proceededEdges0) flatMap { edges.rootsOf(_) } flatMap { _.nodes }
                val activatedReverters: Set[WorkingReverter] = reverters filter {
                    _ match {
                        case r: LiftTriggered => liftings0 exists { _.before == r.trigger }
                        case r: MultiLiftTriggered => r.triggers forall {
                            _ match {
                                case LiftTrigger(trigger) => liftings0 exists { _.before == trigger }
                                case AliveTrigger(trigger) =>
                                    // 여기가 true이면 AlwaysTriggered랑 똑같음
                                    // ExpandResult가 나타내는 그래프에서(루트 포함) trigger가 살아있으면 activated되어야 하므로 true, 아니면 false
                                    rootNodes0 contains trigger
                            }
                        }
                    }
                }

                logging("reverters")(s"activated: $activatedReverters")

                val (terminalLiftings: Set[TermLifting], (treatedNodes: Set[Node], treatedEdges: Set[DeriveEdge], liftBlockedNodes: Set[Node]), ExpandResult(liftings, newNodes, newEdges, newReverters, proceededEdges)) = {
                    if (activatedReverters.isEmpty) {
                        (terminalLiftings0, (nodes, edges, Set()), expand0)
                    } else {
                        sealed trait KillTask
                        case class EdgeKill(edge: DeriveEdge) extends KillTask
                        case class NodeKill(node: Node) extends KillTask

                        def collectKills(queue: List[KillTask], nodesCC: Set[Node], edgesCC: Set[DeriveEdge]): (Set[Node], Set[DeriveEdge]) =
                            queue match {
                                case task +: rest =>
                                    logging("reverterKill", s"Reverter Kill Task: $task @ $gen")
                                    task match {
                                        case EdgeKill(edge) =>
                                            assert(!(edgesCC contains edge))
                                            edge match {
                                                case SimpleEdge(start, end) =>
                                                    if (edgesCC.incomingEdgesOf(end).isEmpty) {
                                                        collectKills(NodeKill(end) +: rest, nodesCC - end, edgesCC)
                                                    } else {
                                                        collectKills(rest, nodesCC, edgesCC)
                                                    }
                                                case JoinEdge(start, end, join, _) =>
                                                    if (edgesCC.incomingEdgesOf(end).isEmpty) {
                                                        collectKills(NodeKill(end) +: rest, nodesCC - end, edgesCC)
                                                    } else {
                                                        collectKills(rest, nodesCC, edgesCC)
                                                    }
                                            }
                                        case NodeKill(node) =>
                                            assert(!(nodesCC contains node))
                                            val relatedEdges: Set[DeriveEdge] = edgesCC.incomingEdgesOf(node) ++ edgesCC.outgoingEdgesOf(node)
                                            val relatedEdgesKillTasks: List[EdgeKill] = relatedEdges.toList map { EdgeKill(_) }
                                            collectKills(relatedEdgesKillTasks ++ rest, nodesCC - node, edgesCC -- relatedEdges)
                                    }
                                case List() => (nodesCC, edgesCC)
                            }
                        val killEdges = activatedReverters collect { case x: DeriveReverter => x.targetEdge }
                        val killNodes = activatedReverters collect { case x: NodeKillReverter => x.targetNode }
                        val killTasks: List[KillTask] = (killEdges.toList map { EdgeKill(_) }) ++ (killNodes.toList map { NodeKill(_) })
                        val (treatedNodes, treatedEdges) = collectKills(killTasks, nodes -- killNodes, edges -- killEdges)

                        val liftBlockedNodes = activatedReverters collect { case x: TemporaryLiftBlockReverter => x.targetNode }
                        logging("reverters", s"LiftBlockedNodes: $gen -> $liftBlockedNodes")

                        val terminalLiftings = (terminalLiftings0 filter { lifting => (treatedNodes contains lifting.before) && !(liftBlockedNodes contains lifting.before) })
                        val expand1 = expand(treatedNodes, treatedEdges, liftBlockedNodes, nextGenId, terminalLiftings.toList map { lifting => LiftTask(lifting) })
                        assert(terminalLiftings.asInstanceOf[Set[Lifting]] subsetOf expand1.liftings)
                        (terminalLiftings, (treatedNodes, treatedEdges, liftBlockedNodes), expand1)
                    }
                }
                val roots: Set[DeriveEdge] = rootTipsOfProceededEdges(proceededEdges) flatMap { treatedEdges.rootsOf(_) }

                val finalEdges = newEdges ++ roots
                val finalNodes = finalEdges flatMap { _.nodes }
                val workingReverters0 = proceedReverters(reverters, newReverters, liftings, proceededEdges)
                val workingReverters = workingReverters0 filter {
                    _ match {
                        case x: DeriveReverter => finalEdges contains x.targetEdge
                        case x: TemporaryLiftBlockReverter => finalNodes contains x.targetNode
                        case x: NodeKillReverter => finalNodes contains x.targetNode
                    }
                }

                logging("proceedTerminal") {
                    println("- liftings")
                    liftings foreach { lifting => println(lifting.toShortString) }
                    println("- newNodes")
                    newNodes foreach { node => println(node.toShortString) }
                    println("- newEdges")
                    newEdges foreach { edge => println(edge.toShortString) }
                    println("- newReverters")
                    newReverters foreach { reverter => println(reverter.toShortString) }
                    println("- proceededEdges")
                    proceededEdges foreach { pe => println(s"${pe._1.toShortString} --> ${pe._2.toShortString}") }

                    println("- roots")
                    roots foreach { edge => println(edge.toShortString) }

                    println("=== Edges before assassin works ===")
                    expand0.edges foreach { edge => println(edge.toShortString) }
                    println("============ End of generation =======")
                }

                val resultCandidates = collectResultCandidates(liftings)
                val nextParsingContext = ParsingContext(gen + 1, finalNodes, finalEdges, workingReverters, resultCandidates)
                val verboseProceedLog = VerboseProceedLog(
                    activatedReverters,
                    terminalLiftings,
                    liftings,
                    newNodes,
                    newEdges,
                    newReverters,
                    proceededEdges,
                    roots,
                    revertersLog,
                    finalNodes,
                    finalEdges,
                    workingReverters,
                    nodes -- treatedNodes,
                    edges -- treatedEdges,
                    liftBlockedNodes)
                Left((nextParsingContext, verboseProceedLog))
            }
        }
        def proceedTerminal(next: Input): Either[ParsingContext, ParsingError] =
            proceedTerminalVerbose(next) match {
                case Left((ctx, _)) => Left(ctx)
                case Right(error) => Right(error)
            }

        def toResult: Option[ParseResult] = {
            if (resultCandidates.size != 1) None
            else resultCandidates.iterator.next.parsed map { ParseResult(_) }
        }
    }

    def assertForAll[T](set: Iterable[T], p: T => Boolean): Unit = {
        val failedSet = set filterNot { p(_) }
        if (!failedSet.isEmpty) {
            println(failedSet)
            assert(failedSet.isEmpty)
        }
    }

    object ParsingContext {
        def fromSeedVerbose(seed: Symbol): (ParsingContext, VerboseProceedLog) = {
            val startProgress = SymbolProgress(seed, 0)
            assert(startProgress.isInstanceOf[SymbolProgressNonterminal])
            val ExpandResult(liftings, nodes, edges, reverters, proceededEdges) = expand(Set(), Set(), Set(), 0, List(DeriveTask(startProgress.asInstanceOf[NonterminalNode])))
            // expand2(seeds.toList, seeds, Set(), Set())

            logging("initialPC") {
                println("- nodes")
                nodes.toSeq.sortBy { _.id } foreach { node => println(node.toShortString) }
                println("- edges")
                edges.toSeq.sortBy { e => (e.start.id, e.end.id) } foreach { edge => println(edge.toShortString) }
                println("- liftings")
                liftings.toSeq.sortBy { l => (l.before.id, l.after.id) } foreach { lifting =>
                    println(lifting.toShortString)
                }
            }

            assert(nodes contains startProgress)
            assertForAll[SymbolProgressNonterminal](nodes collect { case x: SymbolProgressNonterminal => x }, { node =>
                val derivation = node.derive(0)
                (derivation._1 subsetOf edges) && (derivation._2 subsetOf reverters)
            })
            assert(edges forall { _.nodes subsetOf nodes })
            assert((edges flatMap { _.nodes }) == nodes)
            // assert(liftings filter { _.after.canFinish } forall { lifting => graph.edges.incomingSimpleEdgesOf(lifting.before) map { _.start } map { _.lift(lifting.after) } subsetOf liftings })
            assert(liftings filter { !_.after.canFinish } forall { lifting =>
                edges.rootsOf(lifting.before).asInstanceOf[Set[DeriveEdge]] subsetOf edges
            })
            // lifting의 after가 derive가 있는지 없는지에 따라서도 다를텐데..
            //assert(liftings collect { case l @ Lifting(_, after: SymbolProgressNonterminal, _) if !after.canFinish => l } filter { _.after.asInstanceOf[SymbolProgressNonterminal].derive(0).isEmpty } forall { lifting =>
            // graph.edges.rootsOf(lifting.before).asInstanceOf[Set[Edge]] subsetOf graph.edges
            //})

            // val finishable: Set[Lifting] = nodes collect { case n if n.canFinish => Lifting(n, n, None) }

            val workingReverters = proceedReverters(Set(), reverters, liftings, proceededEdges)
            val resultCandidates = collectResultCandidates(liftings)
            val startingContext = ParsingContext(0, nodes, edges, workingReverters, resultCandidates)
            val verboseProceedLog = VerboseProceedLog(
                Set(),
                Set(),
                liftings,
                nodes,
                edges,
                reverters,
                proceededEdges,
                Set(),
                Map(),
                Set(),
                Set(),
                workingReverters,
                Set(),
                Set(),
                Set())
            (startingContext, verboseProceedLog)
        }
        def fromSeed(seed: Symbol): ParsingContext = fromSeedVerbose(seed)._1
    }

    val startingContextVerbose = ParsingContext.fromSeedVerbose(grammar.startSymbol)
    val startingContext = startingContextVerbose._1

    def parse(source: Inputs.Source): Either[ParsingContext, ParsingError] =
        source.foldLeft[Either[ParsingContext, ParsingError]](Left(startingContext)) {
            (ctx, terminal) =>
                ctx match {
                    case Left(ctx) => ctx proceedTerminal terminal
                    case error @ Right(_) => error
                }
        }
    def parse(source: String): Either[ParsingContext, ParsingError] =
        parse(Inputs.fromString(source))
}
