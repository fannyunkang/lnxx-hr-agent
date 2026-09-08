ALTER TABLE knowledge_chunks ADD COLUMN version INT DEFAULT 1 NOT NULL;
ALTER TABLE knowledge_chunks ADD COLUMN page_number INT;
ALTER TABLE knowledge_chunks ADD COLUMN source_type VARCHAR(32) DEFAULT 'TEXT' NOT NULL;
ALTER TABLE knowledge_chunks ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE knowledge_chunks ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;

CREATE TABLE knowledge_personal_access (
    document_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (document_id, employee_id)
);

CREATE TABLE knowledge_documents (
    document_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    title VARCHAR(256) NOT NULL,
    source_name VARCHAR(512) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active BOOLEAN DEFAULT FALSE NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (document_id, version)
);

CREATE TABLE knowledge_ingestion_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    chunk_count INT DEFAULT 0 NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE agent_traces ADD COLUMN request_id VARCHAR(64);
ALTER TABLE agent_traces ADD COLUMN runtime VARCHAR(32) DEFAULT 'python' NOT NULL;
ALTER TABLE agent_traces ADD COLUMN model VARCHAR(128);
ALTER TABLE agent_traces ADD COLUMN status VARCHAR(32) DEFAULT 'COMPLETED' NOT NULL;
ALTER TABLE agent_traces ADD COLUMN nodes_json TEXT;
ALTER TABLE agent_traces ADD COLUMN retries INT DEFAULT 0 NOT NULL;
ALTER TABLE agent_traces ADD COLUMN error_code VARCHAR(64);

CREATE INDEX idx_knowledge_job_document ON knowledge_ingestion_jobs(document_id, version);
CREATE INDEX idx_knowledge_active ON knowledge_chunks(document_id, version, active);
