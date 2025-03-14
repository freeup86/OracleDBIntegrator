package com.dbintegrator.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;

public class ConfigurationManager {
    private static final String CONFIG_FILE = "db_connections.properties";
    // Simple secret key for basic encryption - in a production app, use more secure key management
    private static final String SECRET_KEY = "OracleDBIntegrator";
    private static final String SALT = "IntegratorSalt";

    // Property keys
    private static final String SOURCE_HOST = "source.host";
    private static final String SOURCE_PORT = "source.port";
    private static final String SOURCE_SID = "source.sid";
    private static final String SOURCE_USERNAME = "source.username";
    private static final String SOURCE_PASSWORD = "source.password";

    private static final String DEST_HOST = "dest.host";
    private static final String DEST_PORT = "dest.port";
    private static final String DEST_SID = "dest.sid";
    private static final String DEST_USERNAME = "dest.username";
    private static final String DEST_PASSWORD = "dest.password";

    private Properties properties;

    public ConfigurationManager() {
        properties = new Properties();
        loadConfiguration();
    }

    private void loadConfiguration() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (Exception e) {
                System.err.println("Error loading configuration: " + e.getMessage());
            }
        }
    }

    public void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Database Connection Configuration");
        } catch (Exception e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }

    // Saves source database connection details
    public void saveSourceConnection(String host, int port, String sid, String username, String password) {
        properties.setProperty(SOURCE_HOST, host);
        properties.setProperty(SOURCE_PORT, String.valueOf(port));
        properties.setProperty(SOURCE_SID, sid);
        properties.setProperty(SOURCE_USERNAME, username);
        properties.setProperty(SOURCE_PASSWORD, encrypt(password));
        saveConfiguration();
    }

    // Saves destination database connection details
    public void saveDestConnection(String host, int port, String sid, String username, String password) {
        properties.setProperty(DEST_HOST, host);
        properties.setProperty(DEST_PORT, String.valueOf(port));
        properties.setProperty(DEST_SID, sid);
        properties.setProperty(DEST_USERNAME, username);
        properties.setProperty(DEST_PASSWORD, encrypt(password));
        saveConfiguration();
    }

    // Gets source connection details
    public DatabaseConnectionManager getSourceConnection() {
        if (!hasSourceConnection()) {
            return null;
        }

        String host = properties.getProperty(SOURCE_HOST);
        int port = Integer.parseInt(properties.getProperty(SOURCE_PORT));
        String sid = properties.getProperty(SOURCE_SID);
        String username = properties.getProperty(SOURCE_USERNAME);
        String password = decrypt(properties.getProperty(SOURCE_PASSWORD));

        return new DatabaseConnectionManager(host, port, sid, username, password);
    }

    // Gets destination connection details
    public DatabaseConnectionManager getDestConnection() {
        if (!hasDestConnection()) {
            return null;
        }

        String host = properties.getProperty(DEST_HOST);
        int port = Integer.parseInt(properties.getProperty(DEST_PORT));
        String sid = properties.getProperty(DEST_SID);
        String username = properties.getProperty(DEST_USERNAME);
        String password = decrypt(properties.getProperty(DEST_PASSWORD));

        return new DatabaseConnectionManager(host, port, sid, username, password);
    }

    // Checks if source connection details exist
    public boolean hasSourceConnection() {
        return properties.containsKey(SOURCE_HOST) &&
                properties.containsKey(SOURCE_PORT) &&
                properties.containsKey(SOURCE_SID) &&
                properties.containsKey(SOURCE_USERNAME) &&
                properties.containsKey(SOURCE_PASSWORD);
    }

    // Checks if destination connection details exist
    public boolean hasDestConnection() {
        return properties.containsKey(DEST_HOST) &&
                properties.containsKey(DEST_PORT) &&
                properties.containsKey(DEST_SID) &&
                properties.containsKey(DEST_USERNAME) &&
                properties.containsKey(DEST_PASSWORD);
    }

    // Basic encryption for storing passwords
    private String encrypt(String property) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);

            byte[] encrypted = cipher.doFinal(property.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            System.err.println("Error encrypting property: " + e.getMessage());
            return property;
        }
    }

    // Decrypt stored passwords
    private String decrypt(String property) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secret);

            byte[] decoded = Base64.getDecoder().decode(property);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error decrypting property: " + e.getMessage());
            return property;
        }
    }
}