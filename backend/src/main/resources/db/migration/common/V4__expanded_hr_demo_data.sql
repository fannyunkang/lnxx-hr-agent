INSERT INTO employees(employee_id, name, department, position, email_masked, phone_masked)
SELECT 'E1002', '王敏', '研发部', '前端开发工程师', 'w***@lnxx.com', '136****8899'
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_id = 'E1002');

INSERT INTO attendance_summaries(employee_id, attendance_month, work_days, attended_days, late_count, absent_count)
SELECT 'E1002', '2026-09', 22, 20, 2, 0
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_summaries WHERE employee_id = 'E1002' AND attendance_month = '2026-09'
);

INSERT INTO leave_balances(employee_id, annual_total, annual_used, annual_remaining)
SELECT 'E1002', 8.0, 1.0, 7.0
WHERE NOT EXISTS (SELECT 1 FROM leave_balances WHERE employee_id = 'E1002');

INSERT INTO approvals(approval_id, employee_id, approval_type, title, status, updated_at)
SELECT 'AP-20260905', 'E1002', 'LEAVE', '9月调休申请', 'APPROVING', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM approvals WHERE approval_id = 'AP-20260905');
