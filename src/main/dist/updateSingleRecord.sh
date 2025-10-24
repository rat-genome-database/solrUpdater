#!/bin/bash

# Script to update a single record in Solr by PMID
# Usage: ./updateSingleRecord.sh <pmid> [solr_url]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <pmid> [solr_url]"
    echo "Example: $0 12345678"
    echo "Example: $0 12345678 http://dev.rgd.mcw.edu:8983/solr/ai1"
    exit 1
fi

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

PMID=$1
SOLR_URL=${2:-"http://dev.rgd.mcw.edu:8983/solr/OntoMate"}

echo "Single Record Solr Update"
echo "PMID: $PMID"
echo "Solr URL: $SOLR_URL"

# Run the SingleRecordUpdater class with proper classpath
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.SingleRecordUpdater "$PMID" "$SOLR_URL"

echo "Single record update completed"