INSERT INTO employees(employee_id, name, department, position, email_masked, phone_masked)
SELECT 'E9002', '赵强', '平台管理部', '系统管理员', 'z***@lnxx.com', '137****6601'
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_id = 'E9002');

INSERT INTO attendance_summaries(employee_id, attendance_month, work_days, attended_days, late_count, absent_count)
SELECT 'E9002', '2026-09', 22, 22, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_summaries WHERE employee_id = 'E9002' AND attendance_month = '2026-09'
);

INSERT INTO leave_balances(employee_id, annual_total, annual_used, annual_remaining)
SELECT 'E9002', 15.0, 4.0, 11.0
WHERE NOT EXISTS (SELECT 1 FROM leave_balances WHERE employee_id = 'E9002');

INSERT INTO approvals(approval_id, employee_id, approval_type, title, status, updated_at)
SELECT 'AP-20260907', 'E9002', 'SYSTEM_ACCESS', '知识库权限调整申请', 'APPROVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM approvals WHERE approval_id = 'AP-20260907');

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'ONBOARD-001', 1, '新员工入职流程', '新员工入职前由 HR 完成 offer 确认、合同材料收集和账号开通申请；入职当天由直属负责人完成岗位介绍和导师安排。', 'PUBLIC'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'ONBOARD-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'PROBATION-001', 1, '试用期转正规则', '试用期员工应在转正日前七个工作日发起转正评估，由直属负责人、部门负责人和 HR 共同完成评审。', 'PUBLIC'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'PROBATION-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'REIMBURSE-001', 1, '差旅报销规范', '员工提交差旅报销时需上传行程单、发票和审批单；单笔超过五千元的报销需部门负责人复核。', 'PUBLIC'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'REIMBURSE-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'TRAINING-001', 1, '培训学习制度', '公司鼓励员工参加岗位相关培训，培训费用需提前提交申请；通过审批后可按年度额度报销。', 'PUBLIC'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'TRAINING-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'SECURITY-001', 1, '信息安全与数据保密制度', '员工不得在外部渠道传播客户数据、员工个人信息、薪酬材料和未公开经营数据；发现泄露风险应及时向直属负责人和信息安全接口人报告。', 'PUBLIC'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'SECURITY-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'REMOTE-001', 1, '远程办公申请规则', '因特殊情况需要远程办公时，应提前向直属负责人提交申请并说明时间、原因和交付安排；连续远程超过三天需部门负责人审批。', 'DEPARTMENT'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'REMOTE-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'RD-QUALITY-001', 1, '研发代码评审规范', '研发部合并主干代码前应至少完成一次同级评审，涉及核心链路的变更需补充测试说明和回滚方案。', 'DEPARTMENT'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'RD-QUALITY-001' AND chunk_number = 1);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope)
SELECT 'HR-COMP-001', 1, '薪酬调整材料管理规范', '薪酬调整、绩效等级和特殊激励材料仅限 HR 与授权管理人员查看，普通员工不得查询他人薪酬或绩效明细。', 'HR_ONLY'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_chunks WHERE document_id = 'HR-COMP-001' AND chunk_number = 1);

INSERT INTO knowledge_departments(document_id, department)
SELECT 'REMOTE-001', '研发部'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_departments WHERE document_id = 'REMOTE-001' AND department = '研发部');

INSERT INTO knowledge_departments(document_id, department)
SELECT 'REMOTE-001', '人力资源部'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_departments WHERE document_id = 'REMOTE-001' AND department = '人力资源部');

INSERT INTO knowledge_departments(document_id, department)
SELECT 'RD-QUALITY-001', '研发部'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_departments WHERE document_id = 'RD-QUALITY-001' AND department = '研发部');

INSERT INTO knowledge_departments(document_id, department)
SELECT 'HR-COMP-001', '人力资源部'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_departments WHERE document_id = 'HR-COMP-001' AND department = '人力资源部');
