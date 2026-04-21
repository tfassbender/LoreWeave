# LoreWeave – Deployment

How to run LoreWeave on a Linux server so a Custom GPT Action (or any authenticated client) can query it over HTTPS. This document covers a single-host deployment with systemd + a reverse proxy; horizontal scaling is out of scope for v1 (the index is in-memory per process, and the vault is small enough that one JVM is plenty).

The first real rollout is tracked in [Phase 12 of the implementation plan](implementation_plan.md#phase-12--first-deployment). This document is the reference — phase 12 is the checklist that confirms we've done it once.

## 1. Prerequisites

On the target host:

- **Linux** — tested assumption is Debian/Ubuntu or any systemd-driven distro. Commands below use `apt`; swap for your package manager.
- **JDK 21** on `$PATH`. Temurin works well:
  ```sh
  sudo apt install -y temurin-21-jdk   # via Adoptium's repo
  java --version                       # confirm 21.x
  ```
- **systemd** — already present on any reasonable modern distro.
- **A domain name** pointed at the host's public IP (`A` record for the subdomain you want, e.g. `loreweave.example.com`).
- **Git access to your vault repository** — either public HTTPS, a deploy key, or a personal access token scoped to that one repo. If it's private, set up SSH and make sure `ssh -T git@github.com` works for the user that'll run LoreWeave.

On your laptop:

- A local build of the fast-jar (`./gradlew build`). You'll copy the output to the server.

## 2. Directory layout on the server

The convention this guide follows:

```
/opt/loreweave/                        # the app itself
├── quarkus-app/                       # copied from the build
│   ├── quarkus-run.jar
│   ├── app/
│   ├── lib/
│   └── quarkus/
└── start.sh                           # optional wrapper

/etc/loreweave/
└── application-local.properties       # secrets, chmod 600

/var/lib/loreweave/
└── vault/                             # the cloned Obsidian vault; git-managed

/var/log/loreweave/                    # rotating log files
└── loreweave.log
```

Reasons for the split:

- `/opt/loreweave/` — the binary. Replaced on upgrade.
- `/etc/loreweave/` — secrets and machine-specific config. Survives upgrades. Permissions matter.
- `/var/lib/loreweave/` — mutable data (the working-tree clone of the vault). Survives upgrades.
- `/var/log/loreweave/` — log output, rotated by the application.

Create them and a dedicated user:

```sh
sudo useradd --system --no-create-home --shell /usr/sbin/nologin loreweave
sudo mkdir -p /opt/loreweave /etc/loreweave /var/lib/loreweave /var/log/loreweave
sudo chown -R loreweave:loreweave /var/lib/loreweave /var/log/loreweave
sudo chmod 750 /etc/loreweave
```

## 3. Install the fast-jar

From your laptop, build then copy:

```sh
# On the laptop
./gradlew build

# Copy the whole quarkus-app folder (not just the run.jar — it needs its siblings)
scp -r build/quarkus-app loreweave-host:/tmp/
```

On the server:

```sh
sudo rsync -a --delete /tmp/quarkus-app/ /opt/loreweave/quarkus-app/
sudo chown -R loreweave:loreweave /opt/loreweave
```

Smoke-test before going further:

```sh
sudo -u loreweave java -jar /opt/loreweave/quarkus-app/quarkus-run.jar
# Ctrl-C once you see "Listening on http://0.0.0.0:4717"
```

## 4. Configure `application-local.properties`

This is where the remote vault URL and the bearer token live. Keep it out of the repo, out of `/opt`, and on 600 permissions.

```sh
sudo tee /etc/loreweave/application-local.properties > /dev/null <<'PROPS'
# Where the vault lives. Use SSH if private; HTTPS is fine for public repos.
loreweave.vault.remote=git@github.com:your-org/your-vault.git

# Absolute paths so systemd's working directory doesn't matter.
loreweave.vault.local-path=/var/lib/loreweave/vault
loreweave.logging.path=/var/log/loreweave

# Bind the HTTP server to localhost — the reverse proxy will expose 443.
quarkus.http.host=127.0.0.1
quarkus.http.port=4717

# Bearer token. Generate with: openssl rand -base64 32
loreweave.auth.token=PASTE_GENERATED_TOKEN_HERE

# Pull interval. 5m is reasonable for an always-on service.
loreweave.sync.interval=5m
PROPS

sudo chown loreweave:loreweave /etc/loreweave/application-local.properties
sudo chmod 600 /etc/loreweave/application-local.properties
```

Generate the token first, then paste:

```sh
openssl rand -base64 32
```

## 5. systemd unit

Drop this file at `/etc/systemd/system/loreweave.service`:

```ini
[Unit]
Description=LoreWeave REST API
Documentation=https://github.com/tfassbender/LoreWeave
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=loreweave
Group=loreweave
WorkingDirectory=/opt/loreweave

# Load the local overrides via -D so SmallRye Config picks them up.
ExecStart=/usr/bin/java \
  -Dsmallrye.config.locations=/etc/loreweave/application-local.properties \
  -jar /opt/loreweave/quarkus-app/quarkus-run.jar

Restart=on-failure
RestartSec=5s

# Hardening — most of these are safe-and-free with our service model.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/loreweave /var/log/loreweave
CapabilityBoundingSet=
AmbientCapabilities=
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
LockPersonality=true
MemoryDenyWriteExecute=false
# (Quarkus needs W+X for some CDI codegen; leaving MDWE off.)

[Install]
WantedBy=multi-user.target
```

Enable and start:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now loreweave
sudo systemctl status loreweave
```

Watch startup:

```sh
sudo journalctl -u loreweave -f
```

On first boot the service will clone the remote vault into `/var/lib/loreweave/vault`, build the index, and start serving. That can take seconds to a minute depending on vault size and network.

Confirm locally:

```sh
curl -s http://127.0.0.1:4717/health | jq .
# status: UP | DEGRADED | ...
```

## 6. Reverse proxy + TLS — Caddy (recommended)

Caddy is the quickest path to HTTPS because it handles Let's Encrypt automatically. Install it, then drop a config.

```sh
sudo apt install -y caddy
```

`/etc/caddy/Caddyfile`:

```caddyfile
loreweave.example.com {
    encode gzip
    reverse_proxy 127.0.0.1:4717

    # Tight CORS isn't necessary if only Custom GPT Actions call this,
    # but adding a restrictive default is cheap defense in depth.
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options     "nosniff"
        Referrer-Policy            "no-referrer"
    }
}
```

Reload:

```sh
sudo systemctl reload caddy
```

Caddy will obtain and renew the TLS cert automatically on the first request. Verify:

```sh
curl -s https://loreweave.example.com/health | jq .
```

### Alternative: nginx + certbot

If you prefer nginx, use certbot for TLS provisioning:

```sh
sudo apt install -y nginx python3-certbot-nginx
```

`/etc/nginx/sites-available/loreweave.conf`:

```nginx
server {
    listen 80;
    server_name loreweave.example.com;

    location / {
        proxy_pass http://127.0.0.1:4717;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```sh
sudo ln -s /etc/nginx/sites-available/loreweave.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d loreweave.example.com
```

## 7. Firewall

Only 80 and 443 need to be public. The LoreWeave process itself listens on `127.0.0.1:4717` (because of `quarkus.http.host=127.0.0.1` in step 4), so even without a firewall it's not directly reachable from the internet — but a firewall is still sensible belt-and-braces.

```sh
sudo ufw allow ssh
sudo ufw allow http
sudo ufw allow https
sudo ufw enable
```

Double-check the bind:

```sh
sudo ss -tlnp | grep 4717
# LISTEN ... 127.0.0.1:4717 ... /opt/loreweave/.../java
```

If it says `0.0.0.0:4717`, the config didn't take — recheck `quarkus.http.host` in `/etc/loreweave/application-local.properties` and restart.

## 8. Bearer-token handling

The token in `application-local.properties` is the only credential clients present. Rules:

- **Generate with a CSPRNG** — `openssl rand -base64 32` is fine.
- **Store only on the server** (root-owned or loreweave-owned, 600). Never check it into either repo.
- **Distribute only to clients that need it** — e.g. the Custom GPT Action's auth config. Treat each distribution as a separate copy; if one is compromised, rotate.
- **Rotation procedure** — edit the file, `sudo systemctl restart loreweave`. There's only one token in v1 (no overlap window); clients need to be updated at the same time.

## 9. Vault-repository git access

The LoreWeave user needs to clone and pull your vault's git repository. Pick one:

- **Public HTTPS** — set `loreweave.vault.remote=https://github.com/…`. Works if the repo is public.
- **SSH with a deploy key** — generate a key as the `loreweave` user, add the public key to the repo's deploy keys (read-only is enough), set `loreweave.vault.remote=git@github.com:…`.
  ```sh
  sudo -u loreweave ssh-keygen -t ed25519 -C 'loreweave on hostname' -f /var/lib/loreweave/.ssh/id_ed25519 -N ''
  sudo cat /var/lib/loreweave/.ssh/id_ed25519.pub   # paste into GitHub deploy keys
  sudo -u loreweave ssh -T git@github.com             # accept host key
  ```
- **GitHub fine-grained PAT** — set the remote to `https://oauth2:TOKEN@github.com/…`. Easier but puts the token in the remote URL (and `application-local.properties`).

Deploy keys are the cleanest trade-off — scoped to one repo, separate from any personal credential.

## 10. Verifying the deployment

Once the unit is up and the reverse proxy responds:

```sh
# Public: no auth required
curl -s https://loreweave.example.com/health | jq .
# Expect: { "status": "UP", "notes_count": N, "last_sync": { "ok": true, ... }, ... }

# Authed: every other endpoint
TOKEN='the-token-you-generated'
curl -s -H "Authorization: Bearer $TOKEN" \
    'https://loreweave.example.com/search?q=kael' | jq .

# OpenAPI (public; used when importing into a GPT Action)
curl -s https://loreweave.example.com/q/openapi
```

Connect the Custom GPT Action:

1. Open the GPT editor, Actions → Import schema from URL → `https://loreweave.example.com/q/openapi`.
2. Authentication → API Key → Bearer → paste the token.
3. Ask the GPT "search for Kael"; it should hit `/search` and return the summary.

## 11. Upgrade procedure

Zero-downtime isn't a goal for v1 (single process, single host). The straightforward cycle:

```sh
# On the laptop
./gradlew build
scp -r build/quarkus-app loreweave-host:/tmp/

# On the server
sudo systemctl stop loreweave
sudo rsync -a --delete /tmp/quarkus-app/ /opt/loreweave/quarkus-app/
sudo chown -R loreweave:loreweave /opt/loreweave
sudo systemctl start loreweave
sudo journalctl -u loreweave -f   # watch startup
```

If the upgrade introduces a config-mapping change, `application-local.properties` may need a new key. Check the release notes for any added keys and edit the file *before* restarting — otherwise Quarkus's strict validation will crash at boot. (The `/health` endpoint is unaffected by config changes, so a quick curl confirms a good restart.)

If something goes wrong, roll back:

```sh
# Keep the previous quarkus-app as /opt/loreweave/quarkus-app.prev for fast rollback.
sudo systemctl stop loreweave
sudo rm -rf /opt/loreweave/quarkus-app
sudo mv /opt/loreweave/quarkus-app.prev /opt/loreweave/quarkus-app
sudo systemctl start loreweave
```

## 12. Troubleshooting

| Symptom | Where to look |
|---------|---------------|
| Service won't start | `sudo journalctl -u loreweave -xe` — Quarkus logs the reason. Missing config key → `SRCFG00014`; bad YAML in a vault note → `parse_errors` at startup but server keeps going. |
| `/health` shows `DEGRADED` and `last_sync.ok: false` | Git clone/pull failed. Check `last_sync.error`. Usually a credentials issue (deploy key missing, PAT expired) or a network/firewall block between the host and the git remote. |
| `/health` shows `DEGRADED` but `last_sync.ok: true` | Validation errors in the vault. Inspect `validation.errors.*.samples[]` for the offending files. |
| 401 from every endpoint | Bearer token mismatch, or the token in `application-local.properties` is empty. Server is fail-closed: an unset token rejects every request. |
| TLS cert not provisioning | Caddy needs the domain pointed at the host *before* starting. Check DNS, firewall (80 must be open for the ACME HTTP-01 challenge), then `sudo systemctl reload caddy`. |
| Slow startup | First-boot clone of a large vault is the usual cause. Watch `journalctl -u loreweave -f` — the clone line tells you the size. |
| OOM on large vaults | Bump heap: `ExecStart=... java -Xmx1g -Dsmallrye.config.locations=...`. Default heap on a 4 GB host is usually 1 GB already, so check `journalctl` for the actual heap Quarkus picked. |

### Useful one-liners

```sh
# Service status + last 20 log lines
sudo systemctl status loreweave -n 20

# Tail app logs (file + stdout captured by journald)
sudo tail -f /var/log/loreweave/loreweave.log
sudo journalctl -u loreweave -f

# Force a resync without a restart
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
    https://loreweave.example.com/sync | jq .

# Verify config the app actually sees (Quarkus logs the resolved values only at DEBUG)
sudo -u loreweave java \
  -Dsmallrye.config.locations=/etc/loreweave/application-local.properties \
  -Dquarkus.log.level=DEBUG \
  -jar /opt/loreweave/quarkus-app/quarkus-run.jar 2>&1 | head -50
```

## 13. What's NOT covered here

- **Horizontal scaling** — out of scope for v1. The index is in-memory per process.
- **Database / persistent cache** — not used. Sync wipes and rebuilds.
- **Multi-tenancy** — one vault per instance. Run a second systemd unit with different paths if you need a second vault.
- **Backups** — the vault lives in git; that's the backup. `/etc/loreweave/application-local.properties` should be backed up once after initial setup (it contains the token, not data, so it changes only on rotation).

## 14. Where to go next

- [Implementation plan](implementation_plan.md) — phased progress, including Phase 12 which tracks the first real deployment.
- [OpenAPI spec](open_api_spec.md) — the API surface this guide exposes.
- [Vault schema](vault_schema.md) + [Authoring guide](authoring_guide.md) — so the vault you point this at is well-formed.
