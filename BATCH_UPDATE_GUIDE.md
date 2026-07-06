# Batch Update Guide for FlexibleFieldUpdater

## Overview

The FlexibleFieldUpdater now supports three modes of operation:
1. **Single record update** - Update one PMID
2. **Year-based batch update** - Update all records from a specific year
3. **Full database update** - Update all records in the database

All modes support calculating gene positions from title and abstract text.

## Usage

### 1. Single Record Update

Update a single PMID:

```bash
./updateFields.sh <pmid> <solr_host> <solr_core> <fields>
```

**Example:**
```bash
./updateFields.sh 39022130 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

### 2. Update by Year

Update all records from a specific year:

```bash
./updateFields.sh --year <year> <solr_host> <solr_core> <fields>
```

**Examples:**
```bash
# Update all 2024 records
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count

# Update first 100 records from 2024
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 100
```

### 3. Update All Records

Update all records in the database:

```bash
./updateFields.sh --all <solr_host> <solr_core> <fields>
```

**Examples:**
```bash
# Update all records (use with caution!)
./updateFields.sh --all dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count

# Update first 1000 records
./updateFields.sh --all dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 1000
```

## Optional Parameters

### --limit N

Limits the number of records to process. Useful for testing:

```bash
# Test with 10 records from 2024
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 10
```

## Available Fields

### Gene Fields
- `gene` - Gene names
- `gene_pos` - Gene positions in title and abstract (calculated automatically)
- `gene_count` - Number of occurrences (calculated automatically)

### Basic Fields
- `title`, `abstract`, `p_date`, `p_year`, `authors`, `keywords`

### Ontology Fields
- **IDs**: `mp_id`, `bp_id`, `vt_id`, `chebi_id`, `rs_id`, `rdo_id`, `nbo_id`, `xco_id`, `so_id`, `hp_id`
- **Terms**: `mp_term`, `bp_term`, `vt_term`, etc.
- **Positions**: `mp_pos`, `bp_pos`, `vt_pos`, etc.
- **Counts**: `mp_count`, `bp_count`, `vt_count`, etc.

### Organism Fields
- `organism_common_name`, `organism_term`, `organism_ncbi_id`, `organism_pos`, `organism_count`

## Features

### Automatic Position Calculation

When you update `gene_pos`, the tool:
1. Searches for each gene name in the title (section 0) and abstract (section 1)
2. Records all occurrences with 0-indexed character positions
3. Handles HTML tags in the abstract (`<ns1:i>`, etc.)
4. Normalizes whitespace to match browse interface display
5. Automatically updates `gene_count` with the actual number found

**Position Format:** `section;start-end|start-end|...`
- Section 0 = title
- Section 1 = abstract
- Start = 0-indexed character position (inclusive)
- End = 0-indexed character position (exclusive, one past last char)

**Example:**
```
gene_pos: ["0;11-16|1;333-338|1;470-475|1;548-553|1;734-739|1;836-841|1;1086-1091|1;1262-1267|1;1311-1316", "0;0-0"]
gene_count: [9, 0]
```

### Atomic Updates

The tool uses Solr atomic updates to preserve fields that aren't being updated. Only the specified fields are modified.

### Batch Processing

For batch updates (--year or --all):
- Progress is reported every 100 records
- Errors are counted but don't stop the batch
- Summary statistics are displayed at completion
- Uses quiet mode (minimal output per record)

## Performance Considerations

### Testing First

Always test with --limit before running on full dataset:

```bash
# Test on 10 records first
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 10
```

### Database Load

- Each record requires a database query and Solr update
- Full database updates can take hours
- Consider running during off-peak hours

### Incremental Updates

Process by year instead of all-at-once:

```bash
# Process one year at a time
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
./updateFields.sh --year 2023 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

## Monitoring

### Progress Output

Batch updates show progress every 100 records:
```
2025.10.28 at 15:30:00 Processed 100 records...
2025.10.28 at 15:32:15 Processed 200 records...
```

### Completion Summary

At the end, you'll see:
```
2025.10.28 at 16:45:30 Completed!
Total records processed: 1543
Errors: 2
```

### Error Handling

Errors are logged to stderr but don't stop processing:
```
Error updating PMID 12345678: <error message>
```

## Examples

### Update 2024 Gene Positions

```bash
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

### Test Update on Recent Records

```bash
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 50
```

### Update Different Core

```bash
./updateFields.sh --year 2024 dev.rgd.mcw.edu:8983 OntoMate gene,gene_pos,gene_count
```

### Update Multiple Field Types

```bash
./updateFields.sh 39022130 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count,mp_pos,bp_pos
```

## Troubleshooting

### Build Issues

If you get "Classes not found":
```bash
./gradlew build
```

### Database Connection

Ensure database credentials are in `src/main/resources/database.properties`

### Solr Connection

Verify Solr is accessible:
```bash
curl "http://dev.rgd.mcw.edu:8983/solr/ai1/admin/ping"
```

### Memory Issues

For very large batches, you may need to increase Java heap:
```bash
export GRADLE_OPTS="-Xmx4g"
./updateFields.sh --all ...
```
