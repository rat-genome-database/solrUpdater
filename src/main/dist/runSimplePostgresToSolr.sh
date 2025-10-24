#!/bin/bash

# Simple script to transfer data from PostgreSQL to Solr (no AI processing)
# Usage: ./runSimplePostgresToSolr.sh [solr_url] [date_filter]

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

SOLR_URL=${1:-"http://dev.rgd.mcw.edu:8983/solr/OntoMate"}
DATE_FILTER=${2}

echo "Simple PostgreSQL to Solr transfer"
echo "Solr URL: $SOLR_URL"
if [ -n "$DATE_FILTER" ]; then
    echo "Date filter: $DATE_FILTER"
else
    echo "Processing ALL records (no date filter)"
fi

# Run the main class with proper classpath
if [ -n "$DATE_FILTER" ]; then
    java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.SimplePostgresToSolr "$SOLR_URL" "$DATE_FILTER"
else
    java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.SimplePostgresToSolr "$SOLR_URL"
fi

echo "Simple transfer completed"