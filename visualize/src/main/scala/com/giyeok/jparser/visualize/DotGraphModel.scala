package com.giyeok.jparser.visualize

import com.giyeok.jparser.visualize.DotGraphModel.{Edge, Node}

object DotGraphModel {

    trait Props[This <: Props[This]] {
        private val _properties = scala.collection.mutable.Map[String, String]()

        def attr(name: String, value: String): This = {
            _properties(name) = value
            this.asInstanceOf[This]
        }

        def addStyle(value: String): This = {
            val newStyle = _properties get "style" match {
                case None => value
                case Some(old) => old + "," + value
            }
            attr("style", newStyle)
        }

        def attrString: String = {
            _properties map { kv => kv._1 + "=\"" + kv._2 + "\"" } mkString ","
        }
    }

    case class Node(name: String)(val printName: String) extends Props[Node]

    case class Edge(start: Node, end: Node) extends Props[Edge]

}

class DotGraphModel(val nodes: Set[Node], val edges: Seq[Edge]) {
    def this() = this(Set(), Seq())

    val edgesByStartMap: Map[Node, Seq[Edge]] = edges groupBy { e => e.start }
    assert(edgesByStartMap.keySet subsetOf nodes)

    def addNode(node: Node): DotGraphModel =
        new DotGraphModel(nodes + node, edges)

    def addEdge(edge: Edge): DotGraphModel =
        new DotGraphModel(nodes + edge.start + edge.end, edges :+ edge)

    def removeNode(node: Node): DotGraphModel =
        new DotGraphModel(nodes - node, edges filterNot { e => e.start == node || e.end == node })

    def printDotGraph(startNode: Node): Unit = {
        println("digraph G {")
        println("    node[fontname=\"monospace\", height=.1];")

        def depthFirstTraverse(node: Node, visited: Set[Node]): Set[Node] = {
            // startSymbol이면 출력은 할 필요 없음
            def printLine(line: String): Unit = {
                println(line)
            }

            printLine(s"    ${node.printName}[${node.attrString}];")
            val edges = edgesByStartMap.getOrElse(node, Seq())
            var newVisited = visited + node
            edges foreach { edge =>
                // actual 표현
                printLine(s"    ${edge.start.name} -> ${edge.end.name}[${edge.attrString}];")

                if (!(newVisited contains edge.end)) {
                    newVisited = depthFirstTraverse(edge.end, newVisited)
                }
            }
            newVisited
        }

        val toVisit = nodes
        var visited = Set[Node]()
        visited ++= depthFirstTraverse(startNode, Set(startNode))
        while (toVisit != visited) {
            visited = depthFirstTraverse((toVisit -- visited).head, visited)
        }
        println("}")
    }
}
