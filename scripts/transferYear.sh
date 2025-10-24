#!/bin/bash

# Script to transfer all records from a specific year to Solr
# Usage: ./transferYear.sh <year> [solr_url]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <year> [solr_url]"
    echo "Example: $0 2020"
    echo "Example: $0 2020 http://dev.rgd.mcw.edu:8983/solr/ai1"
    exit 1
fi

YEAR=$1
SOLR_URL=${2:-"http://dev.rgd.mcw.edu:8983/solr/OntoMate"}

# Validate year format (4 digits)
if ! [[ "$YEAR" =~ ^[0-9]{4}$ ]]; then
    echo "Error: Year must be a 4-digit number (e.g., 2020)"
    exit 1
fi

# Create date range for the entire year
START_DATE="${YEAR}-01-01"
END_DATE="$((YEAR + 1))-01-01"

echo "Year Transfer to Solr"
echo "Year: $YEAR"
echo "Date Range: $START_DATE to $END_DATE (exclusive)"
echo "Solr URL: $SOLR_URL"
echo ""

# Use the existing bulk transfer script with date range
./scripts/runSimplePostgresToSolr.sh "$SOLR_URL" "$START_DATE,$END_DATE"

echo "Year $YEAR transfer completed"