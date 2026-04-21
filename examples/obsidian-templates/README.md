# LoreWeave — Obsidian Templates

Frontmatter-prefilled templates for writing notes against the LoreWeave schema.
Drop these into your vault's templates folder, enable Obsidian's core
**Templates** plugin, and notes you create from them pass LoreWeave validation
out of the box.

## What's here

| File | Purpose |
|------|---------|
| [`character.md`](character.md) | People, agents, POV characters |
| [`event.md`](event.md)         | Historical incidents, battles, rituals — pre-fills today's date |
| [`location.md`](location.md)   | Stations, worlds, landmarks |
| [`faction.md`](faction.md)     | Organizations, coalitions, churches |
| [`rule.md`](rule.md)           | Cosmology / physics / magic rules and conventions |

Each template includes LoreWeave's required `type` field plus the recommended
`title`, `summary`, and `schema_version` — so a note created from one will
clear `missing_required_fields` and will only carry a `missing_summary`
warning until the author fills in the summary.

## Installation

1. Copy this `obsidian-templates/` folder into your Obsidian vault. Two reasonable placements:
   - **`.templates/`** at the vault root — leading dot keeps it out of LoreWeave's scan (which skips dot-prefixed directories) and out of Obsidian's tree view by default. *Recommended.*
   - **`Templates/`** — more conventional name, but LoreWeave will try to parse the template files themselves and report warnings on their placeholders. Works, but noisier on `/health`.
2. Enable the core **Templates** plugin: *Settings → Core plugins → Templates → Enable*.
3. Set the template folder: *Settings → Templates → Template folder location* — point it at wherever you placed the files.
4. *(Optional)* Bind a hotkey: *Settings → Hotkeys → "Templates: Insert template"*.

## Using a template

1. Create a new note. Give it the name you want the entity to have — use kebab-case so paths stay readable (e.g. `kael-varyn.md`, not `Kael Varyn.md`).
2. Run **Templates: Insert template** from the command palette or hotkey.
3. Pick the type. The frontmatter is filled in, `{{title}}` becomes your filename, and any date placeholders expand to today.
4. Fill in `summary`, any `metadata` values you care about, and write the body.

## Placeholders

Obsidian's core Templates plugin expands these on insertion:

- `{{title}}` — the filename of the current note, without `.md`. Used as the note's display title (aligns with LoreWeave's `title: → filename` fallback chain).
- `{{date}}` or `{{date:FORMAT}}` — current date. `event.md` pre-fills `metadata.date` with `{{date:YYYY-MM-DD}}`.
- `{{time}}` or `{{time:FORMAT}}` — current time. Not used in these templates but available if you extend them.

Placeholder values in these templates are wrapped in quotes (`"{{title}}"`) so the template files stay as valid YAML regardless of whether they live inside the scanned vault.

If you want richer expansion — prompts, file-tree queries, scripting — install the community **Templater** plugin; the YAML structure of these templates is compatible, you'd just add `<% ... %>` tags alongside.

## Authoring conventions

- **Filenames become the note's lookup handle** in the LoreWeave API. Kebab-case keeps `/search`, `/note?path=…`, and `/related` responses readable.
- **Subdirectories are a vault-layout preference, not a schema requirement.** Organizing by type (`characters/`, `factions/`, …) makes folder navigation nicer but has no effect on how LoreWeave classifies notes — `type:` in frontmatter is what counts.
- **Cross-link with `[[basename]]`** by default. Fall back to `[[full/path]]` only when two notes share a basename and you need to disambiguate.
- **Pipe-display** (`[[kael-varyn|Kael]]`) keeps prose readable without losing the link target.
- **Tags go inline in the body** (`#character`, `#arc/karsis`) — not in frontmatter. Nested tags with `/` are supported.
- **Aliases are metadata only** — free-form alternate names; LoreWeave doesn't use them for link resolution. They stay on the note as context.

## Validation reference

The rules these templates pre-satisfy are documented in the main project:

- [Vault schema](../../doc/vault_schema.md) — full frontmatter + body conventions.
- [OpenAPI spec](../../doc/open_api_spec.md) §5 — error/warning categories surfaced via `/health`.
