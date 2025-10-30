# FlexibleFieldUpdater Usage Examples

## Quick Start

The FlexibleFieldUpdater allows you to update specific fields in a Solr document without updating all fields.

## Usage Methods

### Method 1: Shell Script (Recommended)

```bash
./updateFields.sh <pmid> <solr_host> <solr_core> <fields>
```

### Method 2: Gradle Task

```bash
gradle runFlexibleUpdater -PappArgs="<pmid> <solr_host> <solr_core> <fields>"
```

### Method 3: Direct Java Execution

```bash
java -cp build/classes/java/main:lib/* edu.mcw.rgd.nlp.FlexibleFieldUpdater <pmid> <solr_host> <solr_core> <fields>
```

## Real-World Examples

### Example 1: Update Gene Information Only

Update just the gene-related fields for a specific PMID:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

This will update:
- `gene` - Gene symbols found in the text
- `gene_pos` - Position of gene mentions in the document
- `gene_count` - Number of gene mentions

### Example 2: Update Multiple Ontology Types

Update several ontology fields at once:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 mp_id,mp_term,mp_pos,mp_count,bp_id,bp_term,bp_pos,bp_count
```

This updates both Mammalian Phenotype (MP) and Biological Process (BP) fields.

### Example 3: Update Publication Metadata

Update basic publication information:

```bash
./updateFields.sh 12345678 dev.rgd.mcw.edu:8983 ai1 title,abstract,keywords,authors,p_date,p_year
```

### Example 4: Update Organism Information

Update all organism-related fields:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 organism_common_name,organism_term,organism_ncbi_id,organism_pos,organism_count
```

### Example 5: Update Disease Ontology Terms

Update RGD Disease Ontology annotations:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 rdo_id,rdo_term,rdo_pos,rdo_count
```

### Example 6: Comprehensive Ontology Update

Update all ontology-related fields for a document:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 \
  mp_id,mp_term,mp_pos,mp_count,\
  bp_id,bp_term,bp_pos,bp_count,\
  vt_id,vt_term,vt_pos,vt_count,\
  chebi_id,chebi_term,chebi_pos,chebi_count,\
  rs_id,rs_term,rs_pos,rs_count
```

### Example 7: Different Solr Instances

#### Update on production server:
```bash
./updateFields.sh 39433266 production.example.com:8983 publications gene,gene_pos,gene_count
```

#### Update on dev server:
```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

#### Update on localhost:
```bash
./updateFields.sh 39433266 localhost:8983 my_core gene,gene_pos,gene_count
```

## Common Use Cases

### Use Case 1: After Re-running Gene Annotation

After re-annotating documents with updated gene information:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

### Use Case 2: After Ontology Updates

When ontology terms have been updated in the database:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 mp_id,mp_term,bp_id,bp_term,vt_id,vt_term
```

### Use Case 3: Fix Text Encoding Issues

When abstracts or titles have encoding problems:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 title,abstract
```

### Use Case 4: Update Search Index

When you need to rebuild the searchable text field:

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 text
```

## Field Categories Quick Reference

### Gene Fields
```
gene,gene_pos,gene_count
```

### Phenotype Fields (MP)
```
mp_id,mp_term,mp_pos,mp_count
```

### Biological Process Fields (BP/GO)
```
bp_id,bp_term,bp_pos,bp_count,go_id,go_term,go_count
```

### Disease Fields (RDO)
```
rdo_id,rdo_term,rdo_pos,rdo_count
```

### Chemical Fields (ChEBI)
```
chebi_id,chebi_term,chebi_pos,chebi_count
```

### Strain Fields (RS)
```
rs_id,rs_term,rs_pos,rs_count
```

### All Ontology IDs
```
mp_id,bp_id,vt_id,chebi_id,rs_id,rdo_id,nbo_id,xco_id,so_id,hp_id,zfa_id,cmo_id,ma_id,pw_id
```

### All Ontology Terms
```
mp_term,bp_term,vt_term,chebi_term,rs_term,rdo_term,nbo_term,xco_term,so_term,hp_term,zfa_term,cmo_term,ma_term,pw_term
```

## Tips

1. **No spaces in field list**: Use `gene,gene_pos` not `gene, gene_pos`
2. **Case sensitive**: Field names must match exactly (usually lowercase)
3. **Validate PMID**: Make sure the PMID exists in your database before updating
4. **Check Solr schema**: Ensure fields exist in your Solr core schema
5. **Test first**: Try with a test PMID before bulk updates

## Troubleshooting

### "Field not found in database"
The field doesn't exist in the `solr_docs` table. Check your database schema.

### "PMID not found in database"
The PMID doesn't exist in your database. Verify the PMID is correct.

### "ClassNotFoundException"
Run `./gradlew build` first to compile the classes.

### "Connection refused"
Check that:
- The Solr host is correct
- The port is accessible
- The Solr core name is correct

## Performance Notes

- Single field updates are fast (< 1 second typically)
- Updating many fields for one document is still fast
- For bulk updates of many PMIDs, consider writing a loop script
- The tool commits changes immediately after update

## Batch Processing

To update multiple PMIDs with the same fields:

```bash
#!/bin/bash
FIELDS="gene,gene_pos,gene_count"
HOST="dev.rgd.mcw.edu:8983"
CORE="ai1"

for pmid in 39433266 39433267 39433268; do
    ./updateFields.sh $pmid $HOST $CORE $FIELDS
done
```
