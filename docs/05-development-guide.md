# Development guide

Install JDK 25 and Docker Desktop with Compose. The Maven Wrapper supplies Maven. Create a fine-grained GitHub token limited to the repositories you need, with read-only Metadata, Issues and Pull requests permissions. Public repositories may also be accessed without a token, subject to GitHub's unauthenticated limits.

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env: set POSTGRES_PASSWORD and GITHUB_TOKEN. Never commit it.
docker compose up -d
docker compose ps
# Set the same values in the current shell; Compose does not export .env to Spring.
$env:DB_URL = 'jdbc:postgresql://localhost:55432/btrepolens'
$env:DB_USERNAME = 'btrepolens'
$env:DB_PASSWORD = '<the POSTGRES_PASSWORD from .env>'
$env:GITHUB_TOKEN = '<your token>'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Use `mvnw.cmd test` from `backend` to run tests. No AI key is needed: model auto-selection is disabled. `AI_API_KEY` and `AI_MODEL` are reserved for future configuration. The backend uses UTC internally, including for the JDBC connection. Compose persists PostgreSQL in a named volume; changing its initial credentials after the volume exists does not update the database user. `docker compose down` stops services without deleting data. Port 55432 is bound to localhost to reduce conflicts; change both Compose mapping and `DB_URL` if occupied. Set `SERVER_PORT` if 8080 is occupied.

Call endpoints using the examples in [API specification](04-api-specification.md), Postman, or `curl.exe` on Windows. `/api/v1/health` is liveness only. Actuator `/actuator/health/readiness` includes database health; `/actuator/health` also includes database health. Neither needs GitHub to be available.

For connection failures check Docker health, port, database name and exported credentials. A GitHub 401 means invalid credentials; 403 may mean insufficient permissions; 404 may hide an inaccessible private repository. Rate limits require waiting for reset. If Maven dependencies cannot be downloaded, check network/proxy configuration. GitHub responses can contain untrusted text: future AI features must not interpret it as instructions. The local-only service has no authentication and needs additional hardening before remote deployment.
