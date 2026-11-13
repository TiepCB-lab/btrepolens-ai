# Development guide

Install JDK 25 and Docker Desktop with Compose. The Maven Wrapper supplies Maven. Create a fine-grained GitHub token limited to the repositories you need, with read-only Metadata, Issues and Pull requests permissions. Public repositories may also be accessed without a token, subject to GitHub's unauthenticated limits.

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env: set POSTGRES_PASSWORD and GITHUB_TOKEN. Never commit it.
docker compose up -d
docker compose ps
# Set the same values in the current shell; Compose does not export .env to Spring.
$env:DB_URL = 'jdbc:postgresql://localhost:15432/btrepolens'
$env:DB_USERNAME = 'btrepolens'
$env:DB_PASSWORD = '<the POSTGRES_PASSWORD from .env>'
$env:GITHUB_TOKEN = '<your token>'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Use `mvnw.cmd test` from `backend` to run tests. AI is disabled by default. For OpenAI, set `AI_CHAT_MODEL=openai`, `AI_API_KEY`, and `AI_MODEL`. For Gemini, set `SPRING_PROFILES_ACTIVE=gemini`, `GEMINI_API_KEY`, and optionally `GEMINI_MODEL` (default `gemini-3.5-flash-lite`); Google’s OpenAI-compatible endpoint is used. The Windows backend script loads these settings from `.env`. Select models that support tool calling. The agent sends chat and retrieved GitHub content to the provider; check the provider's data policies and limits before using private data. For approved issue creation, the configured GitHub token also needs Issues write permission; reading alone only needs read permissions. The backend uses UTC internally, including for the JDBC connection. Compose persists PostgreSQL in a named volume; changing its initial credentials after the volume exists does not update the database user. `docker compose down` stops services without deleting data. Port 15432 is bound to localhost to reduce conflicts; change both Compose mapping and `DB_URL` if occupied. Existing `.env` files using port 55432 need their `DB_URL` updated. Set `SERVER_PORT` if 8080 is occupied.

The approval and chat integration tests are opt-in and modify data: set `MVP_TEST_DB_URL` to a **dedicated disposable PostgreSQL database whose name ends in `_test`**, plus `MVP_TEST_DB_USERNAME` and `MVP_TEST_DB_PASSWORD`, then run `mvnw.cmd test`. Never point these variables at a development or production database. Mocked AI and GitHub calls are used. Stop any existing backend instance before starting a version that applies new Flyway migrations.

Call endpoints using the examples in [API specification](04-api-specification.md), Postman, or `curl.exe` on Windows. `/api/v1/health` is liveness only. Actuator `/actuator/health/readiness` includes database health; `/actuator/health` also includes database health. Neither needs GitHub to be available.

For connection failures check Docker health, port, database name and exported credentials. A GitHub 401 means invalid credentials; 403 may mean insufficient permissions; 404 may hide an inaccessible private repository. Rate limits require waiting for reset. If Maven dependencies cannot be downloaded, check network/proxy configuration. GitHub responses are untrusted input and never authorize writes. If a proposal remains `EXECUTING` or becomes `UNKNOWN`, inspect GitHub manually; the system does not retry. Chat turn locks are released when their database connection ends; a superseded response is not saved and the user should check history before retrying. The local-only service has no authentication and needs additional hardening before remote deployment.
