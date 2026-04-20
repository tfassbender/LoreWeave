# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

Design and planning are **complete**. Implementation is **about to start** — phase 1 of `doc/implementation_plan.md`.

Authoritative documentation lives in `doc/`. Read the relevant ones before proposing changes:

- `doc/system_overview.md` — architecture and design philosophy
- `doc/open_api_spec.md` — REST contract
- `doc/tech_stack.md` — locked tech choices with rationale
- `doc/vault_schema.md` — frontmatter + body + validation rules
- `doc/implementation_plan.md` — phased roadmap with checkboxes. **Canonical source of truth for "done" and "next"** — tick items off in the same commit that completes them.

## Build, test, run

Gradle wrapper is committed — no system Gradle needed. JDK 21 is required on `PATH` (the Gradle toolchain pins Java 21).

- Dev mode (hot reload): `./gradlew quarkusDev` — serves on `http://localhost:4717` (configurable via `quarkus.http.port`).
- Tests: `./gradlew test` (Quarkus + RestAssured).
- Fast-jar build: `./gradlew build` — artifact at `build/quarkus-app/quarkus-run.jar`.
- Run fast-jar: `java -jar build/quarkus-app/quarkus-run.jar`.

Smoke endpoints available after boot:

- `GET /ping` — returns `{"status":"ok"}` (temporary, will be removed when the real REST surface lands).
- `GET /q/health` — SmallRye Health.
- `GET /q/openapi` — generated OpenAPI (YAML).

Machine-specific settings go in `application-local.properties` (git-ignored); see `src/main/resources/application.properties` for defaults.

## What LoreWeave Is

A REST API that turns a Git-backed Obsidian vault of markdown notes into a queryable knowledge graph, for consumption by AI agents (primarily Custom GPT Actions). The initial domain is narrative world-building (characters, events, locations, factions, rules), but the core engine is deliberately domain-agnostic — types are values of the `type:` field, not special cases in code.

## Obsidian-first (core design rule)

LoreWeave is a **thin query layer** over an Obsidian-managed vault — not a reimplementation of Obsidian. The rule: **use what Obsidian already provides; do not invent parallel mechanisms.**

- Obsidian gives every file a unique, stable handle (the vault-relative path) and keeps `[[wiki-links]]` consistent on rename/move via *Settings → Files & Links → Automatically update internal links* (on by default). We use the path as the internal handle — **no separate `id` field**.
- Tags, aliases, backlinks, graph view, search — Obsidian already has them. Where LoreWeave needs them, it reads them (tags, aliases) or derives them deterministically (backlinks). It doesn't add rival syntaxes.
- If during implementation you find we're about to build something Obsidian already solves, **flag it and propose dropping our version before writing the code**. The principle is more important than a cleaner REST contract.

What LoreWeave *does* add (because Obsidian exposes no API for it): the REST endpoints, git sync, server-side validation for headless agents. Those are new capabilities, not parallels.

## Tech Stack (summary)

Full detail in `doc/tech_stack.md`. In short:

- **Java 21** on **Quarkus 3.x** (JVM mode, fast-jar packaging — no native image in v1)
- **Gradle** (Kotlin DSL)
- Root group/package: `com.tfassbender.loreweave`
- **JGit** for all git operations — no system `git` dependency at runtime
- **commonmark-java** for markdown AST, **snakeyaml-engine** for YAML frontmatter
- **Picocli** (via `quarkus-picocli`) for a CLI entry point that reuses the parser/validator
- `application.properties` for config; machine-specific overrides in a git-ignored `application-local.properties`
- **JUnit 5** + **RestAssured** for tests
- **MIT** license

**Single Quarkus project, no module split.** Vault-specific behavior is pure configuration, so there's no generic/custom code separation to maintain. To serve a different vault (including a private one), run another instance with different properties — don't fork the code.

## Architecture (Big Picture)

Four layers, kept cleanly separated:

1. **Data source** — Obsidian vault in a Git repo. External to this codebase.
2. **Sync layer** — JGit-based clone-if-missing on boot, periodic `git pull` (configurable, default 5 min), plus manual `POST /sync`. Sync triggers a **full reload** of the in-memory index (no incremental indexing in v1).
3. **Core engine** — parses markdown + YAML, extracts `[[wiki-links]]` and inline `#hashtags`, builds a bidirectional graph, holds the result in memory.
4. **API layer** — thin REST surface over the graph. Five endpoints, bearer-token auth, structured JSON errors.

### Hard invariants — don't break these without a design discussion

- **Notes are the unit**: one `.md` file = one entity. The **vault-relative path** (normalized: forward slashes, case-insensitive, `.md` suffix optional) is the note's stable handle — Obsidian keeps `[[wiki-links]]` pointing at that path correct on rename/move. The `type:` frontmatter field classifies a note but is not part of the handle. There is **no** separate `id` field.
- **Backlinks are computed, not stored**: derived from forward links on every full rebuild. Any change to link extraction must be consistent across a rebuild.
- **In-memory only in v1**: no persistent cache, no database. A sync wipes and rebuilds.
- **Atomic index swap**: the index is held behind a `volatile` reference and swapped only after a successful rebuild. Readers always see a consistent snapshot. `/sync` is serialized via a single-permit semaphore.
- **Sync failure preserves availability**: if `git pull` fails, keep the current index, record the error for `/health`, return `500` with `SYNC_FAILED` to the caller. Do not wipe the served state on a transient failure.
- **Generic core**: the engine does not hardcode narrative-domain types. `character`, `event`, `faction`, etc. are values of `type:`, not special cases.
- **Tags are inline only**: extracted from `#hashtag` patterns in the body by walking the CommonMark AST (so `#` in code blocks, headings, URL fragments, and wiki-link heading fragments is naturally ignored). There is **no** YAML `tags:` field.
- **Folders carry no semantic meaning**: the vault can be organized however the user likes. `type` comes from frontmatter, never folder path.
- **AI-first responses**: structured JSON, size-limited, no raw file exposure without a metadata wrapper. Respect `doc/open_api_spec.md` §6 limits (max 10 search results, max 20 related nodes, default graph depth 2, max depth 5).

