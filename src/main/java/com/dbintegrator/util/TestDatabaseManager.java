package com.dbintegrator.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class TestDatabaseManager {
    // String constants for connection URLs
    private static final String SOURCE_URL = "jdbc:h2:mem:sourcedb;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    private static final String DEST_URL = "jdbc:h2:mem:destdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    public static DatabaseConnectionManager getSourceTestConnection() throws SQLException {
        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(
                "localhost", 1521, "sourcedb", USERNAME, PASSWORD
        );

        try (Connection conn = connectionManager.getConnection()) {
            setupSourceTestData(conn);
        } catch (SQLException e) {
            throw new SQLException("Failed to set up source test database", e);
        }

        return connectionManager;
    }

    public static DatabaseConnectionManager getDestTestConnection() throws SQLException {
        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(
                "localhost", 1521, "destdb", USERNAME, PASSWORD
        );

        try (Connection conn = connectionManager.getConnection()) {
            setupDestTestData(conn);
        } catch (SQLException e) {
            throw new SQLException("Failed to set up destination test database", e);
        }

        return connectionManager;
    }

    private static void setupSourceTestData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop table if exists to avoid conflicts
            stmt.execute("DROP TABLE IF EXISTS PROJECTS");

            // Create projects table with Oracle-compatible syntax
            stmt.execute("CREATE TABLE PROJECTS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "name VARCHAR2(100) NOT NULL, " +
                    "description VARCHAR2(500))");

            // Insert sample data
            stmt.execute("INSERT INTO PROJECTS VALUES (1, 'HR System', 'Human Resources Management System')");
            stmt.execute("INSERT INTO PROJECTS VALUES (2, 'Finance App', 'Financial Management Application')");
            stmt.execute("INSERT INTO PROJECTS VALUES (3, 'CRM System', 'Customer Relationship Management')");
            stmt.execute("INSERT INTO PROJECTS VALUES (4, 'ERP System', 'Enterprise Resource Planning')");
            stmt.execute("INSERT INTO PROJECTS VALUES (5, 'Mobile App', 'Mobile Application Development')");
        }
    }

    private static void setupDestTestData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop table if exists to avoid conflicts
            stmt.execute("DROP TABLE IF EXISTS PA_PROJECTS");

            // Create pa_projects table with Oracle-compatible syntax
            stmt.execute("CREATE TABLE PA_PROJECTS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "name VARCHAR2(100) NOT NULL, " +
                    "description VARCHAR2(500))");

            // Insert sample data
            stmt.execute("INSERT INTO PA_PROJECTS VALUES (101, 'HR System - Phase 2', 'HR System Enhancement')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES (102, 'Finance App - 2023', 'Finance App for 2023')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES (103, 'CRM Update', 'CRM System Updates')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES (104, 'ERP Implementation', 'Implementation of ERP System')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES (105, 'Mobile App Release', 'Release of Mobile Application')");
        }
    }

    public static boolean testBasicH2Connection() {
        try {
            Class.forName("org.h2.Driver");
            try (Connection conn = DriverManager.getConnection(
                    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                    USERNAME, PASSWORD)) {
                conn.createStatement().execute("SELECT 1");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}