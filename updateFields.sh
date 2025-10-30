#!/bin/bash

# FlexibleFieldUpdater - Update specific Solr fields for PubMed records
#
# Usage:
#   Single record:  ./updateFields.sh <pmid> <solr_host> <solr_core> <fields>
#   All records:    ./updateFields.sh --all <solr_host> <solr_core> <fields> [--limit N]
#   By year:        ./updateFields.sh --year <year> <solr_host> <solr_core> <fields> [--limit N]
#
# Examples:
#   ./updateFields.sh 39022130 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
#   ./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
#   ./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 100
#   ./updateFields.sh --all dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 1000

# Get the directory where this script is located
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check if compiled
if [ ! -d "$DIR/build/classes/java/main" ]; then
    echo "Classes not found, please build the project first with: ./gradlew build"
    exit 1
fi

# Use Gradle to run with proper classpath
cd "$DIR"
./gradlew -q runFlexibleUpdater -PappArgs="$*"
