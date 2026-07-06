package edu.mcw.rgd.nlp;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GeneOnlyUpdater {

    private String solrUrl;
    private SolrServer solrServer;

    public GeneOnlyUpdater(String solrUrl) {
        this.solrUrl = solrUrl != null ? solrUrl : DatabaseConfig.getDefaultSolrUrl();
        this.solrServer = new HttpSolrServer(this.solrUrl);
    }

    public void updateGeneInfo(String pmid) {
        System.out.println("Updating gene information for PMID: " + pmid);
        System.out.println("Solr URL: " + solrUrl);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            // Create direct PostgreSQL connection
            System.out.println("Connecting to database: " + DatabaseConfig.getDbUrl());
            Connection conn = DriverManager.getConnection(DatabaseConfig.getDbUrl(), DatabaseConfig.getDbUsername(), DatabaseConfig.getDbPassword());

            String query = "SELECT pmid, gene, gene_count, gene_pos FROM solr_docs WHERE pmid = ?";
            System.out.println("Executing query for PMID: " + pmid);

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, pmid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println(sdf.format(new Date()) + " processing gene data for PMID " + pmid);

                // Create atomic update document
                SolrInputDocument doc = createGeneUpdateDocument(rs);

                if (doc != null) {
                    // Print JSON representation of atomic update
                    System.out.println("\n========== ATOMIC UPDATE JSON ==========");
                    System.out.println(documentToJson(doc));
                    System.out.println("========== END JSON ==========\n");

                    // Send atomic update to Solr
                    System.out.println("Sending atomic update to Solr...");
                    solrServer.add(doc);
                    solrServer.commit();
                    System.out.println("Successfully updated gene information for PMID " + pmid);
                } else {
                    System.err.println("Failed to create gene update document for PMID: " + pmid);
                }
            } else {
                System.err.println("PMID " + pmid + " not found in database");
            }

            rs.close();
            ps.close();
            conn.close();

        } catch (Exception e) {
            System.err.println("Error updating gene information: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a SolrInputDocument for atomic update of gene fields only
     */
    private SolrInputDocument createGeneUpdateDocument(ResultSet rs) throws SQLException {
        SolrInputDocument doc = new SolrInputDocument();

        try {
            // PMID is required as the document ID
            String pmid = rs.getString("pmid");
            if (pmid != null) {
                doc.addField("pmid", pmid);
            } else {
                return null;
            }

            // Gene field - use atomic update syntax
            String geneValue = rs.getString("gene");
            if (geneValue != null && !geneValue.trim().isEmpty()) {
                String[] genes = geneValue.split(" \\| ");
                java.util.Map<String, Object> geneUpdate = new java.util.HashMap<>();
                java.util.List<String> geneList = new java.util.ArrayList<>();
                for (String gene : genes) {
                    gene = gene.trim();
                    if (!gene.isEmpty()) {
                        geneList.add(gene);
                    }
                }
                geneUpdate.put("set", geneList);
                doc.addField("gene", geneUpdate);

                // Also update gene_s (string version)
                java.util.Map<String, Object> geneSUpdate = new java.util.HashMap<>();
                geneSUpdate.put("set", geneList);
                doc.addField("gene_s", geneSUpdate);
            }

            // Gene count field
            String geneCountValue = rs.getString("gene_count");
            if (geneCountValue != null && !geneCountValue.trim().isEmpty()) {
                String[] counts = geneCountValue.split(" \\| ");
                java.util.Map<String, Object> countUpdate = new java.util.HashMap<>();
                java.util.List<Integer> countList = new java.util.ArrayList<>();
                for (String count : counts) {
                    count = count.trim();
                    if (!count.isEmpty()) {
                        try {
                            countList.add(Integer.parseInt(count));
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid count value '" + count + "' for gene_count");
                        }
                    }
                }
                countUpdate.put("set", countList);
                doc.addField("gene_count", countUpdate);
            } else {
                // Set empty count if no gene count data
                java.util.Map<String, Object> countUpdate = new java.util.HashMap<>();
                countUpdate.put("set", java.util.Arrays.asList(0));
                doc.addField("gene_count", countUpdate);
            }

            // Gene position field
            String genePosValue = rs.getString("gene_pos");
            if (genePosValue != null && !genePosValue.trim().isEmpty()) {
                java.util.Map<String, Object> posUpdate = new java.util.HashMap<>();
                posUpdate.put("set", genePosValue);
                doc.addField("gene_pos", posUpdate);
            }

            return doc;

        } catch (SQLException e) {
            System.err.println("Error creating gene update document for PMID: " + rs.getString("pmid"));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert atomic update document to JSON string for debugging
     */
    private String documentToJson(SolrInputDocument doc) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        boolean first = true;
        for (String fieldName : doc.getFieldNames()) {
            if (!first) {
                json.append(",\n");
            }

            Object fieldValue = doc.getFieldValue(fieldName);
            json.append("  \"").append(fieldName).append("\": ");

            if (fieldValue instanceof java.util.Map) {
                // Atomic update syntax
                java.util.Map<?, ?> updateMap = (java.util.Map<?, ?>) fieldValue;
                json.append("{");
                boolean firstUpdate = true;
                for (java.util.Map.Entry<?, ?> entry : updateMap.entrySet()) {
                    if (!firstUpdate) json.append(", ");
                    json.append("\"").append(entry.getKey()).append("\": ");

                    Object value = entry.getValue();
                    if (value instanceof java.util.List) {
                        json.append("[");
                        java.util.List<?> list = (java.util.List<?>) value;
                        for (int i = 0; i < list.size(); i++) {
                            if (i > 0) json.append(", ");
                            Object item = list.get(i);
                            if (item instanceof String) {
                                json.append("\"").append(item.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                            } else {
                                json.append(item);
                            }
                        }
                        json.append("]");
                    } else if (value instanceof String) {
                        json.append("\"").append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    } else {
                        json.append(value);
                    }
                    firstUpdate = false;
                }
                json.append("}");
            } else if (fieldValue instanceof String) {
                json.append("\"").append(fieldValue.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                json.append("\"").append(fieldValue).append("\"");
            }
            first = false;
        }
        json.append("\n}");
        return json.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GeneOnlyUpdater <pmid> [solr_url]");
            System.err.println("Example: GeneOnlyUpdater 12345678 http://dev.rgd.mcw.edu:8983/solr/ai1");
            System.exit(1);
        }

        String pmid = args[0];
        String solrUrl = (args.length > 1 && !args[1].isEmpty()) ? args[1] : DatabaseConfig.getDefaultSolrUrl();

        GeneOnlyUpdater updater = new GeneOnlyUpdater(solrUrl);
        updater.updateGeneInfo(pmid);

        System.out.println("Gene update completed!");
    }
}