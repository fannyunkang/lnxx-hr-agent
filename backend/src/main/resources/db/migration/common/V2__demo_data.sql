INSERT INTO employees VALUES
('E1001', '张伟', '研发部', 'Java 开发工程师', 'z***@lnxx.com', '138****1208'),
('E9001', '李娜', '人力资源部', 'HR 专员', 'l***@lnxx.com', '139****5026');

INSERT INTO attendance_summaries VALUES
('E1001', '2026-09', 22, 21, 1, 0),
('E9001', '2026-09', 22, 22, 0, 0);

INSERT INTO leave_balances VALUES
('E1001', 10.0, 3.0, 7.0),
('E9001', 12.0, 2.0, 10.0);

INSERT INTO approvals VALUES
('AP-20260901', 'E1001', 'LEAVE', '9月年假申请', 'APPROVING', CURRENT_TIMESTAMP),
('AP-20260818', 'E1001', 'OVERTIME', '8月加班申请', 'APPROVED', CURRENT_TIMESTAMP);

INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope) VALUES
('LEAVE-001', 1, '员工休假管理制度', '员工提交年假申请时，应至少提前一个工作日发起；连续三天及以上年假应提前三个工作日申请。', 'PUBLIC'),
('ATTENDANCE-001', 2, '考勤管理制度', '工作日标准上班时间为 09:00。每月迟到三次及以上时，由直属负责人进行考勤提醒。', 'PUBLIC'),
('RD-001', 1, '研发部弹性工作说明', '研发部员工可在 08:30 至 09:30 之间弹性到岗，并完成每日规定工时。', 'DEPARTMENT'),
('HR-001', 1, '人事审批操作规范', '涉及员工敏感信息的审批材料仅限获授权的人力资源岗位查看。', 'HR_ONLY');

INSERT INTO knowledge_departments VALUES ('RD-001', '研发部'), ('HR-001', '人力资源部');

