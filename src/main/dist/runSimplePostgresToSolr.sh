#!/bin/bash

# Simple script to transfer data from PostgreSQL to Solr (no AI processing)
# Usage: ./runSimplePostgresToSolr.sh [solr_url] [date_filter]

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

SOLR_URL=${1:-""}
DATE_FILTER=${2}

echo "Simple PostgreSQL to Solr transfer"
if [ -n "$SOLR_URL" ]; then
    echo "Solr URL: $SOLR_URL"
else
    echo "Solr URL: (from database.properties)"
fi
if [ -n "$DATE_FILTER" ]; then
    echo "Date filter: $DATE_FILTER"
else
    echo "Processing ALL records (no date filter)"
fi

# Run the main class with proper classpath (empty solr_url falls back to properties file)
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.SimplePostgresToSolr "$SOLR_URL" "$DATE_FILTER"

echo "Simple transfer completed"