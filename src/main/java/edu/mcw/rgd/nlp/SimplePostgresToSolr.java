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
    private int successfulDocs = 0;
    private int failedDocs = 0;
    // Solr StrField max term size is 32766 bytes; leave headroom for the "..." truncation suffix
    private static final int MAX_STRING_BYTES = 32000;
    private static final int BATCH_SIZE = DatabaseConfig.getBatchSize();

    public int getSuccessfulDocs() { return successfulDocs; }
    public int getFailedDocs() { return failedDocs; }

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
                processInChunks(conn, sdf, null);
                return;
            } else if (dateFilter != null && dateFilter.toUpperCase().startsWith("CHUNKS ")) {
                // CHUNKS with filter: e.g., "CHUNKS 2025-01-01,2025-11-21"
                String filter = dateFilter.substring(7).trim();
                processInChunks(conn, sdf, filter);
                return;
            }

            String query;
            if (dateFilter != null && !dateFilter.isEmpty()) {
                // Check if it's a date range (contains comma), limit (starts with LIMIT), or updated_after filter
                if (dateFilter.toUpperCase().startsWith("LIMIT")) {
                    // Parse the number from "LIMIT 100" format
                    String[] parts = dateFilter.split(" ");
                    if (parts.length > 1) {
                        query = "SELECT * FROM solr_docs LIMIT " + parts[1];
                    } else {
                        query = "SELECT * FROM solr_docs LIMIT 100";
                    }
                    System.out.println("Using custom limit: " + dateFilter);
                } else if (dateFilter.toUpperCase().startsWith("UPDATED_AFTER")) {
                    // Parse the date from "UPDATED_AFTER 2024-01-01" format
                    String[] parts = dateFilter.split(" ");
                    if (parts.length > 1) {
                        String afterDate = parts[1];
                        query = "SELECT * FROM solr_docs WHERE last_update_date > TIMESTAMP '" + afterDate + "'";
                        System.out.println("Filtering by last_update_date after: " + afterDate);
                    } else {
                        throw new IllegalArgumentException("UPDATED_AFTER requires a date parameter (e.g., UPDATED_AFTER 2024-01-01)");
                    }
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

                // Progress logging every 1000 records
                if (count % 1000 == 0) {
                    System.out.println("Progress: " + count + " records processed");
                }
            }

            // Send any remaining documents
            if (!documentBatch.isEmpty()) {
                sendBatchToSolr();
            }

            // Final commit
            solrServer.commit();
            System.out.println("Transfer summary");
            System.out.println("  Rows scanned:      " + count);
            System.out.println("  Sent to Solr:      " + successfulDocs);
            System.out.println("  Skipped on error:  " + failedDocs);

            conn.commit();  // Commit the transaction
            conn.close();

        } catch (Exception e) {
            System.err.println("Error during transfer: " + e.getMessage());
            e.printStackTrace();
            // Count this as a failure so main() exits non-zero
            failedDocs++;
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
                String title = sanitizeText(rs.getString("title"));
                title = truncateToMaxBytes(title, MAX_STRING_BYTES);
                doc.addField("title", title);
            }

            // Handle abstract field (could be CLOB or TEXT)
            try {
                String abstractText = rs.getString("abstract");
                if (abstractText != null && !abstractText.trim().isEmpty()) {
                    // Sanitize problematic characters that break Solr query parser
                    abstractText = sanitizeText(abstractText);
                    abstractText = truncateToMaxBytes(abstractText, MAX_STRING_BYTES);
                    doc.addField("abstract", abstractText);
                }
            } catch (SQLException e) {
                // Try as CLOB if getString fails
                try {
                    Clob abstractClob = rs.getClob("abstract");
                    if (abstractClob != null) {
                        String abstractText = abstractClob.getSubString(1, (int) abstractClob.length());
                        // Sanitize problematic characters that break Solr query parser
                        abstractText = sanitizeText(abstractText);
                        abstractText = truncateToMaxBytes(abstractText, MAX_STRING_BYTES);
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
            addFieldIfExists(doc, rs, "zfa_id");
            addFieldIfExists(doc, rs, "cmo_id");
            addFieldIfExists(doc, rs, "ma_id");
            addFieldIfExists(doc, rs, "pw_id");
            addFieldIfExists(doc, rs, "mmo_id");
            addFieldIfExists(doc, rs, "mt_id");
            addFieldIfExists(doc, rs, "cl_id");
            addFieldIfExists(doc, rs, "cc_id");
            addFieldIfExists(doc, rs, "mf_id");
            addFieldIfExists(doc, rs, "go_id");

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
            addFieldIfExists(doc, rs, "zfa_term");
            addFieldIfExists(doc, rs, "cmo_term");
            addFieldIfExists(doc, rs, "ma_term");
            addFieldIfExists(doc, rs, "pw_term");
            addFieldIfExists(doc, rs, "mmo_term");
            addFieldIfExists(doc, rs, "mt_term");
            addFieldIfExists(doc, rs, "cl_term");
            addFieldIfExists(doc, rs, "cc_term");
            addFieldIfExists(doc, rs, "mf_term");
            addFieldIfExists(doc, rs, "go_term");

            // Add position fields
            // Special handling for gene_pos to split into separate entries per gene
            addGenePosField(doc, rs);
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
            addFieldIfExists(doc, rs, "zfa_pos");
            addFieldIfExists(doc, rs, "cmo_pos");
            addFieldIfExists(doc, rs, "ma_pos");
            addFieldIfExists(doc, rs, "pw_pos");
            addFieldIfExists(doc, rs, "mmo_pos");
            addFieldIfExists(doc, rs, "mt_pos");
            addFieldIfExists(doc, rs, "cl_pos");
            addFieldIfExists(doc, rs, "cc_pos");
            addFieldIfExists(doc, rs, "mf_pos");

            // Add count fields (required for QueryBuilder interface)
            // These must all be present, even if empty
            addCountFieldIfExists(doc, rs, "gene_count");
            addCountFieldIfExists(doc, rs, "mp_count");
            addCountFieldIfExists(doc, rs, "bp_count");
            addCountFieldIfExists(doc, rs, "vt_count");
            addCountFieldIfExists(doc, rs, "chebi_count");
            addCountFieldIfExists(doc, rs, "rs_count");
            addCountFieldIfExists(doc, rs, "rdo_count");
            addCountFieldIfExists(doc, rs, "nbo_count");
            addCountFieldIfExists(doc, rs, "xco_count");
            addCountFieldIfExists(doc, rs, "so_count");
            addCountFieldIfExists(doc, rs, "hp_count");
            addCountFieldIfExists(doc, rs, "rgd_obj_count");
            addCountFieldIfExists(doc, rs, "go_count");
            addCountFieldIfExists(doc, rs, "zfa_count");
            addCountFieldIfExists(doc, rs, "cmo_count");
            addCountFieldIfExists(doc, rs, "ma_count");
            addCountFieldIfExists(doc, rs, "pw_count");
            addCountFieldIfExists(doc, rs, "mmo_count");
            addCountFieldIfExists(doc, rs, "mt_count");
            addCountFieldIfExists(doc, rs, "cl_count");
            addCountFieldIfExists(doc, rs, "cc_count");
            addCountFieldIfExists(doc, rs, "mf_count");
            addCountFieldIfExists(doc, rs, "organism_count");

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
     * Helper method to add count fields, ensuring all count fields are present even if empty
     */
    private void addCountFieldIfExists(SolrInputDocument doc, ResultSet rs, String fieldName) {
        try {
            String value = rs.getString(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                // Parse count values as integer arrays to match OntoMate schema
                String[] values = value.split(" \\| ");
                for (String val : values) {
                    val = val.trim();
                    if (!val.isEmpty()) {
                        try {
                            doc.addField(fieldName, Integer.parseInt(val));
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid count value '" + val + "' for field " + fieldName);
                        }
                    }
                }
            } else {
                // QueryBuilder requires all count fields to be present, add zero as integer
                doc.addField(fieldName, 0);
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, QueryBuilder may require all count fields
            // Add zero count for missing fields as integer
            doc.addField(fieldName, 0);
        }
    }

    /**
     * Helper method to safely add fields from ResultSet to SolrInputDocument
     */
    private void addFieldIfExists(SolrInputDocument doc, ResultSet rs, String fieldName) {
        try {
            String value = rs.getString(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                // Special handling for count fields - keep as integer arrays to match OntoMate schema
                if (fieldName.endsWith("_count")) {
                    String[] values = value.split(" \\| ");
                    for (String val : values) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            try {
                                doc.addField(fieldName, Integer.parseInt(val));
                            } catch (NumberFormatException e) {
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
                            // Sanitize text fields
                            if (isTextField(fieldName)) {
                                val = sanitizeText(val);
                            }
                            val = truncateToMaxBytes(val, MAX_STRING_BYTES);
                            doc.addField(fieldName, val);
                        }
                    }
                } else {
                    // Single value field - sanitize text fields
                    if (isTextField(fieldName)) {
                        value = sanitizeText(value);
                    }
                    // Cap every string value at Solr's per-term limit. Solr schemas often
                    // copyField text columns into a StrField sibling (e.g. affiliation -> affiliation_s),
                    // so we can't rely on the Java field name suffix to know whether truncation is needed.
                    value = truncateToMaxBytes(value, MAX_STRING_BYTES);
                    doc.addField(fieldName, value);
                }
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, skip it
        }
    }

    /**
     * Process the entire database in chunks to avoid timeouts and memory issues.
     * Uses keyset pagination on pmid so chunks don't skip or duplicate rows if
     * PostgreSQL varies its scan order between queries.
     */
    private void processInChunks(Connection conn, SimpleDateFormat sdf, String dateFilter) throws SQLException, SolrServerException, IOException {
        int chunkSize = DatabaseConfig.getChunkSize();
        int totalProcessed = 0;
        Object lastPmid = null;

        // Build optional date-range predicate
        String dateWhere = "";
        if (dateFilter != null && !dateFilter.isEmpty()) {
            if (dateFilter.contains(",")) {
                String[] dates = dateFilter.split(",");
                dateWhere = "p_date >= DATE '" + dates[0] + "' AND p_date < DATE '" + dates[1] + "'";
                System.out.println("Processing records with date filter: " + dates[0] + " to " + dates[1]);
            }
        }

        // pmid must be non-null: it's the keyset column and the Solr unique key
        String baseWhere = "pmid IS NOT NULL" + (dateWhere.isEmpty() ? "" : " AND " + dateWhere);

        System.out.println("Processing database in chunks of " + chunkSize + " records (keyset pagination on pmid)...");

        Statement countStmt = conn.createStatement();
        ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM solr_docs WHERE " + baseWhere);
        countRs.next();
        int totalRecords = countRs.getInt(1);
        System.out.println("Total records to process: " + totalRecords);
        countRs.close();
        countStmt.close();

        conn.setAutoCommit(false);

        while (true) {
            String where = baseWhere + (lastPmid == null ? "" : " AND pmid > ?");
            String query = "SELECT * FROM solr_docs WHERE " + where + " ORDER BY pmid LIMIT " + chunkSize;

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setFetchSize(100);
            if (lastPmid != null) {
                ps.setObject(1, lastPmid);
            }
            ResultSet rs = ps.executeQuery();

            int chunkCount = 0;
            Object chunkLastPmid = null;
            while (rs.next()) {
                chunkLastPmid = rs.getObject("pmid");
                try {
                    SolrInputDocument doc = createSolrDocument(rs);

                    if (doc != null) {
                        documentBatch.add(doc);

                        if (documentBatch.size() >= BATCH_SIZE) {
                            sendBatchToSolr();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing record: " + e.getMessage());
                }
                chunkCount++;
            }

            rs.close();
            ps.close();

            if (!documentBatch.isEmpty()) {
                sendBatchToSolr();
            }

            totalProcessed += chunkCount;

            System.out.println("Progress: " + totalProcessed + " / " + totalRecords + " (" +
                              String.format("%.1f", (totalProcessed * 100.0 / totalRecords)) + "%)");

            if (chunkCount < chunkSize) {
                break;
            }

            lastPmid = chunkLastPmid;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        solrServer.commit();
        System.out.println("\n==============================================");
        System.out.println("Transfer summary (CHUNKS mode)");
        System.out.println("  Rows scanned:      " + totalProcessed);
        System.out.println("  Sent to Solr:      " + successfulDocs);
        System.out.println("  Skipped on error:  " + failedDocs);
        System.out.println("==============================================");

        conn.commit();
        conn.close();
    }

    /**
     * Sends the current batch of documents to Solr.
     *
     * On batch failure, retries each doc individually so a single poisoned
     * record can be isolated, logged, and skipped instead of taking down the
     * whole sync. The batch is always cleared before returning — otherwise the
     * poisoned doc stays in memory and every subsequent send fails the same way.
     */
    private void sendBatchToSolr() {
        if (documentBatch.isEmpty()) {
            return;
        }

        try {
            solrServer.add(documentBatch);
            solrServer.commit();
            successfulDocs += documentBatch.size();
        } catch (SolrServerException | IOException e) {
            System.err.println("Batch send failed (" + documentBatch.size() + " docs): " + e.getMessage());
            System.err.println("Retrying batch one document at a time to isolate the failure...");
            List<SolrInputDocument> retry = new ArrayList<>(documentBatch);
            int retrySuccess = 0;
            int retryFail = 0;
            for (SolrInputDocument doc : retry) {
                try {
                    solrServer.add(doc);
                    retrySuccess++;
                } catch (SolrServerException | IOException docE) {
                    Object pmid = doc.getFieldValue("pmid");
                    System.err.println("  SKIPPED pmid=" + pmid + ": " + docE.getMessage());
                    retryFail++;
                }
            }
            try {
                solrServer.commit();
            } catch (SolrServerException | IOException commitE) {
                System.err.println("  Commit after per-doc retry failed: " + commitE.getMessage());
            }
            successfulDocs += retrySuccess;
            failedDocs += retryFail;
            System.err.println("Batch retry: " + retrySuccess + " succeeded, " + retryFail + " skipped");
        } finally {
            documentBatch.clear();
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

    /**
     * Check if a field contains text that should be sanitized
     */
    private boolean isTextField(String fieldName) {
        return fieldName.equals("authors") || fieldName.equals("keywords") ||
               fieldName.equals("mesh_terms") || fieldName.equals("affiliation") ||
               fieldName.equals("chemicals") || fieldName.equals("citation") ||
               fieldName.endsWith("_term");
    }

    /**
     * Sanitize text to remove characters that break Solr query parser
     */
    private String sanitizeText(String text) {
        if (text == null) return null;

        // Remove leading tabs (common in abstracts)
        text = text.replaceAll("^\\t+", "");

        // Replace all newlines and carriage returns with spaces
        // This matches ai1 format which has no embedded newlines
        text = text.replaceAll("[\\r\\n]+", " ");

        // Collapse multiple spaces into single space
        text = text.replaceAll("\\s+", " ");

        // Remove trailing spaces
        text = text.trim();

        // Replace problematic question marks with proper Greek letters
        // These appear to be encoding artifacts for Greek letters
        text = text.replace("?-macroglobulin", "α-macroglobulin");
        text = text.replace("?-2-macroglobulin", "α-2-macroglobulin");
        text = text.replace("factor-?", "factor-β");
        text = text.replace("TGF-?", "TGF-β");

        // Replace HTML entities that might cause parsing issues
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");

        // Fix common text extraction errors
        text = text.replace("homologyepatocellular", "hepatocellular");

        // Replace any remaining standalone question marks that might break parsing
        // but preserve question marks that are clearly punctuation (end of sentences)
        text = text.replaceAll("\\?(?![\\s.,;:]|$)", "");

        return text;
    }

    /**
     * Truncate a string to a maximum number of UTF-8 bytes
     * Solr has a limit of 32766 bytes for string fields
     */
    private String truncateToMaxBytes(String text, int maxBytes) {
        if (text == null) {
            return null;
        }

        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }

        // Truncate to maxBytes, being careful not to cut in the middle of a multi-byte character
        int byteCount = 0;
        int charCount = 0;
        for (char c : text.toCharArray()) {
            int charBytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (byteCount + charBytes > maxBytes) {
                break;
            }
            byteCount += charBytes;
            charCount++;
        }

        return text.substring(0, charCount) + "...";
    }

    /**
     * Special handler for gene_pos field to split positions into separate entries
     * Each gene gets ONE position entry (not the combined string)
     */
    private void addGenePosField(SolrInputDocument doc, ResultSet rs) {
        try {
            String genePosValue = rs.getString("gene_pos");

            if (genePosValue != null && !genePosValue.trim().isEmpty()) {
                // Split positions by pipe (|) - each segment is one gene's position
                String[] positions = genePosValue.split("\\|");

                for (String pos : positions) {
                    pos = pos.trim();
                    if (!pos.isEmpty()) {
                        doc.addField("gene_pos", pos);
                    }
                }
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, skip it
        }
    }

    public static void main(String[] args) throws Exception {
        // Debug: print all arguments
        System.out.println("Arguments received: " + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("  args[" + i + "] = " + args[i]);
        }

        String solrUrl = (args.length > 0 && !args[0].isEmpty()) ? args[0] : DatabaseConfig.getDefaultSolrUrl();
        String dateFilter = (args.length > 1 && !args[1].isEmpty()) ? args[1] : null; // null means process all records

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

        if (loader.getFailedDocs() > 0) {
            System.err.println("Transfer completed with " + loader.getFailedDocs() + " skipped document(s). See log above for pmids.");
            System.exit(1);
        }
        System.out.println("Transfer completed!");
    }
}