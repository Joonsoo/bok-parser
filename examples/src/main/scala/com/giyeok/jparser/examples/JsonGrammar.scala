package com.giyeok.jparser.examples

import com.giyeok.jparser.gramgram.MetaGrammar

object JsonGrammar {
    // https://tools.ietf.org/html/rfc8259
    // https://github.com/nst/JSONTestSuite/tree/master/test_parsing
    // http://seriot.ch/parsing_json.php
    // https://www.ecma-international.org/publications/files/ECMA-ST/Ecma-262.pdf
    // http://json.org/
    // https://ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf
    val fromJsonOrg: GrammarWithExamples = GrammarWithExamples(MetaGrammar.translateForce(
        "JSON from JSON.org",
        """json = ws element ws
          |element = value
          |value = object
          |    | array
          |    | string
          |    | number
          |    | "true"
          |    | "false"
          |    | "null"
          |object = '{' ws '}' | '{' ws members ws '}'
          |members = member | member ws ',' ws members
          |member = ws string ws ':' ws element
          |array = '[' ws ']' | '[' ws elements ws ']'
          |elements = element | element ws ',' ws elements
          |
          |string = '"' characters '"'
          |characters = # | character characters
          |character = {0-9a-zA-Z } | '\\' escape
          |escape = {"\\/bnrt} | 'u' hex hex hex hex
          |hex = digit | {A-Fa-f}
          |number = int frac exp
          |int = digit | onenine digits | '-' digit | '-' onenine digits
          |digits = digit | digit digits
          |digit = '0' | onenine
          |onenine = {1-9}
          |frac = # | '.' {0-9}+
          |exp = # | 'E' sign {0-9}+ | 'e' sign {0-9}+
          |sign = # | '+' | '-'
          |ws = # | {\u0009\u000a\u000d\u0020} ws
          |""".stripMargin))
        .example("""123""")
        .example("""{"abc":"def"}""")

}