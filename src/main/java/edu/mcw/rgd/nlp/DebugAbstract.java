package edu.mcw.rgd.nlp;

import java.sql.*;

public class DebugAbstract {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DebugAbstract <pmid>");
            System.exit(1);
        }

        String pmid = args[0];

        Connection conn = DriverManager.getConnection(
            DatabaseConfig.getDbUrl(),
            DatabaseConfig.getDbUsername(),
            DatabaseConfig.getDbPassword()
        );

        PreparedStatement ps = conn.prepareStatement("SELECT abstract, gene, gene_pos, gene_count, title FROM solr_docs WHERE pmid = ?");
        ps.setString(1, pmid);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String title = rs.getString("title");
            String abstractText = rs.getString("abstract");
            String genePos = rs.getString("gene_pos");
            String geneValue = rs.getString("gene");

            System.out.println("Title: " + title);
            System.out.println("Title length: " + title.length());
            System.out.println("\nGene(s): " + geneValue);
            System.out.println("Database gene_pos: " + genePos);

            System.out.println("Abstract length: " + abstractText.length());
            System.out.println("\n=== First 500 characters ===");
            System.out.println(abstractText.substring(0, Math.min(500, abstractText.length())));

            System.out.println("\n\n=== Finding 'HMGCR' positions ===");
            String searchTerm = "HMGCR";
            int pos = 0;
            int count = 0;
            while ((pos = abstractText.indexOf(searchTerm, pos)) != -1) {
                count++;
                int lineStart = Math.max(0, pos - 20);
                int lineEnd = Math.min(abstractText.length(), pos + searchTerm.length() + 20);
                String context = abstractText.substring(lineStart, lineEnd);
                System.out.println(count + ". Position " + pos + "-" + (pos + searchTerm.length() - 1) +
                                   ": ..." + context + "...");
                pos++;
            }

            System.out.println("\nTotal occurrences: " + count);

            // Show character at position 334
            if (abstractText.length() > 340) {
                System.out.println("\n=== Characters around position 334 ===");
                System.out.println("Position 330-345: '" + abstractText.substring(330, 345) + "'");
            }

            // Check for HTML/XML tags
            System.out.println("\n=== Checking for HTML/XML tags ===");
            int tagCount = 0;
            int tagPos = 0;
            while ((tagPos = abstractText.indexOf("<", tagPos)) != -1) {
                int endPos = abstractText.indexOf(">", tagPos);
                if (endPos != -1) {
                    tagCount++;
                    String tag = abstractText.substring(tagPos, endPos + 1);
                    System.out.println("Tag " + tagCount + " at position " + tagPos + "-" + endPos + ": " + tag);
                    tagPos = endPos + 1;
                } else {
                    break;
                }
            }
            System.out.println("Total tags found: " + tagCount);

            // Strip HTML tags and show the cleaned text
            System.out.println("\n=== After stripping HTML tags ===");
            String cleanAbstract = abstractText.replaceAll("<[^>]+>", "");
            System.out.println("Cleaned abstract length: " + cleanAbstract.length());
            System.out.println("Characters removed: " + (abstractText.length() - cleanAbstract.length()));

            // Find HMGCR in cleaned text
            System.out.println("\n=== Finding 'HMGCR' in cleaned text (abstract only) ===");
            int cleanPos = 0;
            int cleanCount = 0;
            while ((cleanPos = cleanAbstract.indexOf("HMGCR", cleanPos)) != -1) {
                cleanCount++;
                int lineStart = Math.max(0, cleanPos - 20);
                int lineEnd = Math.min(cleanAbstract.length(), cleanPos + 5 + 20);
                String context = cleanAbstract.substring(lineStart, lineEnd);
                System.out.println(cleanCount + ". Position " + cleanPos + "-" + (cleanPos + 5) +
                                   ": ..." + context + "...");
                cleanPos++;
            }

            // Check what's at position 158-182 in original abstract (for hp_term comparison)
            System.out.println("\n=== Checking hp_pos position 158-182 in abstract ===");
            if (abstractText.length() > 182) {
                System.out.println("Position 158-182 in original abstract (with tab/tags): '" + abstractText.substring(158, 182) + "'");
            }
            if (cleanAbstract.length() > 182) {
                System.out.println("Position 158-182 in cleaned abstract (no tags): '" + cleanAbstract.substring(158, 182) + "'");
            }

            // Also check without leading tab
            String noTabAbstract = abstractText.replaceAll("^\\s+", "");
            String noTabClean = noTabAbstract.replaceAll("<[^>]+>", "");
            if (noTabClean.length() > 182) {
                System.out.println("Position 158-182 in no-tab cleaned abstract: '" + noTabClean.substring(158, 182) + "'");
            }

            System.out.println("\n=== Testing position format ===");
            if (title.length() >= 78) {
                System.out.println("Title position 54-78: '" + title.substring(54, 78) + "'");
                System.out.println("Expected: 'hepatocellular carcinoma'");
            }
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
