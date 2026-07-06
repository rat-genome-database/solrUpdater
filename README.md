# PostgreSQL to Solr Data Transfer Tool

A Java-based tool for transferring scientific literature data from a PostgreSQL database to Apache Solr for text mining and search applications.

## Features

- **Bulk data transfer** with chunked processing for large datasets
- **Single record updates** by PMID
- **Comprehensive field mapping** including ontology terms, genes, and position data
- **Pipe-separated value handling** for multi-valued Solr fields
- **JSON debugging output** to view documents being sent to Solr
- **Configurable database connections** via properties file

## Setup

### 1. Database Configuration

#### For Development (Local)
Copy the example configuration file:
```bash
cp database.properties.example src/main/resources/database.properties
```

Edit `src/main/resources/database.properties` with your database credentials.

#### For Server Deployment
The application will automatically look for configuration at `/data/properties/database.properties` on the server. Create this file with your production settings:

```bash
# On the server
sudo mkdir -p /data/properties/
sudo cp database.properties.example /data/properties/database.properties
sudo chown your-user:your-group /data/properties/database.properties
sudo chmod 600 /data/properties/database.properties  # Secure permissions
```

Edit `/data/properties/database.properties` with your production database credentials:
```properties
db.url=jdbc:postgresql://your-production-host:5432/rgdsolr
db.username=your_production_username
db.password=your_production_password
solr.default.url=http://your-solr-host:8983/solr/your-core
```

#### Configuration Priority
The application loads configuration in this order:
1. `/data/properties/database.properties` (server)
2. `src/main/resources/database.properties` (fallback)

You can also specify a custom config location:
```bash
./gradlew run --args="your-solr-url" -Dconfig.file=/custom/path/database.properties
```

### 2. Build the Project

```bash
./gradlew build
```

## Usage

### Single Record Update
Update a specific record by PMID:
```bash
./scripts/updateSingleRecord.sh <pmid> [solr_url]
```

Examples:
```bash
./scripts/updateSingleRecord.sh 12417054
./scripts/updateSingleRecord.sh 12417054 "http://dev.rgd.mcw.edu:8983/solr/ai1"
```

### Bulk Transfer
Transfer multiple records:
```bash
./scripts/runSimplePostgresToSolr.sh <solr_url> [options]
```

Examples:
```bash
# Transfer first 1000 records (default)
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1"

# Transfer specific number of records
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1" "LIMIT 100"

# Transfer entire database in chunks (recommended for large datasets)
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1" "CHUNKS"

# Transfer by date range
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1" "2020-01-01,2020-12-31"

# Transfer single day
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1" "2020-01-01"

# Transfer records updated after a specific date
./scripts/runSimplePostgresToSolr.sh "http://dev.rgd.mcw.edu:8983/solr/ai1" "UPDATED_AFTER 2024-01-01"
```

### Transfer Single Year
Transfer all records from a specific year:
```bash
./scripts/transferYear.sh <year> [solr_url]
```

Examples:
```bash
./scripts/transferYear.sh 2020
./scripts/transferYear.sh 2022 "http://dev.rgd.mcw.edu:8983/solr/ai1"
```

### Query Database Records
View a record from the database:
```bash
./gradlew queryRecord -PappArgs="<pmid>"
```

Example:
```bash
./gradlew queryRecord -PappArgs="12417054"
```

## Field Mapping

The tool maps comprehensive database fields to Solr, including:

- **Basic fields**: pmid, title, abstract, authors, keywords, mesh_terms, etc.
- **Gene data**: gene names and positions
- **Ontology terms**: MP, BP, CHEBI, RDO, XCO, SO, HP terms and IDs
- **Position data**: Exact text positions where terms were found
- **Publication metadata**: DOI, citation, chemicals, publication type, etc.

## Database Connection Options

### SSH Tunnel
For secure connections, set up an SSH tunnel:
```bash
ssh -L 5432:database-host:5432 user@jump-server
```

Then configure:
```properties
db.url=jdbc:postgresql://localhost:5432/rgdsolr
```

### Direct Connection
For direct database access:
```properties
db.url=jdbc:postgresql://your-db-host:5432/rgdsolr
```

## JSON Debug Output

All tools display the JSON being sent to Solr for debugging purposes. Multi-valued fields are properly formatted as JSON arrays.

## Architecture

- **SimplePostgresToSolr**: Main bulk transfer class with chunked processing
- **SingleRecordUpdater**: Single record update functionality
- **QueryRecord**: Database query utility
- **DatabaseConfig**: Configuration management from properties file

## Dependencies

- PostgreSQL JDBC Driver
- Apache Solr Java Client (SolrJ)
- Java 17+
- Gradle build system

