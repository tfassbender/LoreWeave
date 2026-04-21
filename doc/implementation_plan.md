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
- [x] `TitleResolver` — frontmatter `title:` → filename without `.md`; returns resolved title plus a flag indicating whether a warning should be raised (the original ID-derived fallback was removed when the `id` field was dropped in favor of path-based identity)
- [x] ~~`IdValidator`~~ — removed. LoreWeave uses the vault-relative path as a note's handle; the filesystem guarantees uniqueness and Obsidian keeps `[[wiki-links]]` consistent across moves.
- [x] `NoteAssembler` — composes the above into a `ParseResult` (sealed: `Success(Note, warnings)` or `Failure(errors)`)
- [x] Unit tests for each parser with small fixture strings (40 unit tests across 6 classes; all green)

---

## Phase 3 — Vault scan + graph build + validation

**Exit criteria**: given a vault directory, we produce an in-memory `Index` with backlinks and a complete `ValidationReport`.

- [x] `VaultScanner` — recursive `.md` walk; skip `.git/`, `.obsidian/`, `.trash/`, any dot-prefixed directory (also handles IO read failures as `parse_errors`)
- [x] `LinkResolver` — resolution order: full path → basename (case-insensitive, `.md` optional); returns `Optional<String>` (normalized target key — the target note's vault-relative path). Tie-break on multiple matches: first-inserted wins; callers feed notes sorted by source path for determinism. Aliases are **not** consulted — they're metadata only. `WikiLinkExtractor` also drops links whose target ends in a known attachment extension (pdf/png/mp3/…) so such links neither enter the graph nor raise `unresolved_links`.
- [x] `IndexBuilder` — assembles `Map<String, IndexedNote>`, builds the resolver over served notes only so links to excluded notes surface as `unresolved_links`
- [x] Backlink computation pass — inverting resolved forward edges into `Backlink` lists attached to `IndexedNote` (not to `Note` itself, which stays a parseable-in-isolation record)
- [x] Validation — all categories populated into a `ValidationReport`:
  - [x] `parse_errors`
  - [x] `missing_required_fields`
  - [x] ~~`invalid_id_format`~~ — removed (no `id` field anymore; path is the handle).
  - [x] ~~`duplicate_ids`~~ — removed (the filesystem prevents duplicate paths).
  - [x] `unresolved_links`
  - [x] `missing_title` (warning)
  - [x] `missing_summary` (warning)
  - [x] `missing_schema_version` (warning)
- [x] `ValidationReport` caps sample paths at 5 per category, keeps full counts
- [x] `Index` — immutable snapshot holding `Map<String, IndexedNote>` and the report (plus `ResolvedLink` wrappers carrying both the raw link and its resolution outcome)
- [x] Notes with errors are excluded from the served index (but their incoming links surface as `unresolved_links`)
- [x] Fixture vaults under `src/test/resources/`: `vault-valid/` (4 notes: characters/kael, characters/rex, locations/karsis, factions/union) and `vault-invalid/` (one file per error/warning category)
- [x] Unit tests covering index build + every validation category (14 new tests across 3 classes; all green)

---

## Phase 4 — Git sync + scheduler

**Exit criteria**: the app clones a configured remote on first boot, pulls periodically, and swaps to a fresh index atomically. A pull failure does not clear the served index.

- [x] Config keys under `loreweave.vault.*` (`remote`, `local-path`) and `loreweave.sync.*` (`interval`) — bound via `@ConfigMapping(prefix = "loreweave")` in `config/LoreWeaveConfig`
- [x] `GitVaultClient` (JGit wrapper): `cloneIfMissing`, `pull` (fast-forward only; fall back to fetch + hard reset on divergent history). Also rejects populated non-git directories rather than silently cloning on top.
- [x] `SyncService` orchestrates: clone-if-missing → pull → scan → build → swap. Marked `@Startup` so the first sync runs at boot rather than on first HTTP hit.
- [x] `volatile Index` reference; atomic swap after successful build
- [x] Single-permit semaphore serializes `/sync` calls; scheduled ticks use `tryAcquire()` (non-blocking skip), manual calls use `tryAcquire(30s)` and throw `SyncInProgressException` on timeout for a phase-5 409 mapping
- [x] `@Scheduled` periodic pull at `loreweave.sync.interval` (default 5 min, 30 s initial delay to avoid racing the `@Startup` sync)
- [x] On pull failure: keep the current `Index`, record a structured last-sync error via `LastSync`, propagate `GitSyncException` upward; scheduled ticks swallow to keep the scheduler alive
- [x] `LastSync` state object: `{ ok, timestamp, updatedFiles, errorMessage? }` (+ `never()` sentinel for pre-first-sync state)
- [x] Integration tests: `GitVaultClientTest` builds a temp repo with JGit, clones, commits a second file in the source, asserts the pull fast-forwards and the new file appears; also covers non-git-dir rejection, invalid remote, and up-to-date no-op. `SyncServiceTest` covers failure-preserves-previous-index, empty-vault tolerance, and lastSync transitions — using a fake `GitVaultClient` rather than live git.
- [x] Also handles the "no remote, no local-path" case: serve an empty index, report `LastSync.ok`, don't crash (verified via real-boot smoke test).

---

## Phase 5 — REST API + auth + OpenAPI

**Exit criteria**: all five spec'd endpoints work behind the bearer-token filter, return the canonical error envelope, and produce a generated OpenAPI document.

- [x] `BearerTokenFilter` (`ContainerRequestFilter`, `@PreMatching`); skip for `/health` and `/q/*` paths. Uses `@ConfigProperty` rather than the full `LoreWeaveConfig` mapping because @PreMatching filters are instantiated before `@ConfigMapping` synthetic beans. Constant-time token compare.
- [x] `loreweave.auth.token` config key, read at startup (fail-closed when blank)
- [x] `ErrorMapper` producing `{ "error": { "code", "message", "details" } }`; covers `LoreWeaveException` subclasses, `GitSyncException`, `SyncInProgressException`, `WebApplicationException`, and anything uncaught
- [x] Error codes: `UNAUTHORIZED`, `NOTE_NOT_FOUND`, `SYNC_FAILED`, `INVALID_REQUEST`, plus two added for specific states: `SYNC_IN_PROGRESS` (409) and `INTERNAL_ERROR` (500). The latter two weren't in the plan's original list; flagged here because the API spec will need to list them too.
- [x] `GET /search?q&type&limit` — weighted substring scoring (title 10, alias 8, tag 6, summary 4, content 1), limit capped at 10. Ties broken by title asc, then by note key for determinism.
- [x] `GET /note?path` — full note including resolved `links` and `backlinks`; 404 when missing. `path` is the vault-relative path (case-insensitive, `.md` optional). Unresolved links are **not** included in the response — they appear only in `/health`'s validation report.
- [x] `GET /related?path&depth&limit` — BFS over resolved forward + backward edges, depth defaults to 2, max 5, limit capped at 20 (bumped from the plan's original max-2 at review time; vaults are small enough that five hops is cheap)
- [x] `POST /sync` — triggers `SyncService.syncNow()`; returns 500 `SYNC_FAILED` on git failure, 409 `SYNC_IN_PROGRESS` if another sync is in flight
- [x] `GET /health` — public; returns status, index stats, `ValidationReport` breakdown (errors + warnings, up to 5 sample paths each), and last-sync info
- [x] `@OpenAPIDefinition` metadata on `LoreWeaveApi` (plain annotation host — not an `Application` subclass, which interferes with CDI bean discovery) + `@Tag` / `@Operation` / `@Parameter` on each resource
- [x] Verified `/q/openapi` renders with all four endpoints and the bearer-token security scheme (smoke-tested on a booted fast-jar)
- [x] RestAssured integration tests: 19 new @QuarkusTest methods across 5 resource test classes, plus 11 plain JUnit tests for SearchService and RelatedService — 92 tests passing total
- [x] Removed the Phase-1 `/ping` smoke resource and its test (the real API has landed)

---

## Phase 6 — API spec alignment

**Exit criteria**: `doc/open_api_spec.md` matches what the generated OpenAPI says for endpoint shapes.

- [x] Update `doc/open_api_spec.md` §4.5 `/health` response to include the validation breakdown and `last_sync` object (full worked example)
- [x] Document all error codes in `doc/open_api_spec.md` §5 as a table — six codes (the plan's original four plus `SYNC_IN_PROGRESS` and `INTERNAL_ERROR` added during phase 5)
- [x] Add a worked example for `/related` at `depth=2`, including an explanation of dedup behavior when two paths converge on the same note
- [x] Cross-check generated OpenAPI vs the hand-written spec for endpoint shape parity; reconcile discrepancies
- [x] **Bonus fix during cross-check**: the generated `/q/openapi` schema was using camelCase field names while Jackson serialized snake_case at runtime, misleading clients importing the spec. Added `mp.openapi.extensions.smallrye.property-naming-strategy=…SnakeCaseStrategy` so the generated schema now mirrors the wire format.
- [x] Rewrote §1–§9 of `doc/open_api_spec.md` around the actual live responses (pulled from a booted fast-jar against the `vault-valid` fixture) rather than hand-sketched examples. Each endpoint section now reflects real output, including `display_text` on links/backlinks and the non-null field omission rule.

---

## Phase 7 — End-to-end tests

**Exit criteria**: a full-boot integration run hits all five endpoints against a fixture vault and passes.

- [x] Expanded `vault-valid/` fixture to 10 notes: characters/kael, rex, zara, tarek; locations/karsis; factions/union, inner-union; events/border-incident, karsis-siege; items/void-crystal. Updated the count + key-set assertions in `IndexBuilderTest`, `VaultScannerTest`, `RelatedServiceTest`, and `HealthResourceTest` to match.
- [x] `EndToEndJourneyTest` — `@QuarkusTest` walking health → search → note → related → sync → health in one ordered flow against the expanded fixture. Verifies the wiring as a single scenario, not just each endpoint in isolation.
- [x] `SyncFailureScenarioTest` — `@QuarkusTest` with `@TestProfile` overriding `loreweave.vault.remote` to a nonexistent file-URL. Asserts: app boots, `/health` reports `DEGRADED` + `last_sync.ok=false` with error message, and `POST /sync` returns 500 `SYNC_FAILED` with a clone-failed detail.
- [x] `SyncServiceConcurrencyTest` — plain JUnit using a latched fake `GitVaultClient`. Two threads call `syncNow()`; the second blocks on the semaphore while the first holds it. Asserts `maxInFlight == 1` (no interleaving), both syncs complete cleanly, and the index is consistent afterwards.
- [x] `ValidationErrorScenarioTest` — `@QuarkusTest` with `@TestProfile` pointing at `vault-invalid/`. Asserts all three remaining error categories (`parse_errors`, `missing_required_fields`, `unresolved_links`) and all three warning categories surface in `/health`, and that warning-only notes remain in the served index (`notes_count = 4`).
- [x] 104 tests passing overall (12 new phase-7 tests on top of phase-6's 92).

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

- [ ] `doc/authoring_guide.md` — conventions for writing notes: folder structure and naming (the path is the handle), when to link vs tag, summary style, type taxonomy suggestions
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
