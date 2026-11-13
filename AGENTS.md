# Project working instructions

## Skills

- For coding tasks, apply `ponytail` to keep the solution focused, understandable and maintainable. Complete validation, error handling and tests appropriate to the change; do not trade correctness or security for a smaller diff.
- For code exploration, debugging, impact analysis, refactoring or implementation, use the `graphify` skill when applicable. Use a current local graph when available, verify findings against the source, and do not assume graph results are exhaustive. If Graphify or its graph is unavailable, say so briefly and continue with source inspection and tools such as `rg`; do not block routine work. Keep secrets and private data out of graph outputs and external services.
- For frontend interface design, implementation, review or polish, apply `design-taste-frontend` (tasteskill) and `impeccable` when applicable. Follow their app-screen guidance for the product dashboard, favoring usability, accessibility, responsive behavior and coherent visual design. Verify the rendered interface when the environment permits. Honor the user's visual direction and the existing product design.
- Read each applicable skill's current `SKILL.md` and follow its workflow; do not assume that a skill is installed in every environment. If one is unavailable, note the limitation and proceed with the closest available workflow.

## Project boundaries

- Preserve the modular monolith and feature-oriented boundaries described in `docs/02-architecture.md` and `docs/06-frontend-architecture.md`.
- GitHub is the source of truth for repository data. GitHub access stays in the backend; never expose tokens or database credentials to the frontend or logs.
- Phase 1 GitHub operations are read-only. Implement later agent, chat or approval features only when requested; future writes require explicit human approval through a controlled workflow.
- Inspect existing code and local changes before editing. Keep changes scoped to the request, run relevant checks, and report any verification that could not be performed.
