CREATE TABLE employees (
    employee_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    department VARCHAR(64) NOT NULL,
    position VARCHAR(128) NOT NULL,
    email_masked VARCHAR(128) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL
);

CREATE TABLE attendance_summaries (
    employee_id VARCHAR(32) NOT NULL,
    attendance_month VARCHAR(7) NOT NULL,
    work_days INT NOT NULL,
    attended_days INT NOT NULL,
    late_count INT NOT NULL,
    absent_count INT NOT NULL,
    PRIMARY KEY (employee_id, attendance_month)
);

CREATE TABLE leave_balances (
    employee_id VARCHAR(32) PRIMARY KEY,
    annual_total DECIMAL(6,1) NOT NULL,
    annual_used DECIMAL(6,1) NOT NULL,
    annual_remaining DECIMAL(6,1) NOT NULL
);

CREATE TABLE approvals (
    approval_id VARCHAR(32) PRIMARY KEY,
    employee_id VARCHAR(32) NOT NULL,
    approval_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    chunk_number INT NOT NULL,
    title VARCHAR(256) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (document_id, chunk_number)
);

CREATE TABLE knowledge_departments (
    document_id VARCHAR(64) NOT NULL,
    department VARCHAR(64) NOT NULL,
    PRIMARY KEY (document_id, department)
);

CREATE TABLE agent_traces (
    trace_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    intent VARCHAR(64) NOT NULL,
    tools VARCHAR(1000) NOT NULL,
    citations VARCHAR(2000) NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_approval_employee ON approvals(employee_id, updated_at);
CREATE INDEX idx_trace_username ON agent_traces(username, created_at);
