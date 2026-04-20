# LoreWeave – Implementation Plan

A phased, checkbox-driven roadmap. Tick items as they're completed.

## How to work with this plan

- **Agile**: this is a starting point, not a contract. Revise freely as we learn. Add items, split items, re-order within a phase.
- **Definition of done** per item: code compiles, relevant tests pass, and any invariant documented in `CLAUDE.md` or `doc/*.md` is kept in sync.
- **Sub-items under a phase can be taken out of order** as long as the phase's exit criteria are met.
- **Cross-cutting concerns** (logging, error shapes, CDI wiring) get introduced incrementally in the phase that first needs them.

---

## Phase 1 — Project scaffold

**Exit criteria**: `./gradlew quarkusDev` boots and serves a trivial endpoint.

- [x] Create `LICENSE` (MIT, standard text) at repo root
- [x] Generate Gradle wrapper (`gradlew`, `gradlew.bat`, Gradle 8.10)
- [x] Create `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` — Quarkus 3.17.8, Java 21, group `com.tfassbender.loreweave`
- [x] Apply the Quarkus Gradle plugin; configure `quarkusBuild` fast-jar output
- [x] Add Quarkus extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-smallrye-openapi`, `quarkus-smallrye-health`, `quarkus-scheduler` (picocli deferred to phase 8 — it hijacks the main lifecycle via `@TopCommand`, which conflicts with the server runtime)
- [x] Add plain dependencies: JGit, commonmark-java, snakeyaml-engine
- [x] Add test dependencies: JUnit 5 (via `quarkus-junit5`), RestAssured, AssertJ
- [x] Create package skeleton under `com.tfassbender.loreweave`: `config`, `domain`, `parsing`, `graph`, `git`, `rest`, `cli`, `health` (each with a `package-info.java` so the empty dirs are committed)
- [x] Add `src/main/resources/application.properties` with default keys (`loreweave.vault.remote`, `loreweave.vault.local-path`, `loreweave.auth.token`, `loreweave.sync.interval`)
- [x] Add `application-local.properties` entry to `.gitignore` (also added `.gradle/`, `build/`, `vault/`)
- [x] Add a temporary `GET /ping` endpoint and smoke-test it (path changed from `/q/ping` — the `/q/*` namespace is reserved for Quarkus management endpoints like `/q/openapi` and `/q/health`)
- [x] Update `CLAUDE.md` with the real build/run commands now that they exist

---

## Phase 2 — Domain model + parsing

**Exit criteria**: given a single markdown file string, we can produce a parsed `Note` plus per-file warnings.

- [x] Define immutable records: `Note`, `Link`, `Backlink`, `ValidationIssue` (plus `ValidationCategory` enum; `Backlink` is defined but only populated in phase 3)
- [x] `FrontmatterParser` using snakeyaml-engine; defensive against malformed YAML; returns a typed frontmatter object + per-file errors
- [x] `MarkdownBodyParser` wrapping commonmark-java, exposing the AST
- [x] `WikiLinkExtractor` — walks text nodes, emits `[[target]]` tokens, strips `#heading` and `|display`, ignores `![[embed]]`
- [x] `HashtagExtractor` — walks text nodes, matches `(?<=^|\s)#[A-Za-z0-9_/-]+`, lowercases and dedups (also rejects purely numeric hashes so `#123` issue refs are not treated as tags)
- [x] `TitleResolver` — frontmatter `title:` → filename without `.md` → ID-derived; returns resolved title plus a flag indicating whether a warning should be raised
- [x] `IdValidator` — regex `^[a-z][a-z0-9]*_[a-z0-9_]+$`, verifies prefix matches `type:`
- [x] `NoteAssembler` — composes the above into a `ParseResult` (sealed: `Success(Note, warnings)` or `Failure(errors)`)
- [x] Unit tests for each parser with small fixture strings (40 unit tests across 6 classes; all green)

---

## Phase 3 — Vault scan + graph build + validation

**Exit criteria**: given a vault directory, we produce an in-memory `Index` with backlinks and a complete `ValidationReport`.

- [ ] `VaultScanner` — recursive `.md` walk; skip `.git/`, `.obsidian/`, `.trash/`, any dot-prefixed directory
- [ ] `LinkResolver` — resolution order: full path → alias → basename (case-insensitive); returns `Optional<String>` (target ID)
- [ ] `IndexBuilder` — assembles `Map<String, Note>`, builds auxiliary indexes (alias, basename)
- [ ] Backlink computation pass — for each forward link, append a `Backlink` to the target note
- [ ] Validation — all categories populated into a `ValidationReport`:
  - [ ] `parse_errors`
  - [ ] `missing_required_fields`
  - [ ] `invalid_id_format`
  - [ ] `duplicate_ids`
  - [ ] `unresolved_links`
  - [ ] `missing_title` (warning)
  - [ ] `missing_summary` (warning)
  - [ ] `missing_schema_version` (warning)
- [ ] `ValidationReport` caps sample paths at 5 per category, keeps full counts
- [ ] `Index` — immutable snapshot holding the note map, alias/basename indexes, and the report
- [ ] Notes with errors are excluded from the served index (but their incoming links surface as `unresolved_links`)
- [ ] Fixture vaults under `src/test/resources/`: `vault-valid/` (small clean vault) and `vault-invalid/` (exercises each category)
- [ ] Unit tests covering index build + every validation category

---

## Phase 4 — Git sync + scheduler

**Exit criteria**: the app clones a configured remote on first boot, pulls periodically, and swaps to a fresh index atomically. A pull failure does not clear the served index.

- [ ] Config keys under `loreweave.vault.*` (`remote`, `local-path`) and `loreweave.sync.*` (`interval`)
- [ ] `GitVaultClient` (JGit wrapper): `cloneIfMissing`, `pull` (fast-forward only; fall back to fetch + hard reset on divergent history)
- [ ] `SyncService` orchestrates: pull → scan → build → swap
- [ ] `volatile Index` reference; atomic swap after successful build
- [ ] Single-permit semaphore serializes `/sync` calls
- [ ] `@Scheduled` periodic pull at `loreweave.sync.interval` (default 5 min)
- [ ] On pull failure: keep the current `Index`, record a structured last-sync error for `/health`, propagate a typed exception upward
- [ ] `LastSync` state object: `{ ok, timestamp, updatedFiles, errorMessage? }`
- [ ] Integration tests: spin up a temp bare repo on disk, have the app clone and pull from it, assert the resulting index

---

## Phase 5 — REST API + auth + OpenAPI

**Exit criteria**: all five spec'd endpoints work behind the bearer-token filter, return the canonical error envelope, and produce a generated OpenAPI document.

- [ ] `BearerTokenFilter` (`ContainerRequestFilter`); skip for `/health` and `/q/*` dev-UI paths
- [ ] `loreweave.auth.token` config key, read at startup
- [ ] `ErrorMapper` producing `{ "error": { "code", "message", "details" } }`; covers uncaught exceptions and typed `LoreWeaveException` variants
- [ ] Error codes: `UNAUTHORIZED`, `NOTE_NOT_FOUND`, `SYNC_FAILED`, `INVALID_REQUEST`
- [ ] `GET /search?q&type&limit` — weighted substring scoring (title 10, alias 8, tag 6, summary 4, content 1), limit capped at 10
- [ ] `GET /note?id` — full note including `links` and `backlinks`; 404 when missing
- [ ] `GET /related?id&depth&limit` — BFS over forward + backward edges, depth defaults to 1, max 2, limit capped at 20
- [ ] `POST /sync` — triggers `SyncService`; returns 500 with `SYNC_FAILED` on git failure
- [ ] `GET /health` — public; returns status, index stats, `ValidationReport` summary, and last-sync info
- [ ] `@OpenAPIDefinition` metadata and annotations on each resource
- [ ] Verify `/q/openapi` renders correctly and is importable as a Custom GPT Action
- [ ] RestAssured integration tests for success and error paths of each endpoint

---

## Phase 6 — API spec alignment

**Exit criteria**: `doc/open_api_spec.md` matches what the generated OpenAPI says for endpoint shapes.

- [ ] Update `doc/open_api_spec.md` §4.5 `/health` response to include the validation breakdown and `last_sync` object
- [ ] Document all four error codes in `doc/open_api_spec.md` §5
- [ ] Add a worked example for `/related` at `depth=2`
- [ ] Cross-check generated OpenAPI vs the hand-written spec for endpoint shape parity; reconcile discrepancies

---

## Phase 7 — End-to-end tests

**Exit criteria**: a full-boot integration run hits all five endpoints against a fixture vault and passes.

- [ ] Expand `vault-valid/` fixture to ~10 notes with a representative link graph
- [ ] Full-boot integration test: start Quarkus in test mode, point at fixture, exercise each endpoint
- [ ] Sync-failure scenario: remote unreachable → 500 + last-sync error in `/health`; previous index still served
- [ ] Concurrent-sync scenario: two parallel `/sync` calls → serialized cleanly, no torn index
- [ ] Validation-error scenario: a `vault-invalid/` fixture load produces the expected `/health` payload

---

## Phase 8 — CLI validator

**Exit criteria**: `java -jar … check <vault-path>` produces a report and a sensible exit code without touching git or the server.

- [ ] Picocli `Check` command: `lore-weave check <vault-path>`
- [ ] Reuses parsing + validation from `parsing` and `graph` packages — no git, no HTTP, no scheduler
- [ ] Output format: human-readable summary, per-category samples, optional `--json` mode for tool integration
- [ ] Exit codes: `0` clean, `1` warnings only, `2` errors present
- [ ] Integration test against `vault-valid/` and `vault-invalid/`
- [ ] Document invocation in `doc/cli.md` and link from the README

---

## Phase 9 — Obsidian templates

**Exit criteria**: a fresh vault with the templates installed produces valid notes out of the box.

- [ ] `examples/obsidian-templates/character.md`
- [ ] `examples/obsidian-templates/event.md`
- [ ] `examples/obsidian-templates/location.md`
- [ ] `examples/obsidian-templates/faction.md`
- [ ] `examples/obsidian-templates/rule.md`
- [ ] `examples/obsidian-templates/README.md` explaining how to install (Obsidian Templates core plugin) and the placeholder convention

---

## Phase 10 — Claude Code skill

**Exit criteria**: a user copies the skill into `~/.claude/skills/` and Claude Code triggers CLI validation on request.

- [ ] `claude/skills/lore-weave-validate/SKILL.md` — trigger description + CLI invocation
- [ ] Document installation and a typical trigger phrase in `claude/skills/lore-weave-validate/README.md`
- [ ] Manual end-to-end check: the skill fires and returns structured output on the test vault

---

## Phase 11 — Documentation

**Exit criteria**: a new user can read the docs and set up a working vault + API deployment.

- [ ] `doc/authoring_guide.md` — conventions for writing notes: choosing IDs, when to link vs tag, summary style, type taxonomy suggestions
- [ ] `doc/deployment.md` — linux server setup, systemd unit, reverse proxy, TLS, secret management
- [ ] Update `doc/system_overview.md` if implementation revealed any invariant changes
- [ ] Verify all cross-references between docs are accurate

---

## Phase 12 — First deployment

**Exit criteria**: the API is reachable on the public subdomain, authenticates requests, and serves the private vault to a Custom GPT Action.

- [ ] Pick a public subdomain and set DNS
- [ ] systemd unit file template in `deploy/loreweave.service`
- [ ] Reverse proxy config (nginx or caddy) in `deploy/`
- [ ] Let's Encrypt TLS setup
- [ ] Token stored in `/etc/loreweave/application-local.properties`, `chmod 600`, not in repo
- [ ] First live pull of the private vault on the server
- [ ] Smoke-test the five endpoints via HTTPS with a real bearer token
- [ ] Connect the Custom GPT Action, run a handful of representative queries

---

## Future considerations (explicitly out of v1)

Not scoped now; tracked so we don't forget:

- Embedding / semantic search
- Metadata-based filters in `/search`
- Transclusion (`![[embed]]`) resolution
- Incremental indexing (currently full rebuild per sync)
- Multiple vaults per deployment
- External structured config file (YAML/JSON) for validation rules and type taxonomy
- GitHub Actions CI
- GraalVM native image build
- Token rotation, multiple tokens, per-token scopes
