# LoreWeave – OpenAPI Specification

This document defines the REST API for LoreWeave, a structured knowledge graph system built on top of an Obsidian vault.

---

# 1. Overview

The LoreWeave API exposes a queryable interface over a graph-based knowledge system consisting of structured notes (characters, events, locations, factions, rules, etc.).

The API is designed for:
- AI agents (e.g., Custom GPT Actions)
- programmatic access
- graph traversal of interconnected knowledge

---

# 2. Authentication

All endpoints (except `/health`) require authentication.

## Scheme

- Type: HTTP Bearer Token
- Header:
```
Authorization: Bearer <token>
```

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
  "summary": "Scout of the Outer Union.",
  "tags": ["pov", "major"],
  "content": "# Kael Varyn\n...",
  "metadata": {
    "faction": "outer_union",
    "status": "active"
  },
  "links": [
    {
      "target_path": "events/border-incident",
      "title": "Border Incident",
      "type": "event"
    }
  ],
  "backlinks": [
    {
      "source_path": "events/border-incident",
      "title": "Border Incident",
      "type": "event"
    }
  ],
  "schema_version": 1
}
```

---

# 4. Endpoints

---

## 4.1 Search Notes

### `GET /search`

Searches notes using keyword matching and optional filtering.

### Query Parameters

- `q` (string, required)
- `type` (string, optional)
- `limit` (int, optional, default: 10, max: 10)

### Response

```json
{
  "results": [
    {
      "path": "events/border-incident",
      "title": "Border Incident",
      "type": "event",
      "summary": "Conflict between factions at the border region.",
      "tags": ["war", "border"],
      "score": 0.92
    }
  ]
}
```

---

## 4.2 Get Note

### `GET /note`

Returns full structured note including links and backlinks.

### Query Parameters

- `path` (string, required) — the vault-relative path, URL-encoded. `.md` suffix is optional; lookup is case-insensitive.

### Response

```json
{
  "note": {
    "path": "characters/kael",
    "title": "Kael Varyn",
    "type": "character",
    "summary": "Scout of the Outer Union.",
    "tags": ["pov"],
    "content": "# Kael Varyn\n...",
    "metadata": {
      "faction": "outer_union",
      "status": "active"
    },
    "links": [
      {
        "target_path": "events/border-incident",
        "title": "Border Incident",
        "type": "event"
      }
    ],
    "backlinks": [
      {
        "source_path": "events/border-incident",
        "title": "Border Incident",
        "type": "event"
      }
    ],
    "schema_version": 1
  }
}
```

---

## 4.3 Related Graph Query

### `GET /related`

Returns graph neighbors (links + backlinks).

### Query Parameters

- `path` (string, required)
- `depth` (int, optional, default: 1, max: 2)
- `limit` (int, optional, default: 10, max: 20)

### Response

```json
{
  "node": "characters/kael",
  "related": [
    {
      "path": "events/border-incident",
      "title": "Border Incident",
      "type": "event",
      "relation": "link"
    },
    {
      "path": "locations/karsis",
      "title": "Karsis Station",
      "type": "location",
      "relation": "link"
    }
  ]
}
```

---

## 4.4 Sync Repository

### `POST /sync`

Triggers Git pull and rebuilds in-memory index.

### Response

```json
{
  "status": "ok",
  "updated_files": 12,
  "timestamp": "2026-04-18T12:00:00Z"
}
```

---

## 4.5 Health Check

### `GET /health`

Public endpoint for system status.

### Response

```json
{
  "status": "healthy",
  "index_loaded": true,
  "notes_count": 128
}
```

---

# 5. Error Format

All errors follow a consistent structure:

```json
{
  "error": {
    "code": "NOTE_NOT_FOUND",
    "message": "No note found for path 'characters/ghost'",
    "details": {}
  }
}
```

Canonical codes: `UNAUTHORIZED`, `NOTE_NOT_FOUND`, `SYNC_FAILED`, `INVALID_REQUEST`.

---

# 6. System Constraints

- Maximum search results: 10
- Maximum related nodes: 20
- Default graph depth: 1
- Full note retrieval only via `/note`

---

# 7. Design Notes

- API is optimized for AI agent consumption.
- Graph structure is first-class (links + backlinks).
- Notes are addressed by their **vault-relative path** — the handle Obsidian itself uses and auto-updates on rename/move. No separate `id` field exists.
- Responses are structured JSON only.
- No raw file exposure without metadata wrapping.

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
