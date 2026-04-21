# LoreWeave – Authoring Guide

A practical guide for writing notes that LoreWeave serves well. The strict rules live in [`vault_schema.md`](vault_schema.md); this document covers the *conventions* on top of those rules — the choices that make a vault easy to navigate, easy to link through, and pleasant for an AI agent to query.

## 1. The shape of a note

Every note is a markdown file with YAML frontmatter at the top. The minimum LoreWeave accepts is:

```markdown
---
type: character
---

Body goes here.
```

A note you actually want to live with looks more like this:

```markdown
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

Kael was stationed at [[locations/karsis-station]] when tensions rose. #pov
His loyalty to the [[outer-union|Union]] is unwavering... #arc/karsis
```

The `type` field is the only required one. `title`, `summary`, and `schema_version` are strongly recommended — LoreWeave serves notes without them but flags them in `/health`. `aliases` and `metadata` are optional free-form.

Don't put tags in frontmatter. Tags are inline `#hashtag` strings in the body — see §4.

## 2. Filenames and directories

### Filenames become the note's handle

Every note is addressed in the API by its **vault-relative path** — normalized to forward slashes, lowercased, with the `.md` suffix optional. A file at `characters/Kael Varyn.md` is addressable as `characters/kael varyn`, which has a space in the URL. Workable, but not lovely.

**Use kebab-case for filenames.** `kael-varyn.md`, `border-incident.md`, `outer-union.md`. Your paths stay readable in `/search` results, your wiki-links don't need quoting, and URL encoding doesn't bite you at the API layer.

### Subdirectories are for navigation, not classification

LoreWeave classifies notes by their `type:` frontmatter field, not by folder. `characters/kael-varyn.md` and `people/kael-varyn.md` are both valid `character` notes if their frontmatter says so.

That said, organizing by type *does* help humans. A common layout:

```
characters/
factions/
locations/
events/
items/
rules/
```

Pick whatever makes sense for your setting. Mixing organizing principles (some by type, some by arc) is also fine — whatever the author prefers.

### Ambiguity rule

If two files share a basename (`characters/kael.md` and `events/kael.md`, say), LoreWeave resolves `[[kael]]` to whichever was scanned first — deterministic but not the author's intent. Disambiguate by writing the full path: `[[events/kael]]`.

## 3. Links: the graph spine

LoreWeave's main value is the link graph. Every `[[wiki-link]]` in a note's body becomes an edge in the queryable graph, and `/related` walks them.

### How to write a link

Three forms, in increasing specificity:

- **Basename** — `[[kael-varyn]]`. Case-insensitive; resolves to any note whose filename (without `.md`) matches. Use this most of the time.
- **Full path** — `[[characters/kael-varyn]]`. Use when you need to disambiguate, or when the target lives in a subdirectory you want to emphasize for readers of the raw markdown.
- **Pipe display** — `[[kael-varyn|Kael]]`. The display text is what the reader sees; resolution still uses the left-hand side. Use freely for prose: `His loyalty to the [[outer-union|Union]] is unwavering` reads better than the bare path.

### What gets ignored

- **Embeds** — `![[image.png]]` is an Obsidian transclusion. LoreWeave doesn't index them.
- **Attachment links** — `[[diagram.png]]`, `[[deck.pdf]]`, `[[theme.mp3]]`. Known binary extensions are silently dropped — they don't appear in the graph and don't flag as `unresolved_links`. You can reference files freely without polluting the validation report.
- **Markdown-style links** — `[text](other.md)`. Not Obsidian's native form; LoreWeave ignores them entirely.
- **External URLs** — `[Obsidian](https://obsidian.md)` is fine in prose; LoreWeave ignores it.
- **Aliases** — a target that only matches an alias (and not a path or basename) does **not** resolve. Aliases are metadata, not routing. If you want something to be linkable, name the file that way or use a basename.

### Fragments

