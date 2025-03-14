package com.dbintegrator.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnectionManager {
    private String host;
    private int port;
    private String sid;
    private String username;
    private String password;
    private Connection connection;
    private String connectionUrl;

    public DatabaseConnectionManager(String host, int port, String sid, String username, String password) {
        this.host = host;
        this.port = port;
        this.sid = sid;
        this.username = username;
        this.password = password;

        // For H2 in-memory databases, use a different connection URL
        if (sid.equals("sourcedb") || sid.equals("destdb")) {
            this.connectionUrl = "jdbc:h2:mem:" + sid + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        } else {
            // Standard Oracle connection URL
            this.connectionUrl = String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);
        }
    }

    public Connection getConnection() throws SQLException {
        // Always create a new connection to avoid closed database issues
        try {
            // Ensure driver is loaded
            if (connectionUrl.contains("h2:mem:")) {
                Class.forName("org.h2.Driver");
            } else {
                Class.forName("oracle.jdbc.driver.OracleDriver");
            }

            // Create new connection with specific properties
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);

            connection = DriverManager.getConnection(connectionUrl, props);
            return connection;
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found", e);
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSid() {
        return sid;
    }

    public String getUsername() {
        return username;
    }

    public String getConnectionInfo() {
        return String.format("%s@%s:%d/%s", username, host, port, sid);
    }
}