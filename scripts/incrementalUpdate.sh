#!/bin/bash
#
# Incremental Solr update — intended to run from cron.
# Transfers all solr_docs rows with last_update_date newer than
# LOOKBACK_HOURS ago (default 25h for nightly with 1h safety overlap).
#
# Usage (cron):
#   0 2 * * * /data/solrUpdater/scripts/incrementalUpdate.sh
#
# Usage (manual, overriding defaults):
#   SOLR_URL=http://dev.rgd.mcw.edu:8983/solr/ai1 LOOKBACK_HOURS=2 \
#     ./scripts/incrementalUpdate.sh
#
set -euo pipefail

# --- config (override via env) ---
REPO_DIR="${REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
SOLR_URL="${SOLR_URL:-http://garak.rgd.mcw.edu:8983/solr/ai1}"
LOOKBACK_HOURS="${LOOKBACK_HOURS:-25}"
CONFIG_FILE="${CONFIG_FILE:-/data/properties/database.properties}"
LOG_DIR="${LOG_DIR:-/var/log/solrUpdater}"
LOCK_FILE="${LOCK_FILE:-/tmp/solrUpdater.lock}"
# ---------------------------------

mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/incremental-$(date +%Y%m%d).log"

# Compute cutoff timestamp (UTC ISO-8601, no spaces so the
# space-split arg parser in SimplePostgresToSolr sees one token).
if date -u -d "1 hour ago" +%s >/dev/null 2>&1; then
    SINCE=$(date -u -d "${LOOKBACK_HOURS} hours ago" +%Y-%m-%dT%H:%M:%S)
else
    # BSD/macOS date fallback (for local testing)
    SINCE=$(date -u -v-"${LOOKBACK_HOURS}"H +%Y-%m-%dT%H:%M:%S)
fi

cd "$REPO_DIR"

{
    echo "===== $(date -u +%Y-%m-%dT%H:%M:%SZ) starting incremental update ====="
    echo "REPO_DIR=$REPO_DIR"
    echo "SOLR_URL=$SOLR_URL"
    echo "LOOKBACK_HOURS=$LOOKBACK_HOURS"
    echo "SINCE=$SINCE"
    echo "CONFIG_FILE=$CONFIG_FILE"
} >> "$LOG"

# flock prevents overlapping runs if one goes long.
# --no-daemon prevents gradle daemons piling up under cron.
exec /usr/bin/flock -n "$LOCK_FILE" \
    ./gradlew --no-daemon run \
        --args="$SOLR_URL UPDATED_AFTER $SINCE" \
        -Dconfig.file="$CONFIG_FILE" \
        >> "$LOG" 2>&1