## API Surface

Full contract in `doc/open_api_spec.md`. Summary:

- `GET /search?q=&type=&limit=` — keyword search; weighted substring scoring (title > alias > tag > summary > content); returns summaries + score, no full content. Hits are identified by their `path`.
- `GET /note?path=` — full note including `links` and `backlinks`. `path` is the vault-relative path (normalized forward-slash, `.md` suffix optional).
- `GET /related?path=&depth=&limit=` — graph neighbors via BFS, seeded by `path`.
- `POST /sync` — git pull + rebuild; `500` / `SYNC_FAILED` on git failure
- `GET /health` — public (no auth); returns status, index stats, validation report summary, last-sync info

All endpoints except `/health` require `Authorization: Bearer <token>` (single static secret configured via `loreweave.auth.token`). Errors always follow `{ "error": { "code", "message", "details" } }`. Canonical codes: `UNAUTHORIZED`, `NOTE_NOT_FOUND`, `SYNC_FAILED`, `INVALID_REQUEST`.

OpenAPI is **generated from JAX-RS annotations** via `quarkus-smallrye-openapi` and served at `/q/openapi`. `doc/open_api_spec.md` is the human-readable mirror, updated alongside code changes.

## Vault Schema (summary)

Full rules and examples in `doc/vault_schema.md`.

| Frontmatter                      | Fields                               |
|----------------------------------|--------------------------------------|
| Required (hard error if missing) | `type`                               |
| Recommended (warning if missing) | `title`, `summary`, `schema_version` |
| Optional                         | `aliases`, `metadata`                |

- Body links: `[[wiki-style]]` only — markdown links and `![[embeds]]` are ignored. Fragments (`#heading`, `#^block`) and pipe-display (`|display`) are stripped before resolution.
- Link resolution order: **full path → basename** (case-insensitive, `.md` optional). Aliases declared in frontmatter are kept as metadata but are **not** used for link resolution. Wiki-links whose target has a known attachment extension (`.pdf`, `.png`, `.mp3`, …) are silently dropped at extraction time — they never reach the graph and never raise `unresolved_links`.
- Title fallback chain: `title:` → filename without `.md`.
- Tags: inline `#hashtag` only, extracted via AST walk, lowercased, deduped.

### Validation categories (all surfaced in `/health`)

Errors — note is excluded from the served index:

- `parse_errors`
- `missing_required_fields` (currently only `type`)
- `unresolved_links`

Warnings — note is served but incomplete:

- `missing_title`
- `missing_summary`
- `missing_schema_version`

## Configuration

Single `application.properties` with sensible defaults; machine-specific overrides in a git-ignored `application-local.properties` so paths and secrets don't leak into the public repo.

Finalized config keys:

- `loreweave.vault.remote` — git URL of the Obsidian vault repo
- `loreweave.vault.local-path` — where the vault is cloned locally (default: `./vault` relative to working dir)
- `loreweave.auth.token` — bearer token for API access
- `loreweave.sync.interval` — periodic pull interval (default `5m`)
- `loreweave.logging.path` — directory for rotating log files (default `./logs`; override to `/var/log/loreweave` on the server)

## Logging

INFO level and above by default, written to both console and a rotating file. The file path is `${loreweave.logging.path}/loreweave.log`, rotated at 10 MB with 10 backups kept. Use `org.jboss.logging.Logger` in new code to stay consistent with Quarkus's internal logging.

Profile overrides use Quarkus's per-profile config convention:

- `application-dev.properties` — active during `./gradlew quarkusDev`. Already set to `DEBUG` for both console and file so you get verbose output while iterating.
- `application-test.properties` — if we ever need test-profile tuning (currently unused; tests inherit `application.properties`).
- `application-prod.properties` — prod profile (the default when running the fast-jar). Currently unused; `application.properties` defaults suffice.

Per-package overrides at runtime via `quarkus.log.category."com.tfassbender.loreweave".level=…`.

## Ecosystem (planned, phases 8–10 of the plan)

- **CLI validator**: `lore-weave check <vault-path>` — Picocli-based, reuses the parser/validator, no git, no HTTP. Same JAR as the server, different entry point.
- **Obsidian templates**: frontmatter-prefilled `.md` templates under `examples/obsidian-templates/`.
- **Claude Code skill**: wraps the CLI so Claude Code can validate notes during authoring in a vault.

## Working in This Repo Right Now

- Changes to `doc/` are changes to the spec — treat them with the same care as API changes. If a doc edit would alter the API contract or any hard invariant above, call that out explicitly in the message.
- Before claiming work is done, tick the corresponding checkbox in `doc/implementation_plan.md`. If a piece of work doesn't fit an existing checkbox, decide first whether it belongs in the plan (add it) or is out of scope (say so).
- Until the phase-1 scaffold lands, there is **no build system**. If asked to "run the tests" or "build the project" before then, say so and ask which phase to tackle first.
- Vault-specific paths (like the user's local test vault) must never be committed — they live in `application-local.properties`.
