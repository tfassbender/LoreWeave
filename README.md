# LoreWeave

A REST API that turns a Git-backed Obsidian vault of markdown notes into a queryable knowledge graph — designed for consumption by AI agents, primarily Custom GPT Actions.

## Status

Implementation is nearly feature-complete. Remaining work tracked in [`doc/implementation_plan.md`](doc/implementation_plan.md).

## Documentation

### For authors writing notes

- [Authoring guide](doc/authoring_guide.md) — conventions for filenames, links, tags, summaries, and types.
- [Vault schema](doc/vault_schema.md) — formal frontmatter and body rules enforced by the parser.
- [Obsidian templates](examples/obsidian-templates/README.md) — drop-in starter files for each note type.

### For operators deploying the service

- [Deployment guide](doc/deployment.md) — Linux server setup with systemd, Caddy/nginx, TLS, and token handling.
- [Tech stack](doc/tech_stack.md) — language, framework, libraries, and version policy.

### For API consumers (Custom GPT Actions, clients, integrations)

- [OpenAPI spec](doc/open_api_spec.md) — REST contract. Human-readable mirror of the live `/q/openapi` document.

### For anyone curious about how it works

- [System overview](doc/system_overview.md) — architecture and design philosophy.
- [Implementation plan](doc/implementation_plan.md) — phased roadmap with progress checkboxes.

## Related repositories

- [LoreWeaveTestVault](https://github.com/tfassbender/LoreWeaveTestVault) — a small sample Obsidian vault used by the end-to-end tests and as a reference for authoring notes against the schema. Clone it into `./vault/` (git-ignored) or point `loreweave.vault.remote` at its URL.
- [LoreWeaveWatcher](https://github.com/tfassbender/LoreWeaveWatcher) — a sibling desktop tool: drop a fat jar into a dot-prefixed folder inside your vault, launch it, and a browser tab shows validation results that update while you edit. Shares the parser with this repo; use it for the author-time feedback loop.

## License

[MIT](LICENSE)
