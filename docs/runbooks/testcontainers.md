# Testcontainers / Docker runbook

This repo's integration tests (`GatewayBffIntegrationTest` in api-gateway,
`CustomerServiceIntegrationTest` in customer-service, `LookupServiceIntegrationTest`
in lookup-service) start real containers via Testcontainers — a real Keycloak
and real PostgreSQL. They require a working Docker-compatible runtime; there
is no mock fallback and none should be added (a Testcontainers failure must
fail the build, not silently skip).

All commands below are Git Bash syntax. Windows users should run Git Bash,
not PowerShell or cmd.exe, when following this runbook (equivalent commands
exist in other shells, but this doc does not maintain a parallel catalog).

## Supported local runtimes

- **Rancher Desktop**, container engine set to **moby** (dockerd) — the
  supported setup on this team's Windows machines.
- Docker Desktop.
- Docker Engine natively on Linux.

A containerd-only engine (Rancher Desktop's `containerd` option) does not
speak the Docker Engine API Testcontainers expects. If Rancher Desktop is
in use, verify the engine before debugging anything else:

```bash
docker version
docker info
docker context ls
docker context show
rdctl list-settings   # look for containerEngine.name
```

`docker info` prints containerd-related fields (snapshotter, containerd
version) even when the selected engine is moby — that output describes an
internal component of dockerd on Rancher Desktop, not the CLI engine in
use. The authoritative field is `containerEngine.name` in
`rdctl list-settings`; it must read `moby`.

## `~/.testcontainers.properties`

This is a **user-level** file (`$HOME/.testcontainers.properties`), never
committed to the repo. Testcontainers writes to it automatically when a
provider strategy is discovered, e.g.:

```
docker.client.strategy=org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy
```

A forced strategy line can go stale — for example if you switch container
runtimes, reinstall Rancher Desktop, or the named pipe/socket path changes.
When Testcontainers reports "Could not find a valid Docker environment" but
`docker version` / `docker info` / `docker ps` all succeed from the same
shell, the forced strategy is a prime suspect: back up the file and remove
just the `docker.client.strategy=` line, letting Testcontainers auto-detect
again.

```bash
cp "$HOME/.testcontainers.properties" "$HOME/.testcontainers.properties.backup-$(date +%Y%m%d-%H%M%S)"
sed '/^docker\.client\.strategy=/d' "$HOME/.testcontainers.properties" > "$HOME/.testcontainers.properties.tmp"
mv "$HOME/.testcontainers.properties.tmp" "$HOME/.testcontainers.properties"
```

Do not commit this file, and do not force a Windows named-pipe (or a Unix
socket) strategy anywhere in repository resources — it would break every
developer not on that exact platform.

## Environment variables

`DOCKER_*` and `TESTCONTAINERS_*` variables override discovery/behavior
silently. Check for stragglers before debugging anything else:

```bash
env | grep -E "^(DOCKER|TESTCONTAINERS)" || true
```

None should normally be set for local development on this project.

## Testcontainers / docker-java versions

All three Testcontainers-using modules (api-gateway, customer-service,
lookup-service) currently pin:

- `testcontainers` (BOM) `1.21.3`
- `docker-java-api` / `docker-java-transport-zerodep` / `docker-java-transport` `3.5.1`
- Surefire `argLine`: `-Dapi.version=1.44`

Rationale (see the pom.xml comments in each module): Testcontainers 1.21.3
ships against an older `docker-java` whose default API-version probe is too
old for Docker Engine 29+ ("client version 1.32 is too old; minimum is
1.41"); the `docker-java` 3.5.1 override plus the `-Dapi.version=1.44`
surefire pin makes API negotiation work against Docker 29 without changing
Testcontainers' major version.

This configuration is currently duplicated per-module rather than
centralized in the root POM (`api-gateway`'s copy doesn't even use a
`testcontainers.version`/`docker-java.version` property the way
customer-service/lookup-service do — it hardcodes the versions directly).
Centralizing it in the root POM's `dependencyManagement` is a reasonable
follow-up cleanup, but is **not** something to do reactively while chasing
a Docker-discovery failure — verify the actual failure first (see below),
because in this codebase's history the version/API-negotiation pin has
already been applied; a Docker-discovery failure that still occurs despite
it has a different root cause (most often the state described below).

Before bumping `testcontainers.version` or `docker-java.version`, reproduce
the failure with the debug logging shown below and confirm it is actually a
version/API-negotiation problem — the generic "Could not find a valid
Docker environment" exception can also come from `~/.testcontainers.properties`
holding a stale forced strategy, from the Docker daemon actually being
down, from a port conflict, or from an image-pull/network problem. Don't
diagnose from the final exception message alone.

## Diagnostics

Run the read-only doctor script first — it never modifies your machine:

```bash
bash scripts/testcontainers-doctor.sh
```

It checks java/mvn/docker availability, `docker version`/`info`/`ps`,
active docker context, `DOCKER_*`/`TESTCONTAINERS_*` env vars, the contents
of `~/.testcontainers.properties` (warning if a strategy is forced), the
Rancher Desktop container engine (via `rdctl`, if present), and whether
port 8080 is free.

If the doctor script passes but a test still fails, get focused logs
instead of jumping to `-X`:

```bash
mvn -pl backend/api-gateway \
  -Dtest=GatewayBffIntegrationTest \
  -Dorg.slf4j.simpleLogger.log.org.testcontainers=DEBUG \
  -Dorg.slf4j.simpleLogger.log.com.github.dockerjava=DEBUG \
  test
```

If that logging is inconclusive, inspect
`backend/api-gateway/target/surefire-reports` before falling back to
`mvn -X`.

## Targeted commands

```bash
mvn -pl backend/api-gateway -Dtest=GatewayBffIntegrationTest test   # Keycloak
mvn -pl backend/customer-service test                               # PostgreSQL
mvn -pl backend/lookup-service test                                 # PostgreSQL
mvn clean install                                                   # full reactor build
```

`GatewayBffIntegrationTest` binds the fixed port **8080** (the committed
Keycloak realm's redirect URI points there) — don't run it while a local
gateway instance, or anything else, is already bound to that port. The
doctor script checks this.

## Troubleshooting order

1. **Docker discovery failure** — `docker version`/`info`/`ps` fail, or
   Testcontainers logs "Could not find a valid Docker environment" even
   though the CLI works. Run the doctor script; check for a stale forced
   strategy in `~/.testcontainers.properties`, a stray `DOCKER_*`/
   `TESTCONTAINERS_*` env var, or (Rancher Desktop) the wrong container
   engine selected.
2. **Image-pull failure** — network/registry errors pulling
   `quay.io/keycloak/keycloak` or `postgres`. Not a discovery problem;
   check network access and registry credentials.
3. **Container readiness failure** — the container starts but its wait
   strategy times out (e.g. Keycloak not answering `/realms/crm-lite`
   within 3 minutes). Check container logs, not discovery logs.
4. **Port conflict** — port 8080 already bound; identify the owning PID
   from `netstat.exe -ano` and stop it yourself. Don't change the test's
   port without reviewing the committed Keycloak redirect URI contract.
5. **Application test assertion failure** — Docker and containers are
   fine; the test itself is failing on its actual assertions. This is an
   ordinary test failure, unrelated to any of the above.

Integration tests must never be skipped, disabled, or mocked out to make a
build green — a Docker-based test failure is real signal.

## CI

CI must provide a Docker-compatible runtime (a Docker-in-Docker or
Docker-socket-mounted runner) for the reactor build to pass; `mvn clean
install` runs these integration tests by default and does not skip them.
