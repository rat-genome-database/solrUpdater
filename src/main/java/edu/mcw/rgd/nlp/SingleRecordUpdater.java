package edu.mcw.rgd.nlp;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class SingleRecordUpdater {

    private String solrUrl;
    private SolrServer solrServer;

    public SingleRecordUpdater(String solrUrl) {
        this.solrUrl = solrUrl != null ? solrUrl : DatabaseConfig.getDefaultSolrUrl();
        this.solrServer = new HttpSolrServer(this.solrUrl);
    }

    public void updateRecord(String pmid) {
        System.out.println("Updating single record for PMID: " + pmid);
        System.out.println("Solr URL: " + solrUrl);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            // Create direct PostgreSQL connection
            System.out.println("Connecting to database: " + DatabaseConfig.getDbUrl());
            Connection conn = DriverManager.getConnection(DatabaseConfig.getDbUrl(), DatabaseConfig.getDbUsername(), DatabaseConfig.getDbPassword());

            String query = "SELECT * FROM solr_docs WHERE pmid = ?";
            System.out.println("Executing query for PMID: " + pmid);

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, pmid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println(sdf.format(new Date()) + " processing PMID " + pmid);

                // Create Solr document from database record
                SolrInputDocument doc = createSolrDocument(rs);

                if (doc != null) {
                    // Print JSON representation of document
                    System.out.println("\n========== JSON BEING SENT TO SOLR ==========");
                    System.out.println(documentToJson(doc));
                    System.out.println("========== END JSON ==========\n");

                    // Send document to Solr
                    System.out.println("Sending document to Solr...");
                    solrServer.add(doc);
                    solrServer.commit();
                    System.out.println("Successfully updated PMID " + pmid + " in Solr");
                } else {
                    System.err.println("Failed to create Solr document for PMID: " + pmid);
                }
            } else {
                System.err.println("PMID " + pmid + " not found in database");
            }

            rs.close();
            ps.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error updating record: " + e.getMessage());
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
                String title = sanitizeText(rs.getString("title"));
                doc.addField("title", title);
            }

            // Handle abstract field (could be CLOB or TEXT)
            try {
                String abstractText = rs.getString("abstract");
                if (abstractText != null && !abstractText.trim().isEmpty()) {
                    // Sanitize problematic characters that break Solr query parser
                    abstractText = sanitizeText(abstractText);
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

            // Add derived mt_ fields from mesh_terms for browse interface compatibility
            try {
                String meshTerms = rs.getString("mesh_terms");
                if (meshTerms != null && !meshTerms.trim().isEmpty()) {
                    // Split mesh terms and add as mt_term
                    String[] terms = meshTerms.split(";");
                    for (String term : terms) {
                        term = term.trim();
                        if (!term.isEmpty()) {
                            doc.addField("mt_term", term);
                        }
                    }
                }
            } catch (SQLException e) {
                // mesh_terms field doesn't exist, skip mt_ fields
            }

            addFieldIfExists(doc, rs, "affiliation");
            addFieldIfExists(doc, rs, "issn");
            // Convert p_year to integer to match OntoMate schema
            try {
                String yearValue = rs.getString("p_year");
                if (yearValue != null && !yearValue.trim().isEmpty()) {
                    try {
                        doc.addField("p_year", Integer.parseInt(yearValue.trim()));
                    } catch (NumberFormatException e) {
                        doc.addField("p_year", yearValue); // fallback to string if not a number
                    }
                }
            } catch (SQLException e) {
                // Field doesn't exist, skip it
            }
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

            // Add GO fields (duplicates of BP for compatibility)
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

            // Add GO terms (duplicates of BP for compatibility)
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
            addCountFieldIfExists(doc, rs, "organism_count");

            // Add source field
            doc.addField("p_source", "pubmed");

            // Add text field for search (array of searchable content like OntoMate)
            try {
                List<String> textContent = new ArrayList<>();
                if (rs.getString("keywords") != null) textContent.add(rs.getString("keywords"));
                if (rs.getString("mesh_terms") != null) textContent.add(rs.getString("mesh_terms"));
                if (rs.getString("chemicals") != null) textContent.add(rs.getString("chemicals"));
                if (rs.getString("title") != null) textContent.add(rs.getString("title"));

                String abstractText = rs.getString("abstract");
                if (abstractText == null) {
                    try {
                        Clob abstractClob = rs.getClob("abstract");
                        if (abstractClob != null) {
                            abstractText = abstractClob.getSubString(1, (int) abstractClob.length());
                        }
                    } catch (SQLException e2) {
                        // Skip if both fail
                    }
                }
                if (abstractText != null && !abstractText.trim().isEmpty()) {
                    // Don't sanitize for text field - keep original for search
                    textContent.add(abstractText);
                }

                for (String text : textContent) {
                    doc.addField("text", text);
                }
            } catch (SQLException e) {
                // Skip text field if error
            }

            // Add string versions of key array fields to match OntoMate
            try {
                String affiliation = rs.getString("affiliation");
                if (affiliation != null) {
                    doc.addField("affiliation_s", affiliation);
                }
            } catch (SQLException e) {}

            try {
                String organismTerm = rs.getString("organism_term");
                if (organismTerm != null) {
                    doc.addField("organism_term_s", organismTerm);
                }
            } catch (SQLException e) {}

            // Add gene_s field (duplicate of gene array)
            try {
                String geneValue = rs.getString("gene");
                if (geneValue != null && !geneValue.trim().isEmpty()) {
                    String[] values = geneValue.split(" \\| ");
                    for (String val : values) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            doc.addField("gene_s", val);
                        }
                    }
                }
            } catch (SQLException e) {}

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
                            doc.addField(fieldName, val);
                        }
                    }
                } else {
                    // Single value field - sanitize text fields
                    if (isTextField(fieldName)) {
                        value = sanitizeText(value);
                    }
                    // Truncate string fields (_s suffix) to Solr's max term length (32766 bytes)
                    if (fieldName.endsWith("_s")) {
                        value = truncateToMaxBytes(value, 32000); // Use 32000 to be safe
                    }
                    doc.addField(fieldName, value);
                }
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, skip it
        }
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
        if (args.length < 1) {
            System.err.println("Usage: SingleRecordUpdater <pmid> [solr_url]");
            System.err.println("Example: SingleRecordUpdater 12345678 http://dev.rgd.mcw.edu:8983/solr/ai1");
            System.exit(1);
        }

        String pmid = args[0];
        String solrUrl = args.length > 1 ? args[1] : DatabaseConfig.getDefaultSolrUrl();

        SingleRecordUpdater updater = new SingleRecordUpdater(solrUrl);
        updater.updateRecord(pmid);

        System.out.println("Update completed!");
    }
}