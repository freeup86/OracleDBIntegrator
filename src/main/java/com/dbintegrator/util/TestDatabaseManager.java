package com.dbintegrator.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class TestDatabaseManager {

    public static DatabaseConnectionManager getSourceTestConnection() throws SQLException {
        // Create an in-memory H2 database with Oracle mode
        String url = "jdbc:h2:mem:sourcedb;MODE=Oracle;DB_CLOSE_DELAY=-1";
        String username = "sa";
        String password = "";

        Connection conn = DriverManager.getConnection(url, username, password);
        setupSourceTestData(conn);

        return new DatabaseConnectionManager("localhost", 1521, "sourcedb", username, password) {
            @Override
            public Connection getConnection() throws SQLException {
                return conn;
            }
        };
    }

    public static DatabaseConnectionManager getDestTestConnection() throws SQLException {
        // Create another in-memory H2 database for destination
        String url = "jdbc:h2:mem:destdb;MODE=Oracle;DB_CLOSE_DELAY=-1";
        String username = "sa";
        String password = "";

        Connection conn = DriverManager.getConnection(url, username, password);
        setupDestTestData(conn);

        return new DatabaseConnectionManager("localhost", 1521, "destdb", username, password) {
            @Override
            public Connection getConnection() throws SQLException {
                return conn;
            }
        };
    }

    private static void setupSourceTestData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Create projects table
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
            // Create pa_projects table
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
}