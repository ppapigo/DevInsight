CREATE TABLE project_repository (
 project_id BIGINT NOT NULL,
 repository_id BIGINT NOT NULL UNIQUE,
 PRIMARY KEY(project_id, repository_id),
 CONSTRAINT fk_project_repository_project FOREIGN KEY(project_id) REFERENCES project(id),
 CONSTRAINT fk_project_repository_repo FOREIGN KEY(repository_id) REFERENCES github_repository(id)
);

INSERT INTO project_repository(project_id, repository_id)
SELECT id, repository_id FROM project;
