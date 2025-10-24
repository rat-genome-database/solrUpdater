#!/bin/bash

# Script to update a single record in Solr by PMID
# Usage: ./updateSingleRecord.sh <pmid> [solr_url]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <pmid> [solr_url]"
    echo "Example: $0 12345678"
    echo "Example: $0 12345678 http://dev.rgd.mcw.edu:8983/solr/ai1"
    exit 1
fi

PMID=$1
SOLR_URL=${2:-"http://dev.rgd.mcw.edu:8983/solr/OntoMate"}

echo "Single Record Solr Update"
echo "PMID: $PMID"
echo "Solr URL: $SOLR_URL"

# Use gradle custom task to handle classpath properly
# Set system property for external config file
./gradlew runSingleUpdater -PappArgs="$PMID $SOLR_URL" -Dconfig.file=/data/properties/database.properties

echo "Single record update completed"