#!/usr/bin/env bash
#
# testcontainers-doctor.sh
#
# Diagnostic-only health check for the Docker/Testcontainers setup this
# repository's integration tests (api-gateway, customer-service,
# lookup-service) depend on. Safe to run from Git Bash on Windows, or from
# a normal shell on Linux/macOS.
#
# This script never modifies the developer's machine: it does not touch
# ~/.testcontainers.properties, does not switch container engines, does not
# kill processes, and does not delete containers/images/volumes.
#
# Exit code is non-zero if any MANDATORY check fails (java/mvn/docker CLI
# missing, docker info unreachable). WARN lines are advisory only and do
# not affect the exit code.

set -u

PASS=0
WARN=0
FAIL=0

pass() { echo "PASS  $1"; PASS=$((PASS + 1)); }
warn() { echo "WARN  $1"; WARN=$((WARN + 1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL + 1)); }
info() { echo "INFO  $1"; }

echo "=== testcontainers-doctor: environment ==="

# --- java -------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
    pass "java on PATH: $(java -version 2>&1 | head -1)"
else
    fail "java not found on PATH"
fi

# --- maven --------------------------------------------------------------
if command -v mvn >/dev/null 2>&1; then
    pass "mvn on PATH: $(mvn -v 2>&1 | head -1)"
else
    fail "mvn not found on PATH"
fi

# --- docker CLI -----------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
    fail "docker CLI not found on PATH"
else
    pass "docker CLI on PATH"

    echo
    echo "=== docker version ==="
    if docker version >/tmp/tc-doctor-version.$$ 2>&1; then
        cat /tmp/tc-doctor-version.$$
        if grep -q "^Server:" /tmp/tc-doctor-version.$$; then
            pass "docker version reached both client and server"
        else
            fail "docker version reached the client but not the server (daemon not running?)"
        fi
    else
        cat /tmp/tc-doctor-version.$$
        fail "docker version failed"
    fi
    rm -f /tmp/tc-doctor-version.$$

    echo
    echo "=== docker info ==="
    if docker info >/tmp/tc-doctor-info.$$ 2>&1; then
        cat /tmp/tc-doctor-info.$$
        pass "docker info succeeded"
    else
        cat /tmp/tc-doctor-info.$$
        fail "docker info failed — daemon unreachable"
    fi
    rm -f /tmp/tc-doctor-info.$$

    echo
    echo "=== docker context ==="
    CTX="$(docker context show 2>/dev/null || true)"
    if [ -n "$CTX" ]; then
        info "active context: $CTX"
        docker context ls 2>&1 || true
    else
        warn "could not determine active docker context"
    fi

    echo
    echo "=== docker ps ==="
    if docker ps >/tmp/tc-doctor-ps.$$ 2>&1; then
        cat /tmp/tc-doctor-ps.$$
        pass "docker ps succeeded"
    else
        cat /tmp/tc-doctor-ps.$$
        fail "docker ps failed"
    fi
    rm -f /tmp/tc-doctor-ps.$$
fi

# --- relevant env vars ----------------------------------------------------
echo
echo "=== DOCKER_*/TESTCONTAINERS_* environment variables ==="
ENV_MATCHES="$(env | grep -E '^(DOCKER|TESTCONTAINERS)' || true)"
if [ -n "$ENV_MATCHES" ]; then
    echo "$ENV_MATCHES"
    warn "one or more DOCKER_*/TESTCONTAINERS_* overrides are set — these can silently change how Testcontainers discovers Docker"
else
    info "none set"
    pass "no DOCKER_*/TESTCONTAINERS_* overrides in this shell"
fi

# --- ~/.testcontainers.properties -----------------------------------------
echo
echo "=== \$HOME/.testcontainers.properties ==="
TC_PROPS="$HOME/.testcontainers.properties"
if [ -f "$TC_PROPS" ]; then
    info "found: $TC_PROPS"
    cat "$TC_PROPS"
    if grep -q '^docker\.client\.strategy=' "$TC_PROPS" 2>/dev/null; then
        warn "docker.client.strategy is forced in $TC_PROPS — this pins a specific provider strategy and can go stale (e.g. after switching container runtimes). If Testcontainers ever fails to discover Docker on this machine, try removing this line and letting auto-detection run before assuming a repository-level bug."
    else
        pass "no forced docker.client.strategy"
    fi
else
    info "no user-level .testcontainers.properties file found (auto-detection will run)"
    pass "no user-level .testcontainers.properties file found"
fi

# --- Rancher Desktop / rdctl ------------------------------------------------
echo
echo "=== Rancher Desktop (rdctl) ==="
if command -v rdctl >/dev/null 2>&1; then
    info "rdctl found: $(command -v rdctl)"
    RD_SETTINGS="$(rdctl list-settings 2>/dev/null || true)"
    if [ -n "$RD_SETTINGS" ]; then
        ENGINE="$(printf '%s\n' "$RD_SETTINGS" | grep -m1 '"name":' | sed -E 's/.*"name":[[:space:]]*"([^"]*)".*/\1/')"
        if [ -n "$ENGINE" ]; then
            info "containerEngine.name = $ENGINE"
            if [ "$ENGINE" = "moby" ]; then
                pass "Rancher Desktop container engine is moby (dockerd) — expected by these tests"
            else
                warn "Rancher Desktop container engine is '$ENGINE', not moby. A containerd-only engine does not speak the Docker Engine API Testcontainers expects; switch containerEngine.name to moby in Rancher Desktop settings."
            fi
        else
            warn "could not parse containerEngine.name from rdctl list-settings output"
        fi
    else
        warn "rdctl found but 'rdctl list-settings' returned nothing (Rancher Desktop not running?)"
    fi
else
    info "rdctl not found on PATH (not using Rancher Desktop, or it's not installed) — skipping"
fi

# --- port 8080 (GatewayBffIntegrationTest requires this exact port) --------
echo
echo "=== port 8080 (required by GatewayBffIntegrationTest — fixed Keycloak redirect URI) ==="
PORT_HIT=""
if command -v netstat.exe >/dev/null 2>&1; then
    PORT_HIT="$(netstat.exe -ano 2>/dev/null | grep -E '[:.]8080[[:space:]]' || true)"
elif command -v netstat >/dev/null 2>&1; then
    PORT_HIT="$(netstat -an 2>/dev/null | grep -E '[:.]8080[[:space:]]' || true)"
fi
if [ -n "$PORT_HIT" ]; then
    echo "$PORT_HIT"
    warn "port 8080 appears to be in use — GatewayBffIntegrationTest binds this port and will fail to start if it's already occupied. Identify the owning PID above and stop it yourself; this script will not do so."
else
    pass "port 8080 appears free"
fi

# --- summary ---------------------------------------------------------------
echo
echo "=== summary ==="
echo "PASS=$PASS WARN=$WARN FAIL=$FAIL"

echo
echo "=== targeted test commands ==="
cat <<'EOF'
  mvn -pl backend/api-gateway -Dtest=GatewayBffIntegrationTest test   # Keycloak (Testcontainers)
  mvn -pl backend/customer-service test                              # PostgreSQL (Testcontainers)
  mvn -pl backend/lookup-service test                                # PostgreSQL (Testcontainers)
  mvn clean install                                                  # full reactor build
EOF

if [ "$FAIL" -gt 0 ]; then
    echo
    echo "One or more mandatory checks failed — see FAIL lines above."
    exit 1
fi

exit 0
