package com.giyeok.jparser.metalang3a

import java.io.File

import com.giyeok.jparser.examples.metalang3.MetaLang3Grammar
import com.giyeok.jparser.metalang3a.MetaLanguage3.writeScalaParserCode

import scala.io.Source

object Grammars {
  private def generateScalaParserCode(name: String, grammarDef: String, examples: List[String] = null): Unit = {
    println(s"Generating $name...")
    writeScalaParserCode(grammarDef, name,
      "com.giyeok.jparser.metalang3a.generated",
      new File("./metalang/src/main/scala"),
      Option(examples))
    println(s"$name generated!")
  }

  def generateMetaLang3Ast(): Unit =
    generateScalaParserCode("MetaLang3Ast", MetaLang3Grammar.inMetaLang3.grammar,
      examples = List("A = B C 'd' 'e'*"))

  def readFile(path: String): String = {
    val file = new File(path)
    val source = Source.fromFile(file)
    try source.mkString finally source.close()
  }

  def generateMlProtoAst(): Unit = generateScalaParserCode("MlProto",
    readFile("./examples/src/main/resources/mlproto/mlproto.cdg"),
    examples = List("module Conv2d<>(inChannels: Int) {}"))


  def generateAutoDbAst(): Unit = generateScalaParserCode("AutoDbAst",
    readFile("./examples/src/main/resources/autodb/autodb.cdg"),
    examples = List(
      """database DartFriends {
        |  entity User(
        |    userId: Long as UserId,
        |    firebaseUid: String?,
        |    userDetail: Ref(UserDetail),
        |  ) key(userId), unique(loginName)
        |
        |  entity UserDetail(
        |    user: Ref(User),
        |    version: Long as UserDetailVersion,
        |    profileImage: String?,
        |    homeShop: Ref(Shop)?
        |  ) key(user)
        |
        |  entity Following(
        |    follower: Ref(User),
        |    following: Ref(User),
        |  ) key(follower, following)
        |}""".stripMargin))

  def generateArrayExprAst(): Unit = generateScalaParserCode("ArrayExprAst",
    """E:Expr = 'a' {Literal(value=$0)} | A
      |A = '[' WS E (WS ',' WS E)* WS ']' {Arr(elems=[$2]+$3)}
      |WS = ' '*
      |""".stripMargin,
    examples = List("[a]"))

  def generateLongestMatch(): Unit = generateScalaParserCode("LongestMatchAst",
    """S = Token*
      |Token = <Id | Num | WS>
      |Id = 'a-zA-Z'+
      |Num = '0-9'+
      |WS = ' \n\r\t'+
      |""".stripMargin
  )

  def generateExceptMatch(): Unit = generateScalaParserCode("ExceptMatchAst",
    """S = Token*
      |Token: Token = <Id | Kw | Num | WS>
      |Id = Name-Kw {Id(name=str($0))}
      |Kw = "if" {Keyword(typ:%Kw=%IF)} | "else" {Keyword(%ELSE)}
      |Name = 'a-zA-Z'+
      |Num = '0-9'+ {Number(value=str($0))}
      |WS = ' \n\r\t'+ {WS()}
      |""".stripMargin)

  def generateExpressionGrammar(): Unit = generateScalaParserCode("ExpressionGrammarAst",
    """Expression: Expression = Term
      |    | Expression WS '+' WS Term {BinOp(op=$2, lhs=$0, rhs=$4)}
      |Term: Term = Factor
      |    | Term WS '*' WS Factor {BinOp($2, $0, $4)}
      |Factor: Factor = Number
      |    | Variable
      |    | '(' WS Expression WS ')' {Paren(expr=$2)}
      |Number: Number = '0' {Integer(value="0")}
      |    | '1-9' '0-9'* {Integer(str($0) + str($1))}
      |Variable = <'A-Za-z'+> {Variable(name=$0)}
      |WS = ' '*
      |""".stripMargin)

  def main(args: Array[String]): Unit = {
    //    generateArrayExprAst()
    //    generateAutoDbAst()
    //    generateMetaLang3Ast()
    //    generateMlProtoAst()
    //    generateLongestMatch()
    //    generateExceptMatch()
    generateExpressionGrammar()
  }
}
