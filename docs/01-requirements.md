# Requirements

Developers need a unified view of unfinished GitHub work and, eventually, grounded assistance with priorities and planning. BTRepoLens AI aims to provide that context through a natural-language assistant. The sole MVP actor is a developer using a trusted local installation.

The MVP includes registering repositories, retrieving metadata, issues and pull requests, analyzing tasks, proposing plans and issues, explicit approval before writes, conversation history and tool execution logs. The assistant uses Spring AI tools constrained to the active repository. Issue proposals require explicit approval before creation.

Functional rules: multiple accessible repositories may be registered, at most one is active, registration verifies GitHub access and duplicate names are rejected. Reads are scoped to the explicitly requested registered repository. GitHub remains authoritative for remote data; the database stores only local selections. No fabricated GitHub data is returned.

Non-functional rules: credentials are external, errors are sanitized, calls are bounded and timed out, database changes use migrations, and HTTP, application, persistence and integration concerns remain separate. External text must be treated as untrusted when future AI features consume it. Future writes require persistent explicit approval and at-most-once execution. Remote use requires authentication and further hardening.
