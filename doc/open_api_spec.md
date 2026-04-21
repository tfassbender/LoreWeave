# LoreWeave – OpenAPI Specification

This document defines the REST API for LoreWeave, a structured knowledge graph system built on top of an Obsidian vault. It is a **human-readable mirror** of the machine-generated spec served at `GET /q/openapi`. When the two disagree, the generated spec wins — this file is updated whenever endpoint shapes change.

---

# 1. Overview

The LoreWeave API exposes a queryable interface over a graph-based knowledge system consisting of structured notes (characters, events, locations, factions, rules, etc.).

The API is designed for:
- AI agents (e.g., Custom GPT Actions)
- programmatic access
- graph traversal of interconnected knowledge

All JSON uses `snake_case` field names and ISO-8601 timestamps; null fields are omitted.

---

# 2. Authentication

All endpoints except `GET /health` require a bearer token.

## Scheme

- Type: HTTP Bearer Token
- Header: `Authorization: Bearer <token>`
- The token is a single static shared secret configured on the server via `loreweave.auth.token`.

Requests without a valid token get a `401` / `UNAUTHORIZED` response; see §5.

---

# 3. Core Data Model

## Note handle

Every note is addressed by its **`path`** — the vault-relative path, normalized to forward slashes, lowercased, with the `.md` suffix optional. Example: `characters/kael`. This is the same handle Obsidian uses internally; renaming or moving a note inside Obsidian updates `[[wiki-links]]` automatically (*Settings → Files & Links → Automatically update internal links*), so paths stay consistent across edits. There is no separate `id` field.

## Note (base entity)

```json
{
  "path": "characters/kael",
  "title": "Kael Varyn",
  "type": "character",
  "summary": "Outer Union scout, POV of the Karsis arc.",
  "tags": ["pov", "major"],
  "content": "Kael was stationed at [[locations/karsis]] ...",
  "metadata": {
    "faction": "outer_union"
  },
  "links": [
    {
      "target_path": "locations/karsis",
      "title": "Karsis Station",
      "type": "location"
    },
    {
      "target_path": "factions/union",
      "title": "The Outer Union",
      "type": "faction",
      "display_text": "Union"
    }
  ],
  "backlinks": [
    {
      "source_path": "characters/rex",
      "title": "Rex Morrow",
      "type": "character",
      "display_text": "Kael"
    }
  ],
  "schema_version": 1
}
```

Notes:
- `content` is the raw markdown body (without the frontmatter block).
- `metadata` is free-form passthrough from the note's frontmatter.
- `links` only contains **resolved** forward links. Unresolved `[[…]]` references do not appear here — they surface in `/health` under `validation.errors.unresolved_links`.
- `display_text` on a link/backlink is the pipe-display portion of the wiki-link (e.g. `[[union|Union]]` → `"display_text": "Union"`). Omitted when the wiki-link didn't use pipe-display.
- `backlinks` are computed on every index rebuild; the `display_text` is the pipe-display used at the linking site.

---

# 4. Endpoints

---

## 4.1 Search Notes

### `GET /search`

Weighted substring search with optional `type` filter. Scoring uses per-field boolean contribution (case-insensitive "contains"):

| Field | Weight |
|-------|-------:|
| `title` | 10 |
| `aliases` | 8 |
| `tags` | 6 |
| `summary` | 4 |
| `body` | 1 |

The raw sum is normalized to `[0.0, 1.0]` by dividing by the maximum possible sum (29). Ties break by title ascending, then by note `path`.

### Query Parameters

- `q` (string, required) — query text.
- `type` (string, optional) — restrict to a single `type:` value.
- `limit` (int, optional, default: 10, max: 10) — values above 10 are clamped.

### Response

```json
{
  "results": [
    {
      "path": "characters/kael",
      "title": "Kael Varyn",
      "type": "character",
      "summary": "Outer Union scout, POV of the Karsis arc.",
      "tags": ["pov", "major"],
      "score": 0.655
    },
    {
      "path": "characters/rex",
      "title": "Rex Morrow",
      "type": "character",
      "summary": "Inner Union enforcer, antagonist of the Karsis arc.",
      "tags": ["antagonist"],
      "score": 0.034
    }
  ]
}
```

Each hit contains summary fields only — fetch the full note (with `content`, `metadata`, `links`, `backlinks`) via `GET /note?path=<hit.path>`.

---

## 4.2 Get Note

### `GET /note`

Returns a full structured note including links and backlinks.

### Query Parameters

- `path` (string, required) — vault-relative path, URL-encoded. Case-insensitive; `.md` suffix optional.

### Response

```json
{
  "note": {
    "path": "characters/kael",
    "title": "Kael Varyn",
    "type": "character",
    "summary": "Outer Union scout, POV of the Karsis arc.",
    "tags": ["pov", "major"],
    "content": "Kael was stationed at [[locations/karsis]] when tensions rose. #pov\n...",
    "metadata": {
      "faction": "outer_union"
    },
    "links": [
      {
        "target_path": "locations/karsis",
        "title": "Karsis Station",
        "type": "location"
      },
      {
        "target_path": "factions/union",
        "title": "The Outer Union",
        "type": "faction",
        "display_text": "Union"
      }
    ],
    "backlinks": [
      {
        "source_path": "characters/rex",
        "title": "Rex Morrow",
        "type": "character",
        "display_text": "Kael"
      }
    ],
    "schema_version": 1
  }
}
```

### Errors
- `404 NOTE_NOT_FOUND` — no note served for the given path.
- `400 INVALID_REQUEST` — `path` missing or blank.

---

## 4.3 Related Graph Query

### `GET /related`

