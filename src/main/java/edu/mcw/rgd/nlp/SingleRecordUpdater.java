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