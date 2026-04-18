# LoreWeave – System Overview

## 1. Purpose
LoreWeave is a knowledge system designed to turn a structured Obsidian vault into a queryable, interconnected information graph. It enables AI-assisted exploration of notes, allowing external systems (e.g. Custom GPTs) to retrieve, navigate, and reason over structured content.

The system is designed primarily for narrative world-building (lore, stories, events, characters), but follows a modular architecture that can later be extended toward more general-purpose knowledge systems.

---

## 2. Core Concept
LoreWeave treats a collection of markdown files as a **graph-based knowledge system** rather than isolated documents.

Key principles:
- Each note represents a single entity (e.g. character, event, location, rule)
- Notes are uniquely identified via a strict ID system
- Relationships between notes are first-class citizens
- The system exposes structured access to content via a REST API

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

- id (string, unique)
- title (string)
- type (string: character, event, location, faction, rule, etc.)
- content (markdown text)
- summary (optional but recommended)
- tags (list of strings)
- metadata (flexible key-value object)
- links (outgoing relationships)
- backlinks (incoming relationships)

### ID System
- Strict, deterministic IDs
- Format: `type_name_identifier`
- Example: `character_kael_varyn`

---

## 5. Link System
LoreWeave builds a bidirectional graph from Obsidian-style links.

### Forward Links
Extracted from markdown:
- [[Character - Kael Varyn]]

Stored as structured references:
- target_id
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
- All links must resolve to valid IDs
- Required fields (id, type, content) must exist
- Schema version must be present in each note

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
- Periodic automatic pull
- Optional manual sync endpoint
- Designed for incremental updates rather than full reloads (future optimization possible)

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
To ensure efficient AI usage:
- Limit number of search results
- Limit related graph expansion depth
- Control maximum response payload size

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
