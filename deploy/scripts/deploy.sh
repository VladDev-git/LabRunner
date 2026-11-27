#!/usr/bin/env bash
# Continuous Deployment of LabRunner on the VM.
# Runs on the self-hosted GitHub Actions runner inside the VM (job "deploy" in ci.yml);
# can also be started by hand with the same environment variables.
#
# Required env: IMAGE_TAG DB_PASSWORD REDIS_PASSWORD S3_SECRET_KEY GRAFANA_ADMIN_PASSWORD
# Optional env: TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID GRAFANA_ANONYMOUS_VIEWER APP_ROOT BASE_URL
set -euo pipefail

: "${IMAGE_TAG:?IMAGE_TAG is required (e.g. sha-1a2b3c4)}"
: "${DB_PASSWORD:?}" "${REDIS_PASSWORD:?}" "${S3_SECRET_KEY:?}" "${GRAFANA_ADMIN_PASSWORD:?}"

SRC="$(cd "$(dirname "$0")/.." && pwd)"           # deploy/ in the checkout
APP_ROOT="${APP_ROOT:-/opt/labrunner}"
DEST="$APP_ROOT/compose"
BASE_URL="${BASE_URL:-http://localhost}"
COMPOSE=(docker compose --project-directory "$DEST" -f "$DEST/docker-compose.prod.yml")

log() { printf '\n==> %s\n' "$*"; }

log "Deploying LabRunner $IMAGE_TAG to $DEST"
mkdir -p "$DEST"
# Copy the stack definition (compose, proxy, monitoring); .env is generated below.
cp "$SRC/docker-compose.prod.yml" "$SRC/Caddyfile" "$DEST/"
rm -rf "$DEST/monitoring" && cp -r "$SRC/monitoring" "$DEST/monitoring"

# Community dashboard "Node Exporter Full" (grafana.com ID 1860), fetched at deploy time.
NODE_DASH="$DEST/monitoring/grafana/dashboards/node-exporter-full.json"
if curl -fsSL --retry 3 https://grafana.com/api/dashboards/1860/revisions/latest/download -o "$NODE_DASH.tmp"; then
  sed 's/\${DS_PROMETHEUS}/prometheus/g' "$NODE_DASH.tmp" > "$NODE_DASH" && rm -f "$NODE_DASH.tmp"
else
  echo "warning: could not download dashboard 1860, continuing without it"
fi

# Secrets come from the GitHub environment "production" and are written only here (mode 600).
log "Writing $DEST/.env"
umask 077
cat > "$DEST/.env" <<EOF
IMAGE_TAG=$IMAGE_TAG
DB_NAME=labrunner
DB_USER=labrunner
DB_PASSWORD=$DB_PASSWORD
REDIS_PASSWORD=$REDIS_PASSWORD
S3_ACCESS_KEY=labrunner
S3_SECRET_KEY=$S3_SECRET_KEY
S3_BUCKET=labrunner-reports
GRAFANA_ADMIN_PASSWORD=$GRAFANA_ADMIN_PASSWORD
GRAFANA_ANONYMOUS_VIEWER=${GRAFANA_ANONYMOUS_VIEWER:-false}
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN:-}
TELEGRAM_CHAT_ID=${TELEGRAM_CHAT_ID:-}
EOF
umask 022

log "Pulling images"
# Registries occasionally time out: retry before failing the deployment.
for attempt in 1 2 3; do
  "${COMPOSE[@]}" pull --quiet && break
  [ "$attempt" -eq 3 ] && { echo "image pull failed 3 times"; exit 1; }
  echo "pull failed (attempt $attempt), retrying in 10 s"; sleep 10
done

log "Starting the stack (waits until every service is healthy)"
"${COMPOSE[@]}" up -d --remove-orphans --wait --wait-timeout 180
"${COMPOSE[@]}" ps --format 'table {{.Name}}\t{{.Image}}\t{{.Status}}'

# ---------- Post-deploy smoke test ----------
log "Smoke test 1/2: GET $BASE_URL/health"
health=""
for _ in $(seq 1 30); do
  health=$(curl -fsS "$BASE_URL/health" 2>/dev/null) && echo "$health" | grep -q '"UP"' && break
  sleep 2
done
echo "$health"
echo "$health" | jq -e --arg v "$IMAGE_TAG" '.status == "UP" and .version == $v' > /dev/null \
  || { echo "health check failed or wrong version deployed"; "${COMPOSE[@]}" logs --tail 50 api; exit 1; }
echo "HTTP 200, status UP, version $IMAGE_TAG"

log "Smoke test 2/2: submission goes through API -> Redis -> Orchestrator -> S3"
assignment=$(curl -fsS "$BASE_URL/api/v1/assignments" | jq '.[0].id')
created=$(curl -fsS -X POST "$BASE_URL/api/v1/submissions" -H 'Content-Type: application/json' \
  -d "{\"assignmentId\": $assignment, \"studentName\": \"smoke-test\", \"sourceCode\": \"fun main() { println(42) }\"}")
id=$(echo "$created" | jq -r '.id')
status=""
for _ in $(seq 1 30); do
  status=$(curl -fsS "$BASE_URL/api/v1/submissions/$id" | jq -r '.status')
  case "$status" in PASSED|FAILED|ERROR) break ;; esac
  sleep 1
done
echo "submission $id -> $status"
[ "$status" = "PASSED" ] || { echo "end-to-end check failed"; "${COMPOSE[@]}" logs --tail 50 orchestrator; exit 1; }
curl -fsS "$BASE_URL/api/v1/submissions/$id/report" | jq -c '{runner, score, maxScore, durationMs}'

log "Deployment of $IMAGE_TAG finished"
