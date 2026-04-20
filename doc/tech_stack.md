# LoreWeave – Tech Stack

Technology choices for the LoreWeave implementation. Decisions are stable but not immutable — revisit if a choice becomes a blocker.

## Summary

| Concern | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Quarkus 3.x |
| Build tool | Gradle (Kotlin DSL) |
| Group / root package | `com.tfassbender.loreweave` |
| Packaging | `quarkus-app` fast-jar, JVM mode |
| HTTP layer | Quarkus REST (RESTEasy Reactive) |
| JSON | Jackson (`quarkus-rest-jackson`) |
| OpenAPI | `quarkus-smallrye-openapi` (generated from annotations) |
| Health | `quarkus-smallrye-health` (extended with custom check) |
| Scheduling | `quarkus-scheduler` |
| CLI entrypoint | `quarkus-picocli` |
| Markdown parser | commonmark-java |
| YAML parser | snakeyaml-engine |
| Git client | JGit (Eclipse) |
| Config | `application.properties`; machine-specific overrides in git-ignored `application-local.properties` |
| Tests | JUnit 5 + RestAssured |
| License | MIT |

## Rationale

### Java 21 on Quarkus
The user has existing Quarkus experience. Quarkus gives us fast boot, dev-mode hot reload, first-class REST/CDI/config, and an optional native-image path held for later. Java 21 unlocks records, pattern matching for switch, and virtual threads — useful for the parsing and graph-walking code.

### Gradle over Maven
User preference. Kotlin DSL for type-safe build scripts and better IntelliJ support.

### JGit over system git
Removes a runtime dependency (no `git` required on the server), identical behavior across Windows development and Linux production, and typed exceptions instead of stdout parsing. Well-maintained by the Eclipse Foundation; used internally by Gerrit, Eclipse/EGit, and JetBrains tooling.

### commonmark-java over flexmark
We only need an AST — no custom rendering, no HTML output. commonmark-java is smaller, simpler, and sufficient. We walk the AST ourselves for `[[wiki-link]]` and `#hashtag` extraction so that tags inside code blocks, headings, and URL fragments are naturally ignored.

### snakeyaml-engine
Actively maintained successor to snakeyaml, YAML 1.2 compliant. We parse into plain `Map`/`List` structures — no data binding to Java classes is needed, because `metadata:` must stay flexible.

### `application.properties` (not a YAML/JSON config file)
User preference, simpler, Quarkus's default. An external structured config file for validation rules and type taxonomy is a possible future improvement, explicitly deferred from v1.

### No native image in v1
JVM-mode fast-jar boots fast enough, has a much better dev loop, and avoids GraalVM's reflection/serialization quirks. Revisit if the linux server is memory-constrained.

### No CI in v1
Low-volume solo project. GitHub Actions can be added later if the code surface makes it valuable.

## Dependency versions

Exact versions are pinned in `build.gradle.kts` at the time of scaffolding. Rule: prefer the latest stable release at the first commit of each phase, with explicit version upgrades thereafter. Expect roughly:

- Quarkus 3.17+
- JGit 6.10+
- commonmark-java 0.22+
- snakeyaml-engine 2.8+
- JUnit 5.11+

Update this document when versions change meaningfully (e.g. a major Quarkus upgrade).
