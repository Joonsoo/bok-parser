package com.giyeok.jparser.tests.gramgram

import com.giyeok.jparser.examples.GrammarWithExamples
import com.giyeok.jparser.tests.Viewer
import com.giyeok.jparser.tests.metalang.GrammarGrammarTests

object Visualization extends Viewer {
    val allTests: Set[GrammarWithExamples] = Set(
        GrammarGrammarTests.tests
    ).flatten

    def main(args: Array[String]): Unit = {
        start()
    }
}
