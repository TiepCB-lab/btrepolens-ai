# Database design

`tracked_repository` stores ID, canonical owner, name, full name, GitHub URL, active flag and creation/update timestamps. A case-insensitive unique index on full name prevents duplicates; a partial unique index allows at most one active row. `repository_selection_lock` contains one row and serializes concurrent activation transactions, including when no repository is active. There are no relationships between tracked repositories.

Flyway applies versioned SQL from `backend/src/main/resources/db/migration`; Hibernate validates the resulting schema and never updates it. GitHub issues, pull requests and metadata remain remote. Future chat sessions, messages, approval proposals and execution logs are **not implemented**; their schemas and relationships are deliberately deferred.
