#!/bin/bash

# Simple script to transfer data from PostgreSQL to Solr (no AI processing)
# Usage: ./runSimplePostgresToSolr.sh [solr_url] [date_filter]

SOLR_URL=${1:-"http://dev.rgd.mcw.edu:8983/solr/OntoMate"}
DATE_FILTER=${2}

echo "Simple PostgreSQL to Solr transfer"
echo "Solr URL: $SOLR_URL"
if [ -n "$DATE_FILTER" ]; then
    echo "Date filter: $DATE_FILTER"
else
    echo "Processing ALL records (no date filter)"
fi

# Use gradle run to handle classpath properly
# Set system property for external config file
if [ -n "$DATE_FILTER" ]; then
    ./gradlew run --args="$SOLR_URL $DATE_FILTER" -Dconfig.file=/data/properties/database.properties
else
    ./gradlew run --args="$SOLR_URL" -Dconfig.file=/data/properties/database.properties
fi

echo "Simple transfer completed"