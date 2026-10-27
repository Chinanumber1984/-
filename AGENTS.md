# SRM development

- Use the existing checkout. Cloud tasks are already isolated; do not create a worktree unless explicitly requested.
- Read README.md, docs/IMPLEMENTATION.md and docs/requirements before implementing business features.
- Architecture: Snowy 3.x, Java 17, Vue 3/AntDV4/Vite5, MyBatis-Plus, Sa-Token, Flowable7. Current local database is MySQL8.
- Preserve upstream LICENSE and copyright headers. Upstream provenance is recorded under docs/upstream.
- Keep org_id filtering server-side; portal supplier_id must come from a trusted session. Never accept identity scopes directly from request parameters.
- Business state changes require approvals; do not expose direct status updates. Sensitive data must not be logged or returned without field permissions.
- Do not claim the 114 tracked requirements are implemented: docs/backlog.csv records current status.
- Use scripts/mvn.sh for Maven (JDK, proxy and managed CA trust), scripts/init-local.sh for local dependencies.
- Never commit .runtime files or secret values. Do not reset/delete the Docker database volume during routine setup.
