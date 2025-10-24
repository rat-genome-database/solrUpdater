package edu.mcw.rgd.nlp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseConfig {
    private static final Properties properties = new Properties();

    static {
        try {
            // Check for external properties file first
            String externalConfigPath = System.getProperty("config.file", "/data/properties/database.properties");
            File externalFile = new File(externalConfigPath);

            InputStream input = null;

            if (externalFile.exists()) {
                System.out.println("Loading configuration from: " + externalConfigPath);
                input = new FileInputStream(externalFile);
            } else {
                System.out.println("External config not found, loading from classpath: database.properties");
                input = DatabaseConfig.class.getClassLoader().getResourceAsStream("database.properties");
                if (input == null) {
                    throw new RuntimeException("Unable to find database.properties file in classpath or at " + externalConfigPath);
                }
            }

            properties.load(input);
            input.close();

        } catch (IOException e) {
            throw new RuntimeException("Error loading database.properties", e);
        }
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url");
    }

    public static String getDbUsername() {
        return properties.getProperty("db.username");
    }

    public static String getDbPassword() {
        return properties.getProperty("db.password");
    }

    public static String getDefaultSolrUrl() {
        return properties.getProperty("solr.default.url");
    }

    public static int getBatchSize() {
        return Integer.parseInt(properties.getProperty("solr.batch.size", "100"));
    }

    public static int getChunkSize() {
        return Integer.parseInt(properties.getProperty("solr.chunk.size", "10000"));
    }
}