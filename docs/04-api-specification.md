# Phase 1 API

Base URL: `http://localhost:8080`. Responses use `{ "success": true, "data": ..., "message": "...", "errorCode": null, "timestamp": "..." }`. Errors use the same envelope with `success: false`, `data: null`, a sanitized message and error code. Statuses include 400 validation, 404 missing tracked or GitHub resource, 409 duplicate/conflict, 403 GitHub forbidden, 429 rate limit, 502 upstream failure and 504 network/timeout.

| Method | Path | Result |
|---|---|---|
| GET | `/api/v1/health` | Process liveness |
| GET | `/api/v1/repositories` | Registered repositories |
| POST | `/api/v1/repositories` | Register; returns 201 |
| PUT | `/api/v1/repositories/{id}/activate` | Select repository |
| GET | `/api/v1/repositories/{id}/overview` | Metadata, counts, recent items |
| GET | `/api/v1/repositories/{id}/issues` | Issue summaries |
| GET | `/api/v1/repositories/{id}/issues/{number}` | Issue details |
| GET | `/api/v1/repositories/{id}/pull-requests` | Pull request summaries |
| GET | `/api/v1/repositories/{id}/pull-requests/{number}` | Pull request details |

Register with `{"owner":"octocat","name":"Hello-World"}`. IDs and item numbers must be positive. List endpoints accept `state=open|closed|all` (default `open`) and `limit=1..100` (default 20), sorted by most recently updated. Issue lists exclude PRs and scan at most ten GitHub pages, so may contain fewer than the requested limit even if more issues exist. The overview returns up to five recent open items of each kind. `openIssues` and `openPullRequests` are GitHub Search's reported `total` and `incomplete` flag, not list lengths; search indexing can lag. GitHub may return 404 for inaccessible private resources.

Examples:

```sh
curl http://localhost:8080/api/v1/health
curl -X POST http://localhost:8080/api/v1/repositories -H 'Content-Type: application/json' -d '{"owner":"octocat","name":"Hello-World"}'
curl -X PUT http://localhost:8080/api/v1/repositories/1/activate
curl 'http://localhost:8080/api/v1/repositories/1/issues?state=open&limit=10'
curl http://localhost:8080/api/v1/repositories/1/overview
curl http://localhost:8080/api/v1/repositories/1/issues/1
curl 'http://localhost:8080/api/v1/repositories/1/pull-requests?state=all&limit=10'
curl http://localhost:8080/api/v1/repositories/1/pull-requests/1
```
