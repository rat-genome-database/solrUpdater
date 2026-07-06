#!/bin/bash

# Gene-only updater script for efficient atomic updates
# Usage: ./updateGeneOnly.sh <pmid> [solr_url]

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

PMID=$1
SOLR_URL=${2:-""}

if [ -z "$PMID" ]; then
    echo "Usage: $0 <pmid> [solr_url]"
    echo "Example: $0 12345678 http://garak.rgd.mcw.edu:8983/solr/ai1"
    echo "(If solr_url is omitted, the value from database.properties is used.)"
    exit 1
fi

echo "Gene-only atomic update for PMID: $PMID"
if [ -n "$SOLR_URL" ]; then
    echo "Solr URL: $SOLR_URL"
else
    echo "Solr URL: (from database.properties)"
fi

# Run the gene-only updater
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.GeneOnlyUpdater "$PMID" "$SOLR_URL"

echo "Gene update completed"