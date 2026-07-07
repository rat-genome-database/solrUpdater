#!/bin/bash

# Incremental Solr sync — transfers records whose last_update_date is newer
# than the given cutoff. Intended for daily cron use.
#
# Usage: ./incremental-sync.sh [solr_url] [since_date]
#
# solr_url:   Solr endpoint. Pass "" (or omit) to use solr.default.url from
#             database.properties.
# since_date: YYYY-MM-DD cutoff. Defaults to yesterday's date.
#
# Examples:
#   ./incremental-sync.sh
#   ./incremental-sync.sh "" 2026-07-01
#   ./incremental-sync.sh http://garak.rgd.mcw.edu:8983/solr/ai1 2026-07-01

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

SOLR_URL="${1:-}"
# Try GNU date first, fall back to BSD date (macOS) so this works everywhere.
SINCE_DATE="${2:-$(date -d 'yesterday' +%Y-%m-%d 2>/dev/null || date -v-1d +%Y-%m-%d)}"

echo "Incremental Solr sync"
if [ -n "$SOLR_URL" ]; then
    echo "Solr URL: $SOLR_URL"
else
    echo "Solr URL: (from database.properties)"
fi
echo "Since:    $SINCE_DATE"

exec "$SCRIPT_DIR/runSimplePostgresToSolr.sh" "$SOLR_URL" "UPDATED_AFTER $SINCE_DATE"
