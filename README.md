# BTRepoLens AI

BTRepoLens AI is a local, single-developer GitHub project management assistant. Phase 1 provides repository registration and read-only GitHub access. AI planning, chat and approval-gated writes are future work.

Requires Java 25, Docker Compose and a GitHub fine-grained token with repository metadata, Issues and Pull requests read access. Copy `.env.example` to `.env`, set a strong local password and token, and start PostgreSQL with `docker compose up -d`. Compose reads `.env` automatically; Spring Boot does **not**. Export `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `GITHUB_TOKEN` into the shell before running `cd backend` and `./mvnw spring-boot:run` (Windows: `mvnw.cmd`). Run tests with `./mvnw test`.

See the [development guide](docs/05-development-guide.md) for complete PowerShell instructions, [API specification](docs/04-api-specification.md) for examples, and [frontend architecture](docs/06-frontend-architecture.md) for the agreed future stack and structure. The server and database bind to localhost; remote deployment requires authentication and additional security hardening.
