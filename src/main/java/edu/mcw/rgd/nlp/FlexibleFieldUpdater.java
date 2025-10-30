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

/**
 * Flexible field updater that allows updating specific fields for a PMID
 * Usage: FlexibleFieldUpdater <pmid> <solr_host> <solr_core> <field1,field2,field3,...>
 * Example: FlexibleFieldUpdater 12345678 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count
 */
public class FlexibleFieldUpdater {

    private String solrUrl;
    private SolrServer solrServer;
    private Set<String> fieldsToUpdate;

    public FlexibleFieldUpdater(String solrHost, String solrCore, String fields) {
        this.solrUrl = "http://" + solrHost + "/solr/" + solrCore;
        this.solrServer = new HttpSolrServer(this.solrUrl);
        this.fieldsToUpdate = new HashSet<>(Arrays.asList(fields.split(",")));
    }

    public void updateRecord(String pmid) {
        updateRecord(pmid, false);
    }

    public void updateRecord(String pmid, boolean quietMode) {
        if (!quietMode) {
            System.out.println("Updating record for PMID: " + pmid);
            System.out.println("Solr URL: " + solrUrl);
            System.out.println("Fields to update: " + fieldsToUpdate);
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            // Create direct PostgreSQL connection
            if (!quietMode) {
                System.out.println("Connecting to database...");
            }
            DriverManager.setLoginTimeout(30); // 30 second timeout
            Connection conn = DriverManager.getConnection(
                DatabaseConfig.getDbUrl(),
                DatabaseConfig.getDbUsername(),
                DatabaseConfig.getDbPassword()
            );
            if (!quietMode) {
                System.out.println("Database connection established");
            }

            String query = "SELECT * FROM solr_docs WHERE pmid = ?";

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, pmid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                if (!quietMode) {
                    System.out.println(sdf.format(new Date()) + " processing PMID " + pmid);
                }

                // Create Solr document with only the requested fields
                SolrInputDocument doc = createPartialSolrDocument(rs);

                if (doc != null && doc.getFieldNames().size() > 1) { // More than just pmid
                    if (!quietMode) {
                        // Print JSON representation of document
                        System.out.println("\n========== JSON BEING SENT TO SOLR ==========");
                        System.out.println(documentToJson(doc));
                        System.out.println("========== END JSON ==========\n");
                    }

                    // Send document to Solr using atomic update
                    solrServer.add(doc);
                    solrServer.commit();
                    if (!quietMode) {
                        System.out.println("Successfully updated PMID " + pmid + " in Solr");
                    }
                } else {
                    System.err.println("No matching fields found to update for PMID: " + pmid);
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

    public void updateAllRecords(Integer limit) {
        System.out.println("Updating ALL records in database");
        System.out.println("Solr URL: " + solrUrl);
        System.out.println("Fields to update: " + fieldsToUpdate);
        if (limit != null) {
            System.out.println("Limit: " + limit + " records");
        }

        try {
            System.out.println("Connecting to database...");
            DriverManager.setLoginTimeout(30); // 30 second timeout
            Connection conn = DriverManager.getConnection(
                DatabaseConfig.getDbUrl(),
                DatabaseConfig.getDbUsername(),
                DatabaseConfig.getDbPassword()
            );
            System.out.println("Database connection established");

            String query = "SELECT pmid FROM solr_docs";
            if (limit != null) {
                query += " LIMIT " + limit;
            }

            Statement stmt = conn.createStatement();
            stmt.setQueryTimeout(60); // 60 second timeout for query execution
            ResultSet rs = stmt.executeQuery(query);

            int count = 0;
            int errors = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            while (rs.next()) {
                String pmid = rs.getString("pmid");
                count++;

                if (count % 100 == 0) {
                    System.out.println(sdf.format(new Date()) + " Processed " + count + " records...");
                }

                try {
                    updateRecord(pmid, true);
                } catch (Exception e) {
                    errors++;
                    System.err.println("Error updating PMID " + pmid + ": " + e.getMessage());
                }
            }

            rs.close();
            stmt.close();
            conn.close();

            System.out.println("\n" + sdf.format(new Date()) + " Completed!");
            System.out.println("Total records processed: " + count);
            System.out.println("Errors: " + errors);

        } catch (Exception e) {
            System.err.println("Error processing records: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateRecordsByYear(String year, Integer limit) {
        System.out.println("Updating records for year: " + year);
        System.out.println("Solr URL: " + solrUrl);
        System.out.println("Fields to update: " + fieldsToUpdate);
        if (limit != null) {
            System.out.println("Limit: " + limit + " records");
        }

        try {
            System.out.println("Connecting to database...");
            DriverManager.setLoginTimeout(30); // 30 second timeout
            Connection conn = DriverManager.getConnection(
                DatabaseConfig.getDbUrl(),
                DatabaseConfig.getDbUsername(),
                DatabaseConfig.getDbPassword()
            );
            System.out.println("Database connection established");

            String query = "SELECT pmid FROM solr_docs WHERE p_year = ?";
            if (limit != null) {
                query += " LIMIT " + limit;
            }
            System.out.println("Executing query for year " + year + "...");

            PreparedStatement ps = conn.prepareStatement(query);
            ps.setQueryTimeout(60); // 60 second timeout for query execution
            ps.setInt(1, Integer.parseInt(year));
            ResultSet rs = ps.executeQuery();

            System.out.println("Query executed, processing records...");

            int count = 0;
            int errors = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");

            while (rs.next()) {
                String pmid = rs.getString("pmid");
                count++;

                if (count == 1) {
                    System.out.println("Starting to process first record...");
                }

                if (count % 100 == 0) {
                    System.out.println(sdf.format(new Date()) + " Processed " + count + " records for year " + year + "...");
                }

                try {
                    if (count == 1) {
                        System.out.println("Updating PMID: " + pmid);
                    }
                    updateRecord(pmid, true);
                    if (count == 1) {
                        System.out.println("First record completed successfully");
                    }
                } catch (Exception e) {
                    errors++;
                    System.err.println("Error updating PMID " + pmid + ": " + e.getMessage());
                }
            }

            rs.close();
            ps.close();
            conn.close();

            System.out.println("\n" + sdf.format(new Date()) + " Completed!");
            System.out.println("Total records processed: " + count);
            System.out.println("Errors: " + errors);

        } catch (Exception e) {
            System.err.println("Error processing records: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a SolrInputDocument with only the specified fields using atomic update syntax
     */
    private SolrInputDocument createPartialSolrDocument(ResultSet rs) throws SQLException {
        SolrInputDocument doc = new SolrInputDocument();

        try {
            // Always include PMID as the unique identifier (NOT wrapped in atomic update)
            if (rs.getString("pmid") != null) {
                doc.addField("pmid", rs.getString("pmid"));
            }

            // Process each requested field
            for (String fieldName : fieldsToUpdate) {
                fieldName = fieldName.trim();

                if (fieldName.isEmpty()) {
                    continue;
                }

                // Special handling for different field types
                if (fieldName.equals("title")) {
                    addTitleField(doc, rs);
                } else if (fieldName.equals("abstract")) {
                    addAbstractField(doc, rs);
                } else if (fieldName.equals("p_date")) {
                    addDateField(doc, rs);
                } else if (fieldName.equals("p_year")) {
                    addYearField(doc, rs);
                } else if (fieldName.equals("text")) {
                    addTextField(doc, rs);
                } else if (fieldName.equals("mt_term")) {
                    addMeshTermField(doc, rs);
                } else if (fieldName.equals("gene_s")) {
                    addGeneSField(doc, rs);
                } else if (fieldName.equals("gene_pos")) {
                    // Calculate gene_pos from abstract
                    calculateAndAddGenePosField(doc, rs);
                } else if (fieldName.equals("gene_count") && fieldsToUpdate.contains("gene_pos")) {
                    // Skip gene_count if gene_pos is being calculated, as it will be computed automatically
                    continue;
                } else if (fieldName.endsWith("_count")) {
                    addCountFieldIfExists(doc, rs, fieldName);
                } else if (fieldName.equals("p_source")) {
                    doc.addField("p_source", atomicSet("pubmed"));
                } else {
                    // Generic field handling
                    addFieldIfExists(doc, rs, fieldName);
                }
            }

            return doc;

        } catch (SQLException e) {
            System.err.println("Error creating Solr document for PMID: " + rs.getString("pmid"));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Helper method to wrap a value in atomic update syntax
     */
    private Map<String, Object> atomicSet(Object value) {
        Map<String, Object> update = new HashMap<>();
        update.put("set", value);
        return update;
    }

    /**
     * Helper method to wrap a list of values in atomic update syntax
     */
    private Map<String, Object> atomicSetList(List<Object> values) {
        Map<String, Object> update = new HashMap<>();
        update.put("set", values);
        return update;
    }

    /**
     * Add title field with sanitization
     */
    private void addTitleField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String title = rs.getString("title");
        if (title != null) {
            doc.addField("title", atomicSet(sanitizeText(title)));
        }
    }

    /**
     * Add abstract field with CLOB handling
     */
    private void addAbstractField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        try {
            String abstractText = rs.getString("abstract");
            if (abstractText != null && !abstractText.trim().isEmpty()) {
                doc.addField("abstract", atomicSet(sanitizeText(abstractText)));
            }
        } catch (SQLException e) {
            // Try as CLOB if getString fails
            try {
                Clob abstractClob = rs.getClob("abstract");
                if (abstractClob != null) {
                    String abstractText = abstractClob.getSubString(1, (int) abstractClob.length());
                    doc.addField("abstract", atomicSet(sanitizeText(abstractText)));
                }
            } catch (SQLException e2) {
                System.err.println("Warning: Could not read abstract for PMID: " + rs.getString("pmid"));
            }
        }
    }

    /**
     * Add date field in Solr format
     */
    private void addDateField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        Date pDate = rs.getDate("p_date");
        if (pDate != null) {
            doc.addField("p_date", atomicSet(pDate.toString() + "T06:00:00Z"));
        }
    }

    /**
     * Add year field as integer
     */
    private void addYearField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String yearValue = rs.getString("p_year");
        if (yearValue != null && !yearValue.trim().isEmpty()) {
            try {
                doc.addField("p_year", atomicSet(Integer.parseInt(yearValue.trim())));
            } catch (NumberFormatException e) {
                doc.addField("p_year", atomicSet(yearValue));
            }
        }
    }

    /**
     * Add mesh term derived field
     */
    private void addMeshTermField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String meshTerms = rs.getString("mesh_terms");
        if (meshTerms != null && !meshTerms.trim().isEmpty()) {
            List<Object> terms = new ArrayList<>();
            String[] termArray = meshTerms.split(";");
            for (String term : termArray) {
                term = term.trim();
                if (!term.isEmpty()) {
                    terms.add(term);
                }
            }
            if (!terms.isEmpty()) {
                doc.addField("mt_term", atomicSetList(terms));
            }
        }
    }

    /**
     * Add gene_s field (duplicate of gene array for faceting)
     */
    private void addGeneSField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String geneValue = rs.getString("gene");
        if (geneValue != null && !geneValue.trim().isEmpty()) {
            List<Object> genes = new ArrayList<>();
            String[] geneArray = geneValue.split(" \\| ");
            for (String gene : geneArray) {
                gene = gene.trim();
                if (!gene.isEmpty()) {
                    genes.add(gene);
                }
            }
            if (!genes.isEmpty()) {
                doc.addField("gene_s", atomicSetList(genes));
            }
        }
    }

    /**
     * Add gene_pos field with special handling for malformed database data
     * Uses gene_count to split gene_pos correctly when " | " delimiters are missing
     */
    private void addGenePosField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String genePosValue = rs.getString("gene_pos");
        String geneCountValue = rs.getString("gene_count");

        if (genePosValue == null || genePosValue.trim().isEmpty()) {
            return;
        }

        // First try normal split with " | "
        String[] posSplit = genePosValue.split(" \\| ");

        if (posSplit.length > 1) {
            // Has proper delimiters, use normal processing
            List<Object> positions = new ArrayList<>();
            for (String pos : posSplit) {
                pos = pos.trim();
                if (!pos.isEmpty()) {
                    positions.add(pos);
                }
            }
            if (!positions.isEmpty()) {
                doc.addField("gene_pos", atomicSetList(positions));
            }
        } else {
            // No proper delimiters - use gene_count to split
            if (geneCountValue != null && !geneCountValue.trim().isEmpty()) {
                String[] counts = geneCountValue.split(" \\| ");
                List<Object> positions = new ArrayList<>();

                // Split gene_pos by single "|"
                String[] allPositions = genePosValue.split("\\|");

                int posIndex = 0;
                for (String countStr : counts) {
                    countStr = countStr.trim();
                    try {
                        int count = Integer.parseInt(countStr);
                        StringBuilder genePositions = new StringBuilder();

                        for (int i = 0; i < count && posIndex < allPositions.length; i++) {
                            if (i > 0) {
                                genePositions.append("|");
                            }
                            genePositions.append(allPositions[posIndex++]);
                        }

                        if (genePositions.length() > 0) {
                            positions.add(genePositions.toString());
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid count
                    }
                }

                // Add any remaining positions as the last element
                if (posIndex < allPositions.length) {
                    StringBuilder remaining = new StringBuilder();
                    for (int i = posIndex; i < allPositions.length; i++) {
                        if (i > posIndex) {
                            remaining.append("|");
                        }
                        remaining.append(allPositions[i]);
                    }
                    if (remaining.length() > 0) {
                        positions.add(remaining.toString());
                    }
                }

                if (!positions.isEmpty()) {
                    System.out.println("DEBUG: Reconstructed gene_pos with " + positions.size() + " elements using gene_count");
                    doc.addField("gene_pos", atomicSetList(positions));
                }
            } else {
                // Fallback: add as single element
                List<Object> positions = new ArrayList<>();
                positions.add(genePosValue);
                doc.addField("gene_pos", atomicSetList(positions));
            }
        }
    }

    /**
     * Calculate and add gene_pos field by finding actual positions in title and abstract
     * Also updates gene_count based on actual occurrences found
     */
    private void calculateAndAddGenePosField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        String geneValue = rs.getString("gene");
        if (geneValue == null || geneValue.trim().isEmpty()) {
            return;
        }

        // Get title text
        String titleText = rs.getString("title");
        if (titleText == null) {
            titleText = "";
        }

        // Get abstract text
        String abstractText = null;
        try {
            abstractText = rs.getString("abstract");
        } catch (SQLException e) {
            try {
                Clob abstractClob = rs.getClob("abstract");
                if (abstractClob != null) {
                    abstractText = abstractClob.getSubString(1, (int) abstractClob.length());
                }
            } catch (SQLException e2) {
                System.err.println("Warning: Could not read abstract for gene position calculation");
                return;
            }
        }

        if (abstractText == null) {
            abstractText = "";
        }

        // Strip leading whitespace from abstract (browse interface displays without it)
        abstractText = abstractText.replaceAll("^\\s+", "");

        // Normalize whitespace within XML tags (newlines and extra spaces become single space)
        // The browse interface renders: <ns1:i> in vitro </ns1:i> instead of:
        // <ns1:i>
        //   in vitro
        // </ns1:i>
        abstractText = abstractText.replaceAll(">\\s+", "> ").replaceAll("\\s+<", " <");

        System.out.println("DEBUG: Title: '" + titleText + "'");
        System.out.println("DEBUG: Abstract (after normalizing) starts with: '" + abstractText.substring(0, Math.min(50, abstractText.length())) + "'");

        // Split genes by " | "
        String[] genes = geneValue.split(" \\| ");
        List<Object> genePositions = new ArrayList<>();
        List<Object> geneCounts = new ArrayList<>();

        System.out.println("DEBUG: Calculating positions for " + genes.length + " genes in title and abstract");

        for (String gene : genes) {
            gene = gene.trim();
            if (gene.isEmpty()) {
                continue;
            }

            List<String> positions = new ArrayList<>();
            int totalCount = 0;

            // Find occurrences in title (section 0)
            if (!titleText.isEmpty()) {
                int searchStart = 0;
                String lowerTitle = titleText.toLowerCase();
                String lowerGene = gene.toLowerCase();

                while (searchStart < titleText.length()) {
                    int index = lowerTitle.indexOf(lowerGene, searchStart);
                    if (index == -1) {
                        break;
                    }

                    int start = index;
                    int end = index + gene.length();
                    positions.add("0;" + start + "-" + end);
                    totalCount++;
                    searchStart = index + 1;
                }
            }

            // Find occurrences in abstract (section 1)
            if (!abstractText.isEmpty()) {
                int searchStart = 0;
                String lowerAbstract = abstractText.toLowerCase();
                String lowerGene = gene.toLowerCase();

                while (searchStart < abstractText.length()) {
                    int index = lowerAbstract.indexOf(lowerGene, searchStart);
                    if (index == -1) {
                        break;
                    }

                    int start = index;
                    int end = index + gene.length();
                    positions.add("1;" + start + "-" + end);
                    totalCount++;
                    searchStart = index + 1;
                }
            }

            // Build position string
            if (positions.isEmpty()) {
                genePositions.add("0;0-0");
                geneCounts.add(0);
                System.out.println("  Gene '" + gene + "': 0 occurrences found");
            } else {
                genePositions.add(String.join("|", positions));
                geneCounts.add(totalCount);
                System.out.println("  Gene '" + gene + "': " + totalCount + " occurrences at " + String.join(", ", positions));
            }
        }

        // Add fields to document with atomic update syntax
        if (!genePositions.isEmpty()) {
            doc.addField("gene_pos", atomicSetList(genePositions));
            System.out.println("DEBUG: Added gene_pos with " + genePositions.size() + " elements");
        }

        // Always update gene_count when calculating gene_pos
        if (!geneCounts.isEmpty()) {
            doc.addField("gene_count", atomicSetList(geneCounts));
            System.out.println("DEBUG: Added gene_count with " + geneCounts.size() + " elements");
        }
    }

    /**
     * Add text field (searchable content)
     */
    private void addTextField(SolrInputDocument doc, ResultSet rs) throws SQLException {
        List<Object> textContent = new ArrayList<>();

        String keywords = rs.getString("keywords");
        if (keywords != null) textContent.add(keywords);

        String meshTerms = rs.getString("mesh_terms");
        if (meshTerms != null) textContent.add(meshTerms);

        String chemicals = rs.getString("chemicals");
        if (chemicals != null) textContent.add(chemicals);

        String title = rs.getString("title");
        if (title != null) textContent.add(title);

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
            textContent.add(abstractText);
        }

        if (!textContent.isEmpty()) {
            doc.addField("text", atomicSetList(textContent));
        }
    }

    /**
     * Helper method to add count fields
     */
    private void addCountFieldIfExists(SolrInputDocument doc, ResultSet rs, String fieldName) {
        try {
            String value = rs.getString(fieldName);
            List<Object> counts = new ArrayList<>();

            // Debug output
            if (fieldName.equals("gene_count")) {
                System.out.println("DEBUG addCountFieldIfExists gene_count raw: '" + value + "'");
            }

            if (value != null && !value.trim().isEmpty()) {
                String[] values = value.split(" \\| ");
                for (String val : values) {
                    val = val.trim();
                    if (!val.isEmpty()) {
                        try {
                            counts.add(Integer.parseInt(val));
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid count value '" + val + "' for field " + fieldName);
                        }
                    }
                }
            }

            if (counts.isEmpty()) {
                counts.add(0);
            }

            doc.addField(fieldName, atomicSetList(counts));
        } catch (SQLException e) {
            List<Object> defaultCount = new ArrayList<>();
            defaultCount.add(0);
            doc.addField(fieldName, atomicSetList(defaultCount));
        }
    }

    /**
     * Helper method to safely add fields from ResultSet to SolrInputDocument
     */
    private void addFieldIfExists(SolrInputDocument doc, ResultSet rs, String fieldName) {
        try {
            String value = rs.getString(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                // Debug output
                if (fieldName.equals("gene_pos") || fieldName.equals("gene") || fieldName.equals("gene_count")) {
                    System.out.println("DEBUG " + fieldName + " raw value from DB: '" + value + "'");
                }

                // Check if the field contains pipe-separated values
                if (fieldName.endsWith("_id") || fieldName.endsWith("_term") ||
                    fieldName.endsWith("_pos") || fieldName.equals("gene")) {
                    // Split by pipe and add each value separately for multi-valued fields
                    List<Object> fieldValues = new ArrayList<>();
                    String[] values = value.split(" \\| ");

                    // Debug output
                    if (fieldName.equals("gene_pos") || fieldName.equals("gene") || fieldName.equals("gene_count")) {
                        System.out.println("DEBUG " + fieldName + " after split - array length: " + values.length);
                        for (int i = 0; i < values.length && i < 5; i++) {
                            System.out.println("  [" + i + "]: '" + values[i] + "'");
                        }
                    }

                    for (String val : values) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            if (isTextField(fieldName)) {
                                val = sanitizeText(val);
                            }
                            fieldValues.add(val);
                        }
                    }
                    if (!fieldValues.isEmpty()) {
                        doc.addField(fieldName, atomicSetList(fieldValues));
                    }
                } else {
                    // Single value field
                    if (isTextField(fieldName)) {
                        value = sanitizeText(value);
                    }
                    doc.addField(fieldName, atomicSet(value));
                }
            }
        } catch (SQLException e) {
            // Field doesn't exist in ResultSet, skip it
            System.err.println("Warning: Field '" + fieldName + "' not found in database");
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

        text = text.replace("?-macroglobulin", "α-macroglobulin");
        text = text.replace("?-2-macroglobulin", "α-2-macroglobulin");
        text = text.replace("factor-?", "factor-β");
        text = text.replace("TGF-?", "TGF-β");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("homologyepatocellular", "hepatocellular");
        text = text.replaceAll("\\?(?![\\s.,;:]|$)", "");

        return text;
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
                    json.append(value);
                }
            }
            first = false;
        }
        json.append("\n}");
        return json.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage:");
            System.err.println("  Single record:  FlexibleFieldUpdater <pmid> <solr_host> <solr_core> <fields>");
            System.err.println("  All records:    FlexibleFieldUpdater --all <solr_host> <solr_core> <fields> [--limit N]");
            System.err.println("  By year:        FlexibleFieldUpdater --year <year> <solr_host> <solr_core> <fields> [--limit N]");
            System.err.println("\nExamples:");
            System.err.println("  FlexibleFieldUpdater 12345678 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count");
            System.err.println("  FlexibleFieldUpdater --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count");
            System.err.println("  FlexibleFieldUpdater --year 2024 dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 100");
            System.err.println("  FlexibleFieldUpdater --all dev.rgd.mcw.edu:8983 ai1 gene,gene_pos,gene_count --limit 1000");
            System.err.println("\nCommon fields:");
            System.err.println("  Basic: title, abstract, p_date, p_year, authors, keywords");
            System.err.println("  Gene: gene, gene_pos, gene_count");
            System.err.println("  Ontology IDs: mp_id, bp_id, vt_id, chebi_id, rs_id, rdo_id, nbo_id, xco_id, so_id, hp_id");
            System.err.println("  Ontology Terms: mp_term, bp_term, vt_term, chebi_term, rs_term, rdo_term, nbo_term, xco_term, so_term, hp_term");
            System.err.println("  Positions: mp_pos, bp_pos, vt_pos, chebi_pos, rs_pos, rdo_pos, nbo_pos, xco_pos, so_pos, hp_pos");
            System.err.println("  Counts: mp_count, bp_count, vt_count, chebi_count, rs_count, rdo_count, nbo_count, xco_count, so_count, hp_count");
            System.err.println("  Organism: organism_common_name, organism_term, organism_ncbi_id, organism_pos, organism_count");
            System.exit(1);
        }

        // Parse arguments
        String mode = args[0];
        Integer limit = null;

        // Check for --limit option
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--limit")) {
                try {
                    limit = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid limit value: " + args[i + 1]);
                    System.exit(1);
                }
                break;
            }
        }

        if (mode.equals("--all")) {
            // Update all records
            if (args.length < 4) {
                System.err.println("Usage: FlexibleFieldUpdater --all <solr_host> <solr_core> <fields> [--limit N]");
                System.exit(1);
            }
            String solrHost = args[1];
            String solrCore = args[2];
            String fields = args[3];

            FlexibleFieldUpdater updater = new FlexibleFieldUpdater(solrHost, solrCore, fields);
            updater.updateAllRecords(limit);

        } else if (mode.equals("--year")) {
            // Update records by year
            if (args.length < 5) {
                System.err.println("Usage: FlexibleFieldUpdater --year <year> <solr_host> <solr_core> <fields> [--limit N]");
                System.exit(1);
            }
            String year = args[1];
            String solrHost = args[2];
            String solrCore = args[3];
            String fields = args[4];

            FlexibleFieldUpdater updater = new FlexibleFieldUpdater(solrHost, solrCore, fields);
            updater.updateRecordsByYear(year, limit);

        } else {
            // Update single record (original behavior)
            String pmid = args[0];
            String solrHost = args[1];
            String solrCore = args[2];
            String fields = args[3];

            FlexibleFieldUpdater updater = new FlexibleFieldUpdater(solrHost, solrCore, fields);
            updater.updateRecord(pmid);
        }

        System.out.println("Update completed!");
    }
}
