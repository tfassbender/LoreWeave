# LoreWeave – System Overview

## 1. Purpose
LoreWeave is a knowledge system designed to turn a structured Obsidian vault into a queryable, interconnected information graph. It enables AI-assisted exploration of notes, allowing external systems (e.g. Custom GPTs) to retrieve, navigate, and reason over structured content.

The system is designed primarily for narrative world-building (lore, stories, events, characters), but follows a modular architecture that can later be extended toward more general-purpose knowledge systems.

---

## 2. Core Concept
LoreWeave treats a collection of markdown files as a **graph-based knowledge system** rather than isolated documents.

Key principles:
- Each note represents a single entity (e.g. character, event, location, rule)
- Notes are addressed by their **vault-relative path** — the same handle Obsidian uses and keeps consistent across rename/move
- Relationships between notes are first-class citizens
- The system exposes structured access to content via a REST API
- **LoreWeave is a thin layer over Obsidian** — it uses what Obsidian already provides (path-based identity, auto-updated links, tags, aliases) rather than introducing parallel mechanisms

---

## 3. Architecture Overview
The system consists of four main layers:

### 3.1 Data Source
- Obsidian vault stored in a Git repository
- Markdown files with YAML frontmatter

### 3.2 Synchronization Layer
- Periodic Git pull to update content
- Manual sync endpoint to force refresh

### 3.3 Core Engine
- Markdown parsing
- YAML metadata extraction
- Link extraction and graph construction
- In-memory caching of processed notes

### 3.4 API Layer
- REST API exposing structured access to the knowledge graph
- Designed for consumption by AI systems via OpenAPI Actions

---

## 4. Data Model
Each note is represented internally as a structured entity:

- path (vault-relative, normalized — the stable lookup handle)
- title (string)
- type (string: character, event, location, faction, rule, etc.)
- content (markdown text)
- summary (optional but recommended)
- tags (list of strings)
- metadata (flexible key-value object)
- links (outgoing relationships, target is a `path`)
- backlinks (incoming relationships, source is a `path`)

### Note identity
- The vault-relative path is the note's stable handle — no separate `id` field.
- Normalization: lowercase, forward slashes, `.md` suffix optional.
- Obsidian ensures the path stays consistent across rename/move (*Settings → Files & Links → Automatically update internal links*). Using the path as the handle lets LoreWeave inherit that guarantee instead of building a parallel identity system.

---

## 5. Link System
LoreWeave builds a bidirectional graph from Obsidian-style links.

### Forward Links
Extracted from markdown:
- `[[characters/kael]]` (vault-relative path)
- `[[kael]]` (basename, resolved case-insensitively)
- `[[characters/kael|Kael]]` (pipe-display for prose)

Stored as structured references:
- target_path
- target_title
- type

### Backlinks
Automatically computed reverse relationships:
- Which notes reference this note

---

## 6. Core API Endpoints

### 6.1 Search Notes
`GET /search`
- Query-based search across notes
- Supports optional type filtering
- Returns summaries and metadata only

### 6.2 Get Note
`GET /note`
- Returns full structured note
- Includes content, metadata, links, and backlinks

### 6.3 Related Graph Query
`GET /related`
- Returns connected nodes (links + backlinks)
- Supports depth control
- Used for graph traversal

### 6.4 Sync Repository
`POST /sync`
- Triggers Git pull
- Rebuilds in-memory index

### 6.5 Health Check
`GET /health`
- System status
- Index validity and error reporting

---

## 7. Response Design Principles
- All responses use structured JSON
- No raw file exposure without metadata wrapper
- Responses are optimized for AI consumption
- Size-limited outputs for performance and context control

---

## 8. Validation Layer
A lightweight validation system ensures consistency:
- All `[[wiki-links]]` must resolve to a note in the served index
- Required frontmatter: `type` (the filesystem path already provides identity; no separate `id` field)
- Schema version should be present in each note (warning if missing)

Errors are returned in structured format for AI-friendly handling.

---

## 9. Schema Versioning
Each note includes:
- schema_version field

Purpose:
- Future compatibility
- Safe evolution of data model

---

## 10. Git Integration
- Vault stored in Git repository
- Periodic automatic pull (default 5 min; configurable via `loreweave.sync.interval`)
- Manual sync endpoint (`POST /sync`)
- **Full reload on every sync** — the index is rebuilt from scratch behind an atomic `volatile` swap. Incremental indexing is deferred to a later version.

---

## 11. Security Model
- Token-based authentication
- API protected via authorization header
- Designed for controlled external access (e.g. Custom GPT Actions)

---

## 12. Caching Strategy
- In-memory storage of parsed notes
- Full reload on sync
- No persistent cache layer in initial version

---

## 13. Summaries
- Notes should include a short summary field
- Used for search results and lightweight retrieval
- If missing, system may optionally generate summaries (future enhancement)

---

## 14. Response Control Mechanisms
To ensure efficient AI usage (numbers match [`open_api_spec.md`](open_api_spec.md) §6):
- Maximum `/search` results: 10
- Maximum `/related` neighbors: 20
- Default `/related` depth: 2; maximum: 5
- Full note content retrievable only via `/note?path=…` — search hits carry summaries only

---

## 15. Error Handling
All errors are returned in structured format:
- error code
- message
- optional context

Designed to be interpretable by AI systems for recovery and retry logic.

---

## 16. Future Considerations
The system is designed to allow later enhancements without architectural changes:

### 16.1 Embedding-Based Search (Future)
- Semantic search over notes
- Improved relevance beyond keyword matching
- Optional hybrid search model

### 16.2 Extended Graph Queries
- More advanced relationship traversal
- Potential graph query language

### 16.3 Domain Generalization
While initially designed for narrative systems, the architecture supports extension toward more general structured knowledge bases.

---

## 17. Design Philosophy
- Core system is generic and reusable
- Domain-specific logic is separated into a configurable layer
- Strong emphasis on structured data over unstructured text
- AI-first API design (optimized for LLM consumption)
