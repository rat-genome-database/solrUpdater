#!/bin/bash

# FlexibleFieldUpdater - Update specific Solr fields for PubMed records
#
# Usage:
#   Single record:  ./updateFields.sh <pmid> <solr_host> <solr_core> <fields>
#   All records:    ./updateFields.sh --all <solr_host> <solr_core> <fields> [--limit N]
#   By year:        ./updateFields.sh --year <year> <solr_host> <solr_core> <fields> [--limit N]
#
# Pass an empty string ("") for <solr_host> or <solr_core> to inherit the value
# from database.properties (solr.default.url).
#
# Examples:
#   ./updateFields.sh 39022130 garak.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
#   ./updateFields.sh 39022130 "" "" gene,gene_pos,gene_count   # host+core from properties
#   ./updateFields.sh --year 2024 garak.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
#   ./updateFields.sh --year 2024 "" "" gene,gene_pos,gene_count --limit 100
#   ./updateFields.sh --all "" "" gene,gene_pos,gene_count --limit 1000

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up classpath with all jars in lib directory
CLASSPATH="$SCRIPT_DIR/lib/*"

# Run the FlexibleFieldUpdater class with proper classpath
java -cp "$CLASSPATH" -Dconfig.file=/data/properties/database.properties edu.mcw.rgd.nlp.FlexibleFieldUpdater "$@"
