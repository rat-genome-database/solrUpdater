#!/bin/bash

# Full Solr sync — transfers every record in solr_docs, processed in chunks
# (chunk size comes from solr.chunk.size in database.properties, default 10000).
# Intended as a one-time seed before enabling the daily incremental cron.
#
# Usage: ./full-sync.sh [solr_url]
#
# solr_url: Solr endpoint. Pass "" (or omit) to use solr.default.url from
#           database.properties.
#
# Examples:
#   ./full-sync.sh
#   ./full-sync.sh http://garak.rgd.mcw.edu:8983/solr/ai1
#
# This is long-running. Run under nohup / tmux / screen so it survives a
# disconnected shell:
#   nohup ./full-sync.sh > full-sync.log 2>&1 &

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

SOLR_URL="${1:-}"

echo "Full Solr sync (CHUNKS mode)"
if [ -n "$SOLR_URL" ]; then
    echo "Solr URL: $SOLR_URL"
else
    echo "Solr URL: (from database.properties)"
fi

exec "$SCRIPT_DIR/runSimplePostgresToSolr.sh" "$SOLR_URL" "CHUNKS"
