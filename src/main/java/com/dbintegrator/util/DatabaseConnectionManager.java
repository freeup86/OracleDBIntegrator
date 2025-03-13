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

    public DatabaseConnectionManager(String host, int port, String sid, String username, String password) {
        this.host = host;
        this.port = port;
        this.sid = sid;
        this.username = username;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            connection = DriverManager.getConnection(url, props);
        }
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
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