`[[karsis-station#Defenses]]` resolves to the note `karsis-station` — the `#Defenses` is stripped. LoreWeave doesn't address sub-note locations in v1. The fragment is kept on the extracted `Link` record (so a future endpoint *could* surface it), but it plays no role in resolution today.

### Unresolved links

If a link can't be resolved (no path match, no basename match, and the extension isn't a known attachment), it fires the `unresolved_links` validation error. The source note is still served — only the individual link is dropped from the graph — and the error surfaces in `/health`. Check `/health` after any batch of edits; a non-zero count is the signal that something is misspelled.

## 4. Tags: cross-cutting labels

Tags are inline in the body:

```markdown
Kael's loyalty to the Union is unwavering. #pov #arc/karsis
```

### Conventions

- **Flat tags** for obvious classifiers: `#pov`, `#major`, `#antagonist`, `#draft`.
- **Nested tags** (Obsidian-style) for hierarchies: `#arc/karsis`, `#faction/outer`, `#region/outer-rim`. The slash is a tree separator in Obsidian's graph view and in our validation — `#arc/karsis` is one tag, not two.
- Tags are **case-insensitive and deduplicated**. `#POV` and `#pov` in the same note count as one tag, stored lowercase.
- Tags made of **only digits** are dropped — `Fixes #123` in a PR link won't become a tag.

### Where tags show up

