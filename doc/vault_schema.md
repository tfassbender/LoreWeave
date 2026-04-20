# LoreWeave – Vault Schema

This document is the authoritative reference for how to structure notes in a LoreWeave-compatible Obsidian vault. The parser, validator, and API all assume the rules below.

## Note identity — the vault-relative path

Every `.md` file in the vault is one entity. Its **stable handle is its vault-relative path**, normalized to forward slashes, lowercased, with the `.md` suffix optional. `characters/Kael.md` is addressable as `characters/kael`, `characters/kael.md`, `Characters/Kael`, etc.

There is **no** `id` frontmatter field. Obsidian already provides a unique handle (the filesystem path) and keeps `[[wiki-links]]` consistent on rename/move via *Settings → Files & Links → Automatically update internal links*. Introducing a separate `id` would duplicate that machinery and force authors to choose IDs for every note.

## Example note

````markdown
---
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

Kael was stationed at [[locations/karsis]] when tensions rose. #pov
His loyalty to the [[union|Union]] is unwavering, though he
has a personal grudge against [[rex|Rex Morrow]]. #major
````

## Frontmatter fields

### Required — hard error if missing

| Field | Type | Rule |
|---|---|---|
| `type` | string | Free-form lowercase string. No closed vocabulary — any value is accepted. Used as a filter in `/search?type=…` and exposed on every note response. |

### Recommended — warning in `/health` if missing

| Field | Type | Rule |
|---|---|---|
| `title` | string | Human-readable display name. Falls back to the filename (without `.md`). |
| `summary` | string | One or two sentences. Returned in `/search` results — missing summaries degrade search UX. |
| `schema_version` | integer | Currently `1`. Defaults to `1` if missing; being explicit is preferred. |

### Optional — no warning if missing

| Field | Type | Rule |
|---|---|---|
| `aliases` | list of strings | Alternate names for the entity. Free-form metadata — **not** used for link resolution; not exposed in API responses. |
| `metadata` | object | Free-form key/value. Passthrough — the engine does not interpret it. |

### Other keys

Any other frontmatter keys are ignored (not an error). Use them freely for your own tooling.

## Body conventions

### Links (first-class graph edges)

Only note-to-note `[[wiki-style]]` links are indexed:

- `[[Target]]` — resolved in order: explicit vault-relative path → basename (case-insensitive, `.md` suffix optional).
- `[[Target|display text]]` — display text ignored for resolution; the target is what counts.
- `[[Target#heading]]` and `[[Target#^block]]` — the fragment is stripped and the link is treated as pointing to the note itself (LoreWeave does not address sub-note locations).
- When a basename collides (two notes share a filename in different folders), the first one in vault-scan order wins. Disambiguate by writing the full path.

Ignored entirely (no edge produced, no validation error):

- `![[embed]]` — transclusions are not resolved in v1.
- `[text](other.md)` — markdown-style links.
- External URLs (`https://…`).
- `[[attachment.pdf]]`, `[[image.png]]`, and any other link whose target ends in a known attachment extension (pdf, png/jpg/gif/svg/webp/…, mp3/wav/…, mp4/mov/…, zip/tar/…, canvas). The vault may reference these, but LoreWeave neither indexes them nor flags them as broken links.
- Aliases declared in frontmatter. They remain as metadata but are not consulted during link resolution — use a basename or vault-relative path instead, typically with a pipe-display for prose: `[[union|the Union]]`.

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

Step 2 emits a `missing_title` warning in `/health`.

## File and folder rules

- All `.md` files are indexed recursively from the vault root.
- Ignored directories: `.git/`, `.obsidian/`, `.trash/`, and anything beginning with `.`.
- Ignored files: anything not `.md` (images, PDFs, attachments).
- **Folders carry no semantic meaning for classification.** `type` comes from frontmatter, not folder path. Folders *are* however the only disambiguation lever when two notes share a basename — put colliding files in different folders and reference them by full path.

## Schema versioning

- Every note should include `schema_version: 1`.
- Breaking schema changes bump the integer and ship a migration for older versions.
- A missing `schema_version` currently defaults to `1` with a warning; in a future v2 it will trigger a migration path.

## Validation categories

`/health` reports each category with a count and up to 5 sample file paths.

**Errors** (the note does not enter the served index):

- `parse_errors` — YAML parse failure or file read failure
- `missing_required_fields` — `type` absent
- `unresolved_links` — a `[[target]]` doesn't match any served note's path or basename

**Warnings** (the note loads and is served, but is incomplete):

- `missing_title`
- `missing_summary`
- `missing_schema_version`

A note with only warnings is fully usable via the API. A note with errors is **not** served — it's absent from the index, and any incoming links to it appear under `unresolved_links` for whoever linked to it.
