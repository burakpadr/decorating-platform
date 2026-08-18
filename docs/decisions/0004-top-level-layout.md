# 4. Five top-level directories, with the generated client as a sibling

Date: 2026-08-18
Status: accepted

## Context

The spec's layout put the applications under `apps/` and the generated client under `packages/`. A
flatter arrangement was requested: `docs`, `api`, `web-ui`.

That left two questions the request did not answer — where the deployment files go, and where the
generated client goes. Both were answered by trial:

- Deployment files were first flattened to the repo root. With compose, Caddyfile and `.env.example`
  loose among the tooling files, the root stopped being readable, so they moved into `infra/`.
- The client was first nested at `web-ui/api-client`. It worked, but it made the backend build write
  inside the frontend directory and put a pnpm workspace package inside another workspace package.

## Decision

```
api/  api-client/  web-ui/  docs/  infra/
```

`api-client` is a sibling of both applications, not a child of either: `api` produces it, `web-ui`
consumes it, so it belongs to neither.

Every remaining file at the root is one its tooling only finds there — `package.json`,
`pnpm-workspace.yaml` and `pnpm-lock.yaml` because that is where pnpm resolves the workspace;
`.editorconfig` because editors walk *up* from the file being edited, so nesting it would hide it from
`api/src`; `Makefile` because `make` starts from the working directory; `README.md` because that is
where GitHub renders it.

## Consequences

`api-client/**` appears in the path filters of *both* CI pipelines. That is deliberate — a contract
change must verify against both sides — and it is easy to get wrong: while the client was nested,
`web-ui/**` covered it and the explicit entry was removed as redundant. Hoisting it made the entry
necessary again.

Docker build contexts are the repo root, and the compose files sit one level down in `infra/`, so
their `context:` is `..`.

Deployment documentation moved to `docs/engineering/deployment.md` rather than living beside the
compose files, keeping `infra/` to things a machine reads.
