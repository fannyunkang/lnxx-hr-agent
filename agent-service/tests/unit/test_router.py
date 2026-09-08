from hr_agent.graph.router import classify_intent


def test_routes_business_and_knowledge_intents():
    assert classify_intent("查询我的员工档案") == ("EMPLOYEE_PROFILE", "employee_profile")
    assert classify_intent("我还有多少年假？") == ("LEAVE_BALANCE", "leave_balance")
    assert classify_intent("研发部弹性上班规定") == ("KNOWLEDGE_SEARCH", "knowledge_search")


def test_unknown_question_uses_general_route():
    assert classify_intent("你好") == ("GENERAL", "")
