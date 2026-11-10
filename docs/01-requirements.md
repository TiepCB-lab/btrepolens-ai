# Requirements

Developers need a unified view of unfinished GitHub work and, eventually, grounded assistance with priorities and planning. BTRepoLens AI aims to provide that context through a natural-language assistant. The sole MVP actor is a developer using a trusted local installation.

The complete MVP includes registering repositories, retrieving metadata, issues and pull requests, analyzing tasks, proposing plans and issues, explicit approval before writes, conversation history and tool execution logs. Phase 1 implements registration, activation, live read-only repository information, issue and pull request retrieval, health and local persistence. AI, chat, approval and GitHub writes are not implemented.

Functional rules: multiple accessible repositories may be registered, at most one is active, registration verifies GitHub access and duplicate names are rejected. Reads are scoped to the explicitly requested registered repository. GitHub remains authoritative for remote data; the database stores only local selections. No fabricated GitHub data is returned.

Non-functional rules: credentials are external, errors are sanitized, calls are bounded and timed out, database changes use migrations, and HTTP, application, persistence and integration concerns remain separate. External text must be treated as untrusted when future AI features consume it. Future writes require persistent explicit approval and at-most-once execution. Remote use requires authentication and further hardening.
