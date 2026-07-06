# Flexible Solr Field Updater

A flexible utility for updating specific fields in Solr documents without having to update all fields.

## Features

- Update only the fields you specify
- Specify custom Solr host and core
- Update single or multiple fields at once
- Handles all data types (text, dates, multi-valued fields, counts)
- Automatic field sanitization where needed

## Usage

### Command Line Syntax

```bash
java -cp build/classes:lib/* edu.mcw.rgd.nlp.FlexibleFieldUpdater <pmid> <solr_host> <solr_core> <field1,field2,field3,...>
```

Or use the shell script:

```bash
./updateFields.sh <pmid> <solr_host> <solr_core> <field1,field2,field3,...>
```

### Parameters

1. **pmid**: The PubMed ID of the document to update
2. **solr_host**: Solr host with port (e.g., `dev.rgd.mcw.edu:8983`)
3. **solr_core**: Solr core name (e.g., `ai1`)
4. **fields**: Comma-separated list of field names (no spaces)

## Examples

### Update gene fields only

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
```

### Update ontology terms

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 mp_id,mp_term,mp_pos,mp_count
```

### Update basic publication info

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 title,abstract,keywords
```

### Update multiple ontology types

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 mp_id,mp_term,bp_id,bp_term,vt_id,vt_term
```

### Update organism information

```bash
./updateFields.sh 39433266 dev.rgd.mcw.edu:8983 ai1 organism_common_name,organism_term,organism_ncbi_id,organism_pos,organism_count
```

## Available Fields

### Basic Fields
- `title` - Article title
- `abstract` - Article abstract
- `p_date` - Publication date
- `p_year` - Publication year
- `authors` - Author list
- `keywords` - Keywords
- `mesh_terms` - MeSH terms
- `affiliation` - Author affiliation
- `citation` - Citation information
- `chemicals` - Chemical compounds
- `issn` - Journal ISSN
- `doi_s` - DOI
- `pmc_id` - PubMed Central ID
- `p_type` - Publication type
- `p_source` - Publication source

### Gene Fields
- `gene` - Gene symbols
- `gene_pos` - Gene positions in text
- `gene_count` - Number of gene mentions

### Ontology ID Fields
- `mp_id` - Mammalian Phenotype IDs
- `bp_id` - Biological Process (GO) IDs
- `vt_id` - Vertebrate Trait IDs
- `chebi_id` - Chemical Entities IDs
- `rs_id` - Rat Strain IDs
- `rdo_id` - RGD Disease Ontology IDs
- `nbo_id` - Neurobehavior Ontology IDs
- `xco_id` - Experimental Conditions IDs
- `so_id` - Sequence Ontology IDs
- `hp_id` - Human Phenotype IDs
- `zfa_id` - Zebrafish Anatomy IDs
- `cmo_id` - Clinical Measurement Ontology IDs
- `ma_id` - Mouse Anatomy IDs
- `pw_id` - Pathway Ontology IDs
- `go_id` - Gene Ontology IDs

### Ontology Term Fields
Replace `_id` with `_term` for any of the above (e.g., `mp_term`, `bp_term`, etc.)

### Position Fields
Replace `_id` with `_pos` for any of the above (e.g., `mp_pos`, `bp_pos`, etc.)

### Count Fields
Replace `_id` with `_count` for any of the above (e.g., `mp_count`, `bp_count`, etc.)

### Organism Fields
- `organism_common_name` - Common name (e.g., "rat")
- `organism_term` - Scientific name
- `organism_ncbi_id` - NCBI Taxonomy ID
- `organism_pos` - Position in text
- `organism_count` - Number of mentions

### Special Fields
- `text` - Searchable text field (aggregates title, abstract, keywords, etc.)
- `mt_term` - Derived mesh terms (split from mesh_terms field)

## Building

Before first use, compile the Java classes:

```bash
./gradlew build
```

Or if using Gradle directly:

```bash
gradle build
```

## Notes

- The script automatically handles different field types (text, dates, integers, multi-valued fields)
- Multi-valued fields (ending in `_id`, `_term`, `_pos`) are automatically split on " | " delimiter
- Count fields are parsed as integers
- Text fields are automatically sanitized to fix encoding issues
- The PMID field is always included as the document identifier

## Troubleshooting

### Classes not found
If you get a "ClassNotFoundException", make sure to build first:
```bash
./gradlew build
```

### Field not found in database
If a field you specify doesn't exist in the database, you'll see a warning but the update will continue with other fields.

### No fields updated
Make sure your field names are spelled correctly and exist in both the database `solr_docs` table and your Solr schema.