- In `/note` responses (on the note's `tags` array).
- In `/search` — a tag match contributes weight `6` to the relevance score.
- **Not** exposed as a first-class graph edge. If you want A → B to be traversable by `/related`, use a wiki-link, not a shared tag.

### Extracting rules

Tags are extracted by walking the CommonMark AST and scanning only text nodes. That means:

- `#` in a markdown heading (`# Title`) is *not* a tag.
- `#` inside an inline code span (`` `#note` ``) or fenced code block is *not* a tag.
- `#` inside a URL fragment (`https://example.com#section`) is *not* a tag.
- `#` inside the heading portion of a wiki-link (`[[karsis-station#Defenses]]`) is *not* a tag.

In practice, if you can see it as plain prose, it'll be picked up; if it's in formatting that browsers render differently, it won't.

## 5. Links vs tags — when to use which

A rough heuristic:

- **Use a link** when the note points at another specific entity. "Kael fought at [[karsis-siege]]" — karsis-siege is a named thing with its own note. The link encodes structure.
- **Use a tag** when you're classifying across a dimension that doesn't warrant its own note. `#draft`, `#pov`, `#arc/karsis`. No entity would have "arc/karsis" as a standalone page — it's a property of the notes.

If you find yourself wanting to navigate to a tag like it were a node, that's a signal that the tag should become a note. Create `arcs/karsis.md` (type `arc` or `meta`), link notes to it, and the tag becomes redundant.

## 6. Summaries: first impressions for the agent

The `summary` field is the most visible piece of a note outside `/note`. `/search` returns it with every hit; an agent uses it to decide whether to fetch the full content.

Write it like the first line of a book jacket:

- **One or two sentences.** Longer than that and the agent skims.
- **Concrete nouns and verbs.** "Outer Union scout, POV of the Karsis arc" is better than "An important character in the story."
- **Names that appear elsewhere.** If a reader's already asking about "Kael", a summary that says "Outer Union scout" connects two things they're holding in mind.
- **Don't repeat the title.** `title: Kael Varyn`, `summary: Kael Varyn is a character...` wastes space.

A missing summary fires a `missing_summary` warning. The note still loads, but `/search` returns it with an empty summary — which makes it nearly useless to rank.

## 7. Aliases: metadata, not routing

Aliases are alternate names you want the UI (Obsidian's internal search, graph hover text) to know about. Put them in frontmatter:

```yaml
aliases: [Kael, The Scout, Varyn]
```

LoreWeave **does not** use aliases for link resolution. `[[The Scout]]` does not resolve to kael-varyn.md unless there is *also* a file whose basename happens to be "The Scout". To make an alternate name linkable, either:

1. Rename the file to use that name (and Obsidian will rewrite existing links), or
2. Use pipe-display: `[[kael-varyn|The Scout]]`.

Aliases are kept on the note so the field stays meaningful for authors, but they don't feed the graph.

## 8. Type taxonomy

`type` is a free-form lowercase string. LoreWeave doesn't ship a closed list — `type: character` and `type: daemon` both parse. That said, some conventions stick:

**Starter types**, matching what the Obsidian templates provide:

- `character` — a person or agent (POV, antagonist, supporting).
- `event` — something that happened at a definite (or narratively definite) time.
- `location` — a place, from a room to a star system.
- `faction` — an organization, coalition, company, or church.
- `rule` — a cosmology/physics/magic principle or narrative convention.

**Extensions that have worked** in practice:

- `item` — for significant objects (a sword, a relic, a document). Distinct from a location.
- `arc` — for narrative arcs as their own notes, letting characters/events link into them.
- `meta` — for README-style vault-level notes that describe conventions.

**When to invent a new type**: when you want to filter `/search?type=…` on it, or when it feels distinct enough from existing types that an agent's reasoning improves from the category. Don't over-proliferate — five-to-seven types tends to be enough for most vaults.

A type change is just editing a frontmatter field. There is no registry to update.

## 9. Using the templates

Rather than hand-writing the frontmatter for every new note, install the Obsidian templates from [`examples/obsidian-templates/`](../examples/obsidian-templates/README.md). See that README for install steps. The templates pre-fill the required and recommended frontmatter so notes created from them pass validation immediately.

## 10. The validation feedback loop

Run the server against your vault and keep `/health` in a browser tab while authoring:

- `notes_count` — how many notes are served.
- `validation.errors` — per category, with up to 5 sample paths. Fixing these should be your first move on any vault edit.
- `validation.warnings` — per category. Usually lower priority; `missing_summary` in particular is easy to ignore but the most visible issue for consumers.
- `last_sync` — when the server last pulled + rebuilt. If it's stale, `POST /sync` to force a refresh.

A healthy vault reports `status: UP` with zero errors. `DEGRADED` means the index is still being served, but something needs attention.

For faster iteration without commit/push/sync, use the sibling [LoreWeaveWatcher](https://github.com/tfassbender/LoreWeaveWatcher) project — it points at the filesystem directly and refreshes within a second. See its README for install.

## 11. Common pitfalls

- **Forgot `type:`** — note is excluded from the served index entirely. Fires `missing_required_fields`.
- **Link to a file that no longer exists** — unresolved link, in the validation report. If you renamed or moved a file outside Obsidian (via git, terminal, or another editor), Obsidian's auto-update didn't run and other notes' links are stale.
- **Unquoted `{{placeholder}}` in frontmatter** — templates pre-quote these; if you hand-edit a template or write your own, keep the quotes or YAML parsing will fail.
- **Expecting aliases to make a note findable** — they don't. Use pipe-display at the link site or rename the file.
- **Dropped attachment confusion** — a wiki-link like `[[theme.mp3]]` is silently ignored. If you *want* it flagged, that's not possible today; it's by design so vaults can reference media without validation noise.
- **Basename collision** — two `[[kael]]` targets in different folders; the first-scanned wins. If that's not what you wanted, use full paths.
- **`type:` in a folder name but not frontmatter** — `characters/kael.md` without `type: character` in the file is excluded. The folder is a convention for humans, not LoreWeave.

## 12. Where to go next

- [Vault schema](vault_schema.md) — formal rules the parser enforces.
- [OpenAPI spec](open_api_spec.md) — what the API returns and how to query it.
- [System overview](system_overview.md) — the architecture this guide assumes.
- [Obsidian templates](../examples/obsidian-templates/README.md) — drop-in starters for each type.
