package edu.mcw.rgd.nlp;

import java.sql.*;

public class PrintAbstract {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PrintAbstract <pmid>");
            System.exit(1);
        }

        String pmid = args[0];

        Connection conn = DriverManager.getConnection(
            DatabaseConfig.getDbUrl(),
            DatabaseConfig.getDbUsername(),
            DatabaseConfig.getDbPassword()
        );

        PreparedStatement ps = conn.prepareStatement("SELECT title, abstract FROM solr_docs WHERE pmid = ?");
        ps.setString(1, pmid);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String title = rs.getString("title");
            String abstractText = rs.getString("abstract");

            System.out.println("================================================================================");
            System.out.println("TITLE:");
            System.out.println("================================================================================");
            System.out.println(title);
            System.out.println();

            System.out.println("================================================================================");
            System.out.println("ABSTRACT:");
            System.out.println("================================================================================");
            System.out.println(abstractText);
            System.out.println();

            System.out.println("================================================================================");
            System.out.println("STATISTICS:");
            System.out.println("================================================================================");
            System.out.println("Title length: " + title.length() + " characters");
            System.out.println("Abstract length: " + abstractText.length() + " characters");
            System.out.println("First character of abstract: 0x" + Integer.toHexString(abstractText.charAt(0)) +
                             (Character.isWhitespace(abstractText.charAt(0)) ? " (whitespace)" : ""));

        } else {
            System.err.println("PMID not found");
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
