package com.giyeok.jparser.metalang3a

import com.giyeok.jparser.NGrammar
import com.giyeok.jparser.examples.MetaLang3Example
import com.giyeok.jparser.examples.MetaLang3Example.CorrectExample
import com.giyeok.jparser.examples.metalang3.SimpleExamples
import com.giyeok.jparser.metalang2.generated.MetaGrammar3Ast
import com.giyeok.jparser.metalang3a.ValuefyExpr.UnrollChoices

object MetaLanguage3 {

    case class IllegalGrammar(msg: String) extends Exception(msg)

    def check(cond: Boolean, errorMessage: String): Unit = {
        if (!cond) throw IllegalGrammar(errorMessage)
    }

    def parseGrammar(grammar: String): MetaGrammar3Ast.Grammar = MetaGrammar3Ast.parseAst(grammar) match {
        case Left(value) => value
        case Right(value) => throw IllegalGrammar(value.msg)
    }

    case class ProcessedGrammar(ngrammar: NGrammar, startNonterminalName: String,
                                nonterminalTypes: Map[String, Type], nonterminalValuefyExprs: Map[String, UnrollChoices],
                                rawClassRelations: ClassRelationCollector, classParamTypes: Map[String, List[(String, Type)]],
                                shortenedEnumTypesMap: Map[Int, String], enumValuesMap: Map[String, Set[String]],
                                errors: CollectedErrors) {
        val classRelations: ClassRelationCollector = rawClassRelations.removeDuplicateEdges()
    }

    def analyzeGrammar(grammarDef: MetaGrammar3Ast.Grammar, grammarName: String): ProcessedGrammar = {
        val errorCollector = new ErrorCollector()
        val transformer = new GrammarTransformer(grammarDef, errorCollector)
        val grammar = transformer.grammar(grammarName)

        val inferredTypeCollector = new InferredTypeCollector(
            transformer.startNonterminalName(), transformer.classInfo, grammar.rules.keySet, transformer.nonterminalInfo)(errorCollector)

        var counter = 0
        while (errorCollector.isClear && inferredTypeCollector.tryInference()) {
            counter += 1
            if (counter > 5) {
                println(s"try inference for $counter times...")
            }
        }

        if (!inferredTypeCollector.isComplete) {
            errorCollector.addError("Incomplete type info")
        }

        inferredTypeCollector.typeRelations.classRelations.checkCycle(errorCollector)

        val classParamTypes = inferredTypeCollector.classParamTypes.map { pair =>
            val (className, paramTypes) = pair
            val paramNames = transformer.classInfo.classParamSpecs(className).params.map(_.name)
            className -> paramNames.zip(paramTypes)
        }.toMap

        val ngrammar = NGrammar.fromGrammar(grammar)

        val enumsMap = inferredTypeCollector.typeRelations.enumRelations.toUnspecifiedEnumMap(errorCollector)
        var enumValues = transformer.classInfo.canonicalEnumValues
        enumsMap.foreach(pair =>
            enumValues += (pair._2 -> (enumValues.getOrElse(pair._2, Set()) ++
                transformer.classInfo.shortenedEnumValues.getOrElse(pair._1, Set())))
        )

        ProcessedGrammar(ngrammar, transformer.startNonterminalName(),
            inferredTypeCollector.nonterminalTypes, transformer.nonterminalValuefyExprs,
            inferredTypeCollector.typeRelations.classRelations, classParamTypes,
            enumsMap, enumValues,
            errorCollector.collectedErrors
        )
    }

    def analyzeGrammar(grammarDefinition: String, grammarName: String = "GeneratedGrammar"): ProcessedGrammar =
        analyzeGrammar(parseGrammar(grammarDefinition), grammarName)

    def main(args: Array[String]): Unit = {
        def testExample(example: MetaLang3Example): Unit = {
            println(example.grammar)
            val analysis = analyzeGrammar(example.grammar, example.name)
            val valuefyExprSimulator = new ValuefyExprSimulator(analysis.ngrammar, analysis.startNonterminalName, analysis.nonterminalValuefyExprs, analysis.shortenedEnumTypesMap)
            val analysisPrinter = new AnalysisPrinter(valuefyExprSimulator.startValuefyExpr, analysis.nonterminalValuefyExprs)

            analysis.nonterminalTypes.foreach { p =>
                println(s"Nonterm `${p._1}` = ${Type.readableNameOf(p._2)}")
            }
            analysisPrinter.printClassHierarchy(analysis.rawClassRelations.toHierarchy)
            val classHierarchy = analysis.classRelations.toHierarchy
            analysisPrinter.printClassHierarchy(classHierarchy)
            println(s"Enum Values: ${analysis.enumValuesMap}")
            println(s"Shortened enums: ${analysis.shortenedEnumTypesMap}")
            analysis.classParamTypes.foreach(pair =>
                // TODO supers
                analysisPrinter.printClassDef(classHierarchy, pair._1, pair._2)
            )
            analysisPrinter.printValuefyStructure()

            if (!analysis.errors.isClear) {
                throw IllegalGrammar(s"Errors: ${analysis.errors.errors}")
            }
            example.correctExamplesWithResults.foreach { example =>
                val CorrectExample(input, expectedResult) = example
                val parsed = valuefyExprSimulator.parse(input).left.get
                println(s"== Input: $input")
                analysisPrinter.printNodeStructure(parsed)
                val valuefied = valuefyExprSimulator.valuefy(parsed)
                println(valuefied.prettyPrint())
                expectedResult.foreach(someExpectedResult =>
                    check(valuefied.prettyPrint() == someExpectedResult, s"Valuefy result mismatch, actual=${valuefied.prettyPrint()}, expected=$someExpectedResult"))
            }
        }

        // testExample(MetaLang3Grammar.inMetaLang3)
        testExample(SimpleExamples.ex8)
    }
}
