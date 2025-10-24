package edu.mcw.rgd.nlp;

import java.sql.*;

public class QueryRecord {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: QueryRecord <pmid>");
            System.exit(1);
        }

        String pmid = args[0];

        Connection conn = DriverManager.getConnection(DatabaseConfig.getDbUrl(), DatabaseConfig.getDbUsername(), DatabaseConfig.getDbPassword());
        PreparedStatement ps = conn.prepareStatement(
            "SELECT pmid, title, authors, keywords, mesh_terms, gene, abstract FROM solr_docs WHERE pmid = ?"
        );
        ps.setString(1, pmid);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            System.out.println("PMID: " + rs.getString("pmid"));
            System.out.println("\nTitle: " + rs.getString("title"));
            System.out.println("\nAuthors: " + rs.getString("authors"));
            System.out.println("\nKeywords: " + rs.getString("keywords"));
            System.out.println("\nMeSH Terms: " + rs.getString("mesh_terms"));
            System.out.println("\nGenes: " + rs.getString("gene"));

            // Handle abstract
            try {
                String abstractText = rs.getString("abstract");
                if (abstractText != null && !abstractText.trim().isEmpty()) {
                    System.out.println("\nAbstract: " + abstractText.substring(0, Math.min(200, abstractText.length())) + "...");
                }
            } catch (SQLException e) {
                try {
                    Clob abstractClob = rs.getClob("abstract");
                    if (abstractClob != null) {
                        String abstractText = abstractClob.getSubString(1, (int) Math.min(200, abstractClob.length()));
                        System.out.println("\nAbstract: " + abstractText + "...");
                    }
                } catch (SQLException e2) {
                    System.out.println("\nAbstract: [Could not read]");
                }
            }
        } else {
            System.out.println("PMID " + pmid + " not found in database");
        }

        rs.close();
        ps.close();
        conn.close();
    }
}