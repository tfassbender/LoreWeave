# LoreWeave – Vault Schema

This document is the authoritative reference for how to structure notes in a LoreWeave-compatible Obsidian vault. The parser, validator, and API all assume the rules below.

## Example note

````markdown
---
id: character_kael_varyn
type: character
title: Kael Varyn
summary: Outer Union scout, POV of the Karsis arc.
schema_version: 1
aliases: [Kael, The Scout]
metadata:
  faction: outer_union
  status: active
---

# Kael Varyn

Kael was stationed at [[Location - Karsis Station]] when tensions rose. #pov
His loyalty to the [[Faction - Outer Union|Union]] is unwavering, though he
has a personal grudge against [[Character - Rex Morrow]]. #major
````

## Frontmatter fields

### Required — hard error if missing

| Field | Type | Rule |
|---|---|---|
| `id` | string | Must match `^[a-z][a-z0-9]*_[a-z0-9_]+$`. Prefix must equal the `type` value. Unique across the vault. |
| `type` | string | Free-form lowercase string. No closed vocabulary — any value is accepted. |

### Recommended — warning in `/health` if missing

| Field | Type | Rule |
|---|---|---|
| `title` | string | Human-readable display name. Falls back to filename, then to an ID-derived title. |
| `summary` | string | One or two sentences. Returned in `/search` results — missing summaries degrade search UX. |
| `schema_version` | integer | Currently `1`. Defaults to `1` if missing; being explicit is preferred. |

### Optional — no warning if missing

| Field | Type | Rule |
|---|---|---|
| `aliases` | list of strings | Additional link targets for `[[wiki-links]]`. Not exposed in API responses. |
| `metadata` | object | Free-form key/value. Passthrough — the engine does not interpret it. |

### Other keys

Any other frontmatter keys are ignored (not an error). Use them freely for your own tooling.

## ID conventions

- Format: `{type}_{name}[_{identifier}]` — e.g. `character_kael_varyn`, `event_border_incident`, `location_karsis_station`.
- Lowercase letters, digits, and underscores only.
- Must begin with the `type` value followed by `_`.
- Duplicate IDs across files surface under `duplicate_ids` in `/health`.

## Body conventions

### Links (first-class graph edges)

Only `[[wiki-style]]` links are indexed:

- `[[Target]]` — resolved in order: explicit path → alias → basename (case-insensitive).
- `[[Target|display text]]` — display text ignored for resolution; target is what counts.
- `[[Target#heading]]` and `[[Target#^block]]` — the fragment is stripped, the note is resolved.

Ignored entirely (no edge produced):

- `![[embed]]` — transclusions are not resolved in v1.
- `[text](other.md)` — markdown-style links.
- External URLs (`https://…`).
- Attachment-style links to images, PDFs, etc.

### Tags

Tags are inline `#hashtags` that appear anywhere in the body:

- Pattern: `#[a-zA-Z0-9_/-]+`, preceded by whitespace or start-of-line.
- Obsidian-style nested tags (`#project/subtask`) are supported.
- Normalized to lowercase and deduplicated before indexing.
- The extractor walks the CommonMark AST and only scans text nodes, so `#` in headings (`# Title`), code blocks, URL fragments, and wiki-link heading fragments does **not** become a tag.

### Title resolution

Precedence:

1. YAML `title:` field
2. Filename without `.md`
3. ID-derived — drop the `type_` prefix, replace underscores with spaces, title-case. `character_kael_varyn` → `Kael Varyn`.

Steps 2 and 3 emit a `missing_title` warning in `/health`.

## File and folder rules

- All `.md` files are indexed recursively from the vault root.
- Ignored directories: `.git/`, `.obsidian/`, `.trash/`, and anything beginning with `.`.
- Ignored files: anything not `.md` (images, PDFs, attachments).
- **Folders carry no semantic meaning.** Structure your vault however feels natural in Obsidian — the `type` value comes from frontmatter, never the folder path.

## Schema versioning

- Every note should include `schema_version: 1`.
- Breaking schema changes bump the integer and ship a migration for older versions.
- A missing `schema_version` currently defaults to `1` with a warning; in a future v2 it will trigger a migration path.

## Validation categories

`/health` reports each category with a count and up to 5 sample file paths.

**Errors** (the note does not enter the served index):

- `parse_errors` — YAML parse failure or file read failure
- `missing_required_fields` — `id` or `type` absent
- `invalid_id_format` — ID regex mismatch, or type prefix doesn't match `type:`
- `duplicate_ids` — two or more files share an ID
- `unresolved_links` — a `[[target]]` doesn't match any file's path, alias, or basename

**Warnings** (the note loads and is served, but is incomplete):

- `missing_title`
- `missing_summary`
- `missing_schema_version`

A note with only warnings is fully usable via the API. A note with errors is **not** served — its ID is absent from the index, and any incoming links to it appear under `unresolved_links` for whoever linked to it.
