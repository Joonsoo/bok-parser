package com.giyeok.bokparser.tests

object JavaScriptTestCases {
	val tests = List(
		// Regular expressions/unicode/comments
		 """|var filter = /a/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|var filter = /about/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' �Ǵ� 'A' �� �ִ� ���ڿ� ��ΰ� TRUE (��ҹ��� ���� ����)
			|var filter = /a/i;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|/**********
			| *
			| * 'a' ���� 'z' ������ �ϳ��� ������ ��ΰ� TRUE (��ҹ��� ����)
			| /-\/-\ *********** **/
			|var filter = /[a-z]/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' �Ǵ� 'b' �Ǵ� 'c' �� �ִ� ���ڿ� ��ΰ� TRUE (��ҹ��� ����)
			|var filter = /a|b|c/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' ���� 'z' ���� �Ǵ� '0' ���� '9' ������ �ϳ��� ������ ��ΰ� TRUE (��ҹ��� ����)
			|var filter = /[a-z]|[0-9]/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' ���� 'z' ������ ���ڰ� �ƴ� ���ڰ� ���� ��� TRUE (��ҹ��� ����)
			|var filter = /[^a-z]/gim;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' ���� 'z' ������ ���ڷ� �����ϴ� ���ڿ��� �ܿ� TRUE (��ҹ��� ����)
			|var filter = /^[a-z]/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// 'a' ���� 'z' ������ ���ڷ� ������ ���ڿ��� �ܿ� TRUE (��ҹ��� ����)
			|var filter = /[a-z]$/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }
			|// '\' �� �ִ� ���ڿ��� �ܿ� TRUE (��ҹ��� ����)
			|var filter = /\\/;
			|if (filter.test("some test words") == true) { alert("ok"); } else { alert("fail"); }""".stripMargin('|')
	)
	
	val test = tests(0)
}
