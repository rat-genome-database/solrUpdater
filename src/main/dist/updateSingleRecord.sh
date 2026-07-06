#!/bin/bash

# Script to update a single record in Solr by PMID
# Usage: ./updateSingleRecord.sh <pmid> [solr_url]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <pmid> [solr_url]"
    echo "Example: $0 12345678"
    echo "Example: $0 12345678 http://garak.rgd.mcw.edu:8983/solr/ai1"
    echo "(If solr_url is omitted, the value from database.properties is used.)"
    exit 1
fi

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

PMID=$1
SOLR_URL=${2:-""}

echo "Single Record Solr Update"
echo "PMID: $PMID"
if [ -n "$SOLR_URL" ]; then
    echo "Solr URL: $SOLR_URL"
else
    echo "Solr URL: (from database.properties)"
fi

# Run the SingleRecordUpdater class with proper classpath
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.SingleRecordUpdater "$PMID" "$SOLR_URL"

echo "Single record update completed"