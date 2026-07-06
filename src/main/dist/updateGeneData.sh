#!/bin/bash

# Gene-only updater script for efficient atomic updates
# Usage: ./updateGeneData.sh <pmid> [solr_url]

set -e  # Exit on error

PMID=$1
SOLR_URL=${2:-""}

if [ -z "$PMID" ]; then
    echo "Usage: $0 <pmid> [solr_url]"
    echo "Example: $0 39726234 http://garak.rgd.mcw.edu:8983/solr/ai1"
    echo "(If solr_url is omitted, the value from database.properties is used.)"
    exit 1
fi

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

# Fall back to database.properties for SOLR_URL if not provided
if [ -z "$SOLR_URL" ]; then
    PROP_FILE="${CONFIG_FILE:-/data/properties/database.properties}"
    if [ ! -f "$PROP_FILE" ]; then
        PROP_FILE="$SCRIPT_DIR/database.properties"
    fi
    if [ -f "$PROP_FILE" ]; then
        SOLR_URL=$(grep '^solr.default.url=' "$PROP_FILE" | head -1 | cut -d'=' -f2-)
    fi
    if [ -z "$SOLR_URL" ]; then
        echo "Error: No solr_url provided and could not read solr.default.url from $PROP_FILE"
        exit 1
    fi
fi

echo "Gene-only atomic update for PMID: $PMID"
echo "Solr URL: $SOLR_URL"

echo "Getting gene data from database..."

# Use existing SingleRecordUpdater to extract gene data from database
TEMP_OUTPUT="/tmp/gene_extract_${PMID}.txt"

# Run the updater but capture just the JSON part for gene fields
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties \
    edu.mcw.rgd.nlp.SingleRecordUpdater "$PMID" "$SOLR_URL" > "$TEMP_OUTPUT" 2>&1

if [ $? -ne 0 ]; then
    echo "Error: Failed to get gene data from database for PMID $PMID"
    cat "$TEMP_OUTPUT"
    rm -f "$TEMP_OUTPUT"
    exit 1
fi

# Extract gene fields from the JSON output
GENE_JSON=$(grep -A 1000 "========== JSON BEING SENT TO SOLR ==========" "$TEMP_OUTPUT" | \
           grep -B 1000 "========== END JSON ==========" | \
           head -n -1 | tail -n +2)

if [ -z "$GENE_JSON" ]; then
    echo "Error: Could not extract JSON from database query"
    rm -f "$TEMP_OUTPUT"
    exit 1
fi

# Parse out just the gene-related fields
GENE_FIELD=$(echo "$GENE_JSON" | grep '"gene":' | head -1)
GENE_COUNT_FIELD=$(echo "$GENE_JSON" | grep '"gene_count":' | head -1)
GENE_POS_FIELD=$(echo "$GENE_JSON" | grep '"gene_pos":' | head -1)
GENE_S_FIELD=$(echo "$GENE_JSON" | grep '"gene_s":' | head -1)

# Clean up temp file
rm -f "$TEMP_OUTPUT"

if [ -z "$GENE_FIELD" ]; then
    echo "Warning: No gene data found for PMID $PMID"
    echo "Skipping update."
    exit 0
fi

echo "Extracted gene data successfully"

# Create atomic update JSON by extracting values
GENE_VALUES=""
GENE_COUNT_VALUES=""
GENE_POS_VALUES=""
GENE_S_VALUES=""

if [ -n "$GENE_FIELD" ]; then
    GENE_VALUES=$(echo "$GENE_FIELD" | sed 's/.*"gene": *\([^,}]*\).*/\1/' | sed 's/,$//')
fi

if [ -n "$GENE_COUNT_FIELD" ]; then
    GENE_COUNT_VALUES=$(echo "$GENE_COUNT_FIELD" | sed 's/.*"gene_count": *\([^,}]*\).*/\1/' | sed 's/,$//')
fi

if [ -n "$GENE_POS_FIELD" ]; then
    GENE_POS_VALUES=$(echo "$GENE_POS_FIELD" | sed 's/.*"gene_pos": *\([^,}]*\).*/\1/' | sed 's/,$//' | sed 's/"$//')
fi

if [ -n "$GENE_S_FIELD" ]; then
    GENE_S_VALUES=$(echo "$GENE_S_FIELD" | sed 's/.*"gene_s": *\([^,}]*\).*/\1/' | sed 's/,$//')
fi

# Build atomic update JSON
ATOMIC_UPDATE="{"
ATOMIC_UPDATE="${ATOMIC_UPDATE}\"pmid\":\"$PMID\""

if [ -n "$GENE_VALUES" ]; then
    ATOMIC_UPDATE="${ATOMIC_UPDATE},\"gene\":{\"set\":$GENE_VALUES}"
fi

if [ -n "$GENE_COUNT_VALUES" ]; then
    ATOMIC_UPDATE="${ATOMIC_UPDATE},\"gene_count\":{\"set\":$GENE_COUNT_VALUES}"
fi

if [ -n "$GENE_POS_VALUES" ] && [ "$GENE_POS_VALUES" != '""' ]; then
    ATOMIC_UPDATE="${ATOMIC_UPDATE},\"gene_pos\":{\"set\":\"$GENE_POS_VALUES\"}"
fi

if [ -n "$GENE_S_VALUES" ]; then
    ATOMIC_UPDATE="${ATOMIC_UPDATE},\"gene_s\":{\"set\":$GENE_S_VALUES}"
fi

ATOMIC_UPDATE="${ATOMIC_UPDATE}}"

echo ""
echo "========== ATOMIC UPDATE JSON =========="
echo "[$ATOMIC_UPDATE]"
echo "========== END JSON =========="
echo ""

# Perform the atomic update
echo "Sending atomic update to Solr..."
RESPONSE=$(curl -s -X POST "${SOLR_URL}/update?commit=true" \
    -H "Content-Type: application/json" \
    --data-binary "[$ATOMIC_UPDATE]")

# Check if update was successful
if echo "$RESPONSE" | grep -q '"status":0'; then
    echo "✅ Successfully updated gene information for PMID $PMID"
else
    echo "❌ Error updating gene information:"
    echo "$RESPONSE"
    exit 1
fi

echo "Gene update completed!"