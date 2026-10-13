CREATE TABLE developer_profile (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, login VARCHAR(100) NOT NULL UNIQUE,
 name VARCHAR(255) NOT NULL, bio TEXT, avatar_url VARCHAR(1000), html_url VARCHAR(1000),
 public_repos INT NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE github_repository (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, github_id BIGINT NOT NULL UNIQUE,
 full_name VARCHAR(300) NOT NULL UNIQUE, owner_login VARCHAR(100) NOT NULL,
 description TEXT, html_url VARCHAR(1000) NOT NULL, default_branch VARCHAR(255) NOT NULL,
 language VARCHAR(100), stars INT NOT NULL, fork BOOLEAN NOT NULL,
 pushed_at TIMESTAMP(6), created_at TIMESTAMP(6), languages_json TEXT,
 INDEX idx_repository_owner(owner_login)
);
CREATE TABLE project (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, repository_id BIGINT NOT NULL UNIQUE,
 title VARCHAR(255) NOT NULL, description TEXT, published BOOLEAN NOT NULL DEFAULT FALSE,
 featured BOOLEAN NOT NULL DEFAULT FALSE, demo_url VARCHAR(1000), latest_analysis_id BIGINT,
 document_revision BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_project_repo FOREIGN KEY(repository_id) REFERENCES github_repository(id),
 INDEX idx_project_published(published, featured)
);
CREATE TABLE project_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
 original_name VARCHAR(255) NOT NULL, storage_key VARCHAR(100) NOT NULL UNIQUE,
 content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL, extracted_text LONGTEXT,
 public_approved BOOLEAN NOT NULL DEFAULT FALSE, sha256 VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_document_project FOREIGN KEY(project_id) REFERENCES project(id),
 INDEX idx_document_project(project_id, public_approved)
);
CREATE TABLE analysis_job (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, active_project_id BIGINT UNIQUE, force_run BOOLEAN NOT NULL,
 requested_at TIMESTAMP(6) NOT NULL, started_at TIMESTAMP(6), finished_at TIMESTAMP(6),
 error_code VARCHAR(80), message VARCHAR(1000), commit_sha VARCHAR(64),
 CONSTRAINT fk_job_project FOREIGN KEY(project_id) REFERENCES project(id),
 INDEX idx_job_queue(status, requested_at)
);
CREATE TABLE project_analysis (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL, commit_sha VARCHAR(64) NOT NULL,
 model VARCHAR(100) NOT NULL, document_revision BIGINT NOT NULL, result_json LONGTEXT NOT NULL,
 activity_json LONGTEXT NOT NULL, input_tokens INT NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_analysis_project FOREIGN KEY(project_id) REFERENCES project(id),
 INDEX idx_analysis_project(project_id, created_at)
);
ALTER TABLE project ADD CONSTRAINT fk_project_latest_analysis FOREIGN KEY(latest_analysis_id) REFERENCES project_analysis(id);
CREATE TABLE analysis_evidence (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, analysis_id BIGINT NOT NULL, source_key VARCHAR(100) NOT NULL,
 kind VARCHAR(30) NOT NULL, path VARCHAR(1000) NOT NULL, url VARCHAR(1500) NOT NULL,
 excerpt LONGTEXT NOT NULL, document_id BIGINT,
 CONSTRAINT fk_evidence_analysis FOREIGN KEY(analysis_id) REFERENCES project_analysis(id),
 CONSTRAINT fk_evidence_document FOREIGN KEY(document_id) REFERENCES project_document(id),
 UNIQUE(analysis_id, source_key)
);
CREATE TABLE interview_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, question VARCHAR(1000) NOT NULL, answer_json LONGTEXT NOT NULL,
 model VARCHAR(100) NOT NULL, created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE ai_daily_usage (
 usage_day DATE PRIMARY KEY, request_count INT NOT NULL
);
