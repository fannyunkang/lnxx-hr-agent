from hr_agent.graph.router import classify_intent, preclassify_intent


def test_routes_business_and_knowledge_intents():
    assert classify_intent("查询我的员工档案") == ("EMPLOYEE_PROFILE", "employee_profile")
    assert classify_intent("我还有多少年假？") == ("LEAVE_BALANCE", "leave_balance")
    assert classify_intent("研发部弹性上班规定") == ("KNOWLEDGE_SEARCH", "knowledge_search")


def test_unknown_question_uses_general_route():
    assert classify_intent("你好") == ("GENERAL", "")


def test_preclassifier_marks_employee_collection_as_high_risk_search():
    result = preclassify_intent("列出所有员工名单")

    assert result.intent == "HR_EMPLOYEE_SEARCH"
    assert result.selected_tool == "hr_search_employee"
    assert result.access_scope == "ALL_EMPLOYEES"
    assert result.risk_level == "HIGH"


def test_preclassifier_marks_department_collection():
    result = preclassify_intent("帮我查找研发部人员名单")

    assert result.intent == "HR_EMPLOYEE_SEARCH"
    assert result.access_scope == "DEPARTMENT_COLLECTION"
    assert result.target_hints["departments"] == ["研发部"]


def test_preclassifier_keeps_targeted_business_intent_sensitive():
    result = preclassify_intent("查一下 E1002 的年假")

    assert result.intent == "LEAVE_BALANCE"
    assert result.selected_tool == "leave_balance"
    assert result.access_scope == "OTHER_EMPLOYEE"
    assert result.risk_level == "MEDIUM"
    assert result.target_hints["employeeIds"] == ["E1002"]


def test_preclassifier_keeps_self_query_low_risk():
    result = preclassify_intent("我还有多少年假？")

    assert result.intent == "LEAVE_BALANCE"
    assert result.access_scope == "SELF_OR_UNSPECIFIED"
    assert result.risk_level == "LOW"
