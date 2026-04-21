# LoreWeave

A REST API that turns a Git-backed Obsidian vault of markdown notes into a queryable knowledge graph — designed for consumption by AI agents, primarily Custom GPT Actions.

## Status

Early development. Design is complete; implementation is in progress — see [`doc/implementation_plan.md`](doc/implementation_plan.md).

## Documentation

- [System overview](doc/system_overview.md) — architecture and design philosophy
- [OpenAPI spec](doc/open_api_spec.md) — REST contract
- [Tech stack](doc/tech_stack.md) — language, framework, libraries
- [Vault schema](doc/vault_schema.md) — frontmatter and body conventions
- [Implementation plan](doc/implementation_plan.md) — phased roadmap with progress checkboxes

## Related repositories

- [LoreWeaveTestVault](https://github.com/tfassbender/LoreWeaveTestVault) — a small sample Obsidian vault used by the end-to-end tests and as a reference for authoring notes against the schema. Clone it into `./vault/` (git-ignored) or point `loreweave.vault.remote` at its URL.

## License

[MIT](LICENSE)
