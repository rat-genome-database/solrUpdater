package edu.mcw.rgd.nlp;

import java.sql.*;

public class ShowPositions {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ShowPositions <pmid>");
            System.exit(1);
        }

        String pmid = args[0];

        Connection conn = DriverManager.getConnection(
            DatabaseConfig.getDbUrl(),
            DatabaseConfig.getDbUsername(),
            DatabaseConfig.getDbPassword()
        );

        PreparedStatement ps = conn.prepareStatement("SELECT title, abstract, gene FROM solr_docs WHERE pmid = ?");
        ps.setString(1, pmid);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String title = rs.getString("title");
            String abstractText = rs.getString("abstract");
            String geneValue = rs.getString("gene");

            System.out.println("=".repeat(80));
            System.out.println("TITLE (Section 0):");
            System.out.println("=".repeat(80));
            System.out.println(title);
            System.out.println("\nTitle length: " + title.length());

            // Find HMGCR in title
            String[] genes = geneValue.split(" \\| ");
            for (String gene : genes) {
                gene = gene.trim();
                System.out.println("\n--- Searching for '" + gene + "' in title ---");
                int pos = 0;
                int count = 0;
                while ((pos = title.toLowerCase().indexOf(gene.toLowerCase(), pos)) != -1) {
                    count++;
                    System.out.println(count + ". Position 0;" + pos + "-" + (pos + gene.length()) + " => \"" + title.substring(pos, pos + gene.length()) + "\"");
                    System.out.println("   Context: ..." + title.substring(Math.max(0, pos - 20), Math.min(title.length(), pos + gene.length() + 20)) + "...");
                    pos++;
                }
                if (count == 0) {
                    System.out.println("   Not found in title");
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("ABSTRACT (Section 1):");
            System.out.println("=".repeat(80));

            // Show abstract with character positions marked every 50 chars
            System.out.println("\n--- Abstract with position markers every 50 characters ---");
            for (int i = 0; i < abstractText.length(); i += 50) {
                int end = Math.min(i + 50, abstractText.length());
                System.out.println("\nPos " + i + "-" + end + ":");
                System.out.println(abstractText.substring(i, end).replace("\t", "[TAB]").replace("\n", "[NL]"));
            }

            System.out.println("\n\nAbstract length: " + abstractText.length());
            System.out.println("First character: '" + abstractText.charAt(0) + "' (is whitespace: " + Character.isWhitespace(abstractText.charAt(0)) + ")");

            // Find all genes in abstract
            for (String gene : genes) {
                gene = gene.trim();
                System.out.println("\n" + "=".repeat(80));
                System.out.println("Searching for '" + gene + "' in abstract:");
                System.out.println("=".repeat(80));
                int pos = 0;
                int count = 0;
                while ((pos = abstractText.toLowerCase().indexOf(gene.toLowerCase(), pos)) != -1) {
                    count++;
                    System.out.println("\n" + count + ". Position 1;" + pos + "-" + (pos + gene.length()));
                    System.out.println("   Text: \"" + abstractText.substring(pos, pos + gene.length()) + "\"");
                    System.out.println("   Context: ..." + abstractText.substring(Math.max(0, pos - 30), Math.min(abstractText.length(), pos + gene.length() + 30)).replace("\t", "[TAB]").replace("\n", "[NL]") + "...");
                    pos++;
                }
                if (count == 0) {
                    System.out.println("   Not found in abstract");
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("SUMMARY:");
            System.out.println("=".repeat(80));
            for (String gene : genes) {
                gene = gene.trim();
                int titleCount = 0;
                int abstractCount = 0;

                int pos = 0;
                while ((pos = title.toLowerCase().indexOf(gene.toLowerCase(), pos)) != -1) {
                    titleCount++;
                    pos++;
                }

                pos = 0;
                while ((pos = abstractText.toLowerCase().indexOf(gene.toLowerCase(), pos)) != -1) {
                    abstractCount++;
                    pos++;
                }

                System.out.println("\nGene: " + gene);
                System.out.println("  Title occurrences: " + titleCount);
                System.out.println("  Abstract occurrences: " + abstractCount);
                System.out.println("  Total: " + (titleCount + abstractCount));
            }

        } else {
            System.err.println("PMID not found");
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
