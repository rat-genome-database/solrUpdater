package edu.mcw.rgd.nlp;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class SimplePostgresToSolr {

    private String solrUrl;
    private SolrServer solrServer;
    private List<SolrInputDocument> documentBatch;
    private static final int BATCH_SIZE = DatabaseConfig.getBatchSize();

    public SimplePostgresToSolr(String solrUrl) {
        this.solrUrl = solrUrl != null ? solrUrl : DatabaseConfig.getDefaultSolrUrl();
        this.solrServer = new HttpSolrServer(this.solrUrl);
        this.documentBatch = new ArrayList<>();
    }

    public void transferData(String dateFilter) {
        System.out.println("Starting PostgreSQL to Solr transfer...");
        System.out.println("Solr URL: " + solrUrl);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            // Create direct PostgreSQL connection
            System.out.println("Connecting to database: " + DatabaseConfig.getDbUrl());
            Connection conn = DriverManager.getConnection(DatabaseConfig.getDbUrl(), DatabaseConfig.getDbUsername(), DatabaseConfig.getDbPassword());

            // Check if we should process in chunks
            if (dateFilter != null && dateFilter.toUpperCase().equals("CHUNKS")) {
                processInChunks(conn, sdf);
                return;
            }

            String query;
            if (dateFilter != null && !dateFilter.isEmpty()) {
                // Check if it's a date range (contains comma) or limit (starts with LIMIT)
                if (dateFilter.toUpperCase().startsWith("LIMIT")) {
                    // Parse the number from "LIMIT 100" format
                    String[] parts = dateFilter.split(" ");
                    if (parts.length > 1) {
                        query = "SELECT * FROM solr_docs LIMIT " + parts[1];
                    } else {
                        query = "SELECT * FROM solr_docs LIMIT 100";
                    }
                    System.out.println("Using custom limit: " + dateFilter);
                } else if (dateFilter.contains(",")) {
                    // Date range format: "2016-10-01,2016-10-31"
                    String[] dates = dateFilter.split(",");
                    query = "SELECT * FROM solr_docs WHERE p_date >= DATE '" + dates[0] + "' AND p_date < DATE '" + dates[1] + "'";
                    System.out.println("Date range: " + dates[0] + " to " + dates[1]);
                } else {
                    query = "SELECT * FROM solr_docs WHERE p_date >= DATE '" + dateFilter + "' AND p_date < DATE '" + dateFilter + "' + INTERVAL '1 day'";
                    System.out.println("Date filter: " + dateFilter);
                }
            } else {
                query = "SELECT * FROM solr_docs LIMIT 1000";
                System.out.println("Processing first 1000 records (use date filter or LIMIT parameter for more)");
            }
            System.out.println("Executing query: " + query);

            // Use cursor-based fetching for large result sets
            conn.setAutoCommit(false);
            Statement s = conn.createStatement();
            s.setFetchSize(100);  // Fetch 100 rows at a time
            ResultSet rs = s.executeQuery(query);

            int count = 0;
            while (rs.next()) {
                String pmid = rs.getString("pmid");
                System.out.println(sdf.format(new Date()) + " processing " + pmid + " (" + (count + 1) + ")");

                // Create Solr document from database record
                SolrInputDocument doc = createSolrDocument(rs);

                if (doc != null) {
                    // Add document to batch
                    documentBatch.add(doc);

                    // Send batch if it reaches the size limit
                    if (documentBatch.size() >= BATCH_SIZE) {
                        sendBatchToSolr();
                    }
                }

                count++;
            }

            // Send any remaining documents
            if (!documentBatch.isEmpty()) {
                sendBatchToSolr();
            }

            // Final commit
            solrServer.commit();
            System.out.println("Successfully transferred " + count + " documents to Solr");

            conn.commit();  // Commit the transaction
            conn.close();

        } catch (Exception e) {
            System.err.println("Error during transfer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a SolrInputDocument from a database ResultSet
     */
    private SolrInputDocument createSolrDocument(ResultSet rs) throws SQLException {
        SolrInputDocument doc = new SolrInputDocument();

        try {
            // Map database columns to Solr fields
            if (rs.getString("pmid") != null) {
                doc.addField("pmid", rs.getString("pmid"));
                // Note: Not adding 'id' field since ai1 core doesn't have it defined
            }

            if (rs.getString("title") != null) {
                doc.addField("title", rs.getString("title"));
            }

            // Handle abstract field (could be CLOB or TEXT)
            try {
                String abstractText = rs.getString("abstract");
                if (abstractText != null && !abstractText.trim().isEmpty()) {
                    doc.addField("abstract", abstractText);
                }
            } catch (SQLException e) {
                // Try as CLOB if getString fails
                try {
                    Clob abstractClob = rs.getClob("abstract");
                    if (abstractClob != null) {
                        String abstractText = abstractClob.getSubString(1, (int) abstractClob.length());
                        doc.addField("abstract", abstractText);
                    }
                } catch (SQLException e2) {
                    // Skip abstract field if both methods fail
                    System.err.println("Warning: Could not read abstract for PMID: " + rs.getString("pmid"));
                }
            }

            if (rs.getDate("p_date") != null) {
                // Convert to Solr date format (ISO 8601)
                doc.addField("p_date", rs.getDate("p_date").toString() + "T06:00:00Z");
            }

            // Add basic publication fields
            addFieldIfExists(doc, rs, "authors");
            addFieldIfExists(doc, rs, "keywords");
            addFieldIfExists(doc, rs, "mesh_terms");
            addFieldIfExists(doc, rs, "affiliation");
            addFieldIfExists(doc, rs, "issn");
            addFieldIfExists(doc, rs, "p_year");
            addFieldIfExists(doc, rs, "p_type");
            addFieldIfExists(doc, rs, "doi_s");
            addFieldIfExists(doc, rs, "citation");
            addFieldIfExists(doc, rs, "chemicals");
            addFieldIfExists(doc, rs, "j_date_s");
            addFieldIfExists(doc, rs, "pmc_id");

            // Add organism fields
            addFieldIfExists(doc, rs, "organism_common_name");
            addFieldIfExists(doc, rs, "organism_term");
            addFieldIfExists(doc, rs, "organism_ncbi_id");

            // Add gene fields
            addFieldIfExists(doc, rs, "gene");

            // Add ontology term fields (IDs)
            addFieldIfExists(doc, rs, "mp_id");
            addFieldIfExists(doc, rs, "bp_id");
            addFieldIfExists(doc, rs, "vt_id");
            addFieldIfExists(doc, rs, "chebi_id");
            addFieldIfExists(doc, rs, "rs_id");
            addFieldIfExists(doc, rs, "rdo_id");
            addFieldIfExists(doc, rs, "nbo_id");
            addFieldIfExists(doc, rs, "xco_id");
            addFieldIfExists(doc, rs, "so_id");
            addFieldIfExists(doc, rs, "hp_id");
            addFieldIfExists(doc, rs, "xdb_id");
            addFieldIfExists(doc, rs, "rgd_obj_id");

            // Add ontology term fields (terms)
            addFieldIfExists(doc, rs, "mp_term");
            addFieldIfExists(doc, rs, "bp_term");
            addFieldIfExists(doc, rs, "vt_term");
            addFieldIfExists(doc, rs, "chebi_term");
            addFieldIfExists(doc, rs, "rs_term");
            addFieldIfExists(doc, rs, "rdo_term");
            addFieldIfExists(doc, rs, "nbo_term");
            addFieldIfExists(doc, rs, "xco_term");
            addFieldIfExists(doc, rs, "so_term");
            addFieldIfExists(doc, rs, "hp_term");
            addFieldIfExists(doc, rs, "rgd_obj_term");

            // Add position fields
            addFieldIfExists(doc, rs, "gene_pos");
            addFieldIfExists(doc, rs, "mp_pos");
            addFieldIfExists(doc, rs, "bp_pos");
            addFieldIfExists(doc, rs, "vt_pos");
            addFieldIfExists(doc, rs, "chebi_pos");
            addFieldIfExists(doc, rs, "rs_pos");
            addFieldIfExists(doc, rs, "rdo_pos");
            addFieldIfExists(doc, rs, "nbo_pos");
            addFieldIfExists(doc, rs, "xco_pos");
            addFieldIfExists(doc, rs, "so_pos");
            addFieldIfExists(doc, rs, "hp_pos");
            addFieldIfExists(doc, rs, "rgd_obj_pos");
            addFieldIfExists(doc, rs, "organism_pos");

            // Add count fields (required for QueryBuilder interface)
            addFieldIfExists(doc, rs, "gene_count");
            addFieldIfExists(doc, rs, "mp_count");
            addFieldIfExists(doc, rs, "bp_count");
            addFieldIfExists(doc, rs, "vt_count");
            addFieldIfExists(doc, rs, "chebi_count");
            addFieldIfExists(doc, rs, "rs_count");
            addFieldIfExists(doc, rs, "rdo_count");
            addFieldIfExists(doc, rs, "nbo_count");
            addFieldIfExists(doc, rs, "xco_count");
            addFieldIfExists(doc, rs, "so_count");
            addFieldIfExists(doc, rs, "hp_count");
            addFieldIfExists(doc, rs, "rgd_obj_count");
            addFieldIfExists(doc, rs, "go_count");

            // Add source field
            doc.addField("p_source", "pubmed");

            return doc;

        } catch (SQLException e) {
            try {
                System.err.println("Error creating Solr document for PMID: " + rs.getString("pmid"));
            } catch (SQLException e2) {
                System.err.println("Error creating Solr document (couldn't get PMID)");
            }
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            try {
                System.err.println("Unexpected error creating Solr document for PMID: " + rs.getString("pmid"));
            } catch (SQLException e2) {
                System.err.println("Unexpected error creating Solr document (couldn't get PMID)");
            }
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Helper method to safely add fields from ResultSet to SolrInputDocument
     */
    private void addFieldIfExists(SolrInputDocument doc, ResultSet rs, String fieldName) {
        try {
            String value = rs.getString(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                // Special handling for count fields - convert to integers
                if (fieldName.endsWith("_count")) {
                    String[] values = value.split(" \\| ");
                    for (String val : values) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            try {
                                doc.addField(fieldName, Integer.parseInt(val));
                            } catch (NumberFormatException e) {
                                // Skip invalid numbers
                                System.err.println("Warning: Invalid count value '" + val + "' for field " + fieldName);
                            }
                        }
                    }
                }
                // Check if the field contains pipe-separated values
                // These fields typically contain multiple values separated by " | "
                else if (fieldName.endsWith("_id") || fieldName.endsWith("_term") ||
                    fieldName.endsWith("_pos") || fieldName.equals("gene")) {
                    // Split by pipe and add each value separately for multi-valued fields
                    String[] values = value.split(" \\| ");
                    for (String val : values) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            doc.addField(fieldName, val);
                        }
                    }
                } else {
                    // Single value field
                    doc.addField(fieldName, value);
                }
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, skip it
        }
    }

    /**
     * Process the entire database in chunks to avoid timeouts and memory issues
     */
    private void processInChunks(Connection conn, SimpleDateFormat sdf) throws SQLException, SolrServerException, IOException {
        int chunkSize = DatabaseConfig.getChunkSize();  // Process records in chunks
        int offset = 0;
        int totalProcessed = 0;
        boolean hasMore = true;

        System.out.println("Processing entire database in chunks of " + chunkSize + " records...");

        // First get total count
        Statement countStmt = conn.createStatement();
        ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM solr_docs");
        countRs.next();
        int totalRecords = countRs.getInt(1);
        System.out.println("Total records to process: " + totalRecords);
        countRs.close();
        countStmt.close();

        while (hasMore) {
            String query = "SELECT * FROM solr_docs ORDER BY pmid LIMIT " + chunkSize + " OFFSET " + offset;
            System.out.println("\nProcessing chunk: records " + (offset + 1) + " to " + (offset + chunkSize));
            System.out.println("Progress: " + offset + " / " + totalRecords + " (" +
                              String.format("%.1f", (offset * 100.0 / totalRecords)) + "%)");

            // Use cursor-based fetching
            conn.setAutoCommit(false);
            Statement s = conn.createStatement();
            s.setFetchSize(100);
            ResultSet rs = s.executeQuery(query);

            int chunkCount = 0;
            while (rs.next()) {
                String pmid = rs.getString("pmid");
                if (chunkCount % 100 == 0) {
                    System.out.println(sdf.format(new Date()) + " processing " + pmid + " (" + (totalProcessed + chunkCount + 1) + ")");
                }

                // Create Solr document from database record
                try {
                    SolrInputDocument doc = createSolrDocument(rs);

                    if (doc != null) {
                        // Add document to batch
                        documentBatch.add(doc);

                        // Send batch if it reaches the size limit
                        if (documentBatch.size() >= BATCH_SIZE) {
                            sendBatchToSolr();
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue processing
                    System.err.println("Skipping record due to error: " + e.getMessage());
                }
                chunkCount++;
            }

            rs.close();
            s.close();

            // Send any remaining documents in this chunk
            if (!documentBatch.isEmpty()) {
                sendBatchToSolr();
            }

            totalProcessed += chunkCount;
            offset += chunkSize;

            // Check if we have more records
            hasMore = chunkCount == chunkSize;

            if (hasMore) {
                System.out.println("Chunk completed. Processed " + totalProcessed + " records so far...");
                // Small delay between chunks to avoid overwhelming the database
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }

        // Final commit
        solrServer.commit();
        System.out.println("\n==============================================");
        System.out.println("Successfully transferred " + totalProcessed + " documents to Solr");
        System.out.println("==============================================");

        conn.commit();
        conn.close();
    }

    /**
     * Sends the current batch of documents to Solr
     */
    private void sendBatchToSolr() {
        try {
            if (documentBatch.isEmpty()) {
                return;
            }

            System.out.println("Sending batch of " + documentBatch.size() + " documents to Solr...");

            // Print JSON representation of documents
            System.out.println("\n========== JSON BEING SENT TO SOLR ==========");
            for (SolrInputDocument doc : documentBatch) {
                System.out.println(documentToJson(doc));
            }
            System.out.println("========== END JSON ==========\n");

            solrServer.add(documentBatch);
            solrServer.commit();

            // Clear the batch
            documentBatch.clear();

            System.out.println("Batch sent successfully to Solr");

        } catch (SolrServerException | IOException e) {
            System.err.println("Error sending batch to Solr: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Convert SolrInputDocument to JSON string for debugging
     */
    private String documentToJson(SolrInputDocument doc) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        boolean first = true;
        for (String fieldName : doc.getFieldNames()) {
            if (!first) {
                json.append(",\n");
            }

            Collection<Object> values = doc.getFieldValues(fieldName);
            json.append("  \"").append(fieldName).append("\": ");

            if (values.size() > 1) {
                // Multiple values - format as JSON array
                json.append("[");
                boolean firstVal = true;
                for (Object val : values) {
                    if (!firstVal) {
                        json.append(", ");
                    }
                    String escaped = val.toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                    json.append("\"").append(escaped).append("\"");
                    firstVal = false;
                }
                json.append("]");
            } else {
                // Single value
                Object value = values.iterator().next();
                if (value instanceof String) {
                    String escaped = value.toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                    json.append("\"").append(escaped).append("\"");
                } else {
                    json.append("\"").append(value).append("\"");
                }
            }
            first = false;
        }
        json.append("\n}");
        return json.toString();
    }

    public static void main(String[] args) throws Exception {
        // Debug: print all arguments
        System.out.println("Arguments received: " + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("  args[" + i + "] = " + args[i]);
        }

        String solrUrl = args.length > 0 ? args[0] : DatabaseConfig.getDefaultSolrUrl();
        String dateFilter = args.length > 1 ? args[1] : null; // null means process all records

        // Validate that solrUrl looks like a URL
        if (!solrUrl.startsWith("http")) {
            System.err.println("Error: First parameter should be a Solr URL starting with 'http'");
            System.err.println("Usage: SimplePostgresToSolr <solr_url> [date_filter]");
            System.err.println("Example: SimplePostgresToSolr http://dev.rgd.mcw.edu:8983/solr/ai1 CHUNKS");
            System.exit(1);
        }

        System.out.println("Using Solr URL: " + solrUrl);
        System.out.println("Using date filter: " + dateFilter);

        SimplePostgresToSolr loader = new SimplePostgresToSolr(solrUrl);
        loader.transferData(dateFilter);

        System.out.println("Transfer completed!");
    }
}