Breadth-first traversal over resolved forward and backward edges, starting at the seed note. Unresolved links are not traversed.

### Query Parameters

- `path` (string, required) — seed note path.
- `depth` (int, optional, default: 2, max: 5) — values above 5 are clamped.
- `limit` (int, optional, default: 10, max: 20) — values above 20 are clamped.

### Response — worked example at `depth=2`

Given a seed note `characters/kael` that links to `locations/karsis`, `factions/union`, and `characters/rex`, and where `characters/rex` in turn links to `factions/union`:

```
GET /related?path=characters/kael&depth=2&limit=10
```

```json
{
  "node": "characters/kael",
  "related": [
    {
      "path": "locations/karsis",
      "title": "Karsis Station",
      "type": "location",
      "relation": "link"
    },
    {
      "path": "factions/union",
      "title": "The Outer Union",
      "type": "faction",
      "relation": "link"
    },
    {
      "path": "characters/rex",
      "title": "Rex Morrow",
      "type": "character",
      "relation": "link"
    }
  ]
}
```

Depth-2 would also have visited neighbors of the depth-1 nodes, but in this example `rex → union` and `kael → union` fold into the same already-visited node — the BFS dedupes so each note appears at most once in the result. The `node` field echoes the normalized seed path. `relation` is `"link"` for outgoing edges and `"backlink"` for incoming edges.

### Errors
- `404 NOTE_NOT_FOUND` — seed `path` is not in the served index.
- `400 INVALID_REQUEST` — `path` missing or blank.

---

## 4.4 Sync Repository

### `POST /sync`

Triggers a git pull and rebuilds the in-memory index. Serialized via a single-permit semaphore.

### Request body

None.

### Response

```json
{
  "status": "ok",
  "updated_files": 3,
  "timestamp": "2026-04-21T14:34:55.160Z"
}
```

- `updated_files` is the number of files changed compared to the previous HEAD, or `0` if the repo was already up to date (or no remote is configured).
- `timestamp` is when the sync finished.

### Errors
- `500 SYNC_FAILED` — git clone or pull failed. The previously served index is retained (availability is preserved over freshness).
- `409 SYNC_IN_PROGRESS` — another sync is already running; the caller should retry after a short back-off.

---

## 4.5 Health Check

### `GET /health`

Public. No authentication. Returns liveness, current index size, the per-category validation breakdown, and the most recent sync outcome.

### Response

```json
{
  "status": "UP",
  "index_loaded": true,
  "notes_count": 4,
  "validation": {
    "errors": {
      "parse_errors": { "count": 0, "samples": [] },
      "missing_required_fields": { "count": 0, "samples": [] },
      "unresolved_links": { "count": 0, "samples": [] }
    },
    "warnings": {
      "missing_title": { "count": 0, "samples": [] },
      "missing_summary": { "count": 0, "samples": [] },
      "missing_schema_version": { "count": 0, "samples": [] }
    }
  },
  "last_sync": {
    "ok": true,
    "timestamp": "2026-04-21T14:34:55.160Z",
    "updated_files": 0
  }
}
```

- `status` is `"UP"` when the last sync succeeded AND the validation report has zero errors; `"DEGRADED"` otherwise. The server is still serving — `DEGRADED` just signals that something needs attention.
- `validation.errors` and `validation.warnings` list each category with a count and up to 5 sample source-file paths. Categories with zero count are still emitted (empty-samples arrays) so clients can probe without conditional checks.
- `last_sync.error` (string) is present only when the last sync failed; on success it is omitted (per the `non-null` Jackson policy).

---

# 5. Error Format

All errors share this envelope:

```json
{
  "error": {
    "code": "NOTE_NOT_FOUND",
    "message": "No note found for path 'characters/ghost'",
    "details": { "path": "characters/ghost" }
  }
}
```

`details` is always present — may be an empty object when no structured payload applies.

## Error codes

| HTTP | `code` | When |
|-----:|--------|------|
| 400 | `INVALID_REQUEST` | A required query parameter is missing, blank, or out of range. |
| 401 | `UNAUTHORIZED` | Missing `Authorization` header, bad format, or wrong token. |
| 404 | `NOTE_NOT_FOUND` | `/note` or `/related` received a `path` that does not match any served note. |
| 409 | `SYNC_IN_PROGRESS` | `/sync` could not acquire the sync permit within 30 s — another sync is running. |
| 500 | `SYNC_FAILED` | Git clone or pull failed. The previously served index is retained. |
| 500 | `INTERNAL_ERROR` | Uncaught exception — indicates a bug; see server logs. |

---

# 6. System Constraints

- Maximum search results: 10
- Maximum related nodes: 20
- Default graph depth: 2
- Maximum graph depth: 5
- Full note retrieval only via `/note`
- `/sync` is single-permit; concurrent requests get `409 SYNC_IN_PROGRESS`.

---

# 7. Design Notes

- API optimized for AI agent consumption (compact JSON, bounded size, predictable error shape).
- Graph structure is first-class (links + backlinks).
- Notes are addressed by their **vault-relative path** — the handle Obsidian itself uses and auto-updates on rename/move. No separate `id` field exists.
- Responses are structured JSON only; no raw file exposure without a metadata wrapper.
- The generated OpenAPI at `/q/openapi` is the machine-readable source of truth. This document mirrors it for humans.

---

# 8. Future Extensions

Planned but not required for initial version:

- Embedding-based semantic search
- Advanced graph query language
- Incremental indexing improvements
- Multi-domain schema support

---

# 9. Summary

The LoreWeave API provides a structured interface to a Git-backed Obsidian knowledge graph. It enables search, retrieval, and traversal of interconnected notes, optimized for AI-driven exploration and reasoning.
