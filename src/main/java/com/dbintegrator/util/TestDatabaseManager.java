package com.dbintegrator.util;

import java.sql.*;

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

            // Debug print
            printProjectTableContents(connectionManager, "PROJECTS");
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

            // Debug print
            printProjectTableContents(connectionManager, "PA_PROJECTS");
        } catch (SQLException e) {
            throw new SQLException("Failed to set up destination test database", e);
        }

        return connectionManager;
    }

    private static void setupSourceTestData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing tables to avoid conflicts
            stmt.execute("DROP TABLE IF EXISTS TASKS");
            stmt.execute("DROP TABLE IF EXISTS PROJECTS");

            // Create projects table with more detailed projects
            stmt.execute("CREATE TABLE PROJECTS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "name VARCHAR2(100) NOT NULL, " +
                    "description VARCHAR2(500))");

            // Insert expanded set of sample projects
            stmt.execute("INSERT INTO PROJECTS VALUES " +
                    "(1, 'Enterprise Resource Planning (ERP) Implementation', " +
                    "'Comprehensive ERP system rollout across organization')");
            stmt.execute("INSERT INTO PROJECTS VALUES " +
                    "(2, 'Customer Relationship Management (CRM) Upgrade', " +
                    "'Modernizing customer engagement and tracking system')");
            stmt.execute("INSERT INTO PROJECTS VALUES " +
                    "(3, 'Supply Chain Optimization Project', " +
                    "'Improving logistics and supply chain efficiency')");
            stmt.execute("INSERT INTO PROJECTS VALUES " +
                    "(4, 'Digital Transformation Initiative', " +
                    "'Comprehensive digital strategy and implementation')");
            stmt.execute("INSERT INTO PROJECTS VALUES " +
                    "(5, 'Cybersecurity Enhancement Program', " +
                    "'Upgrading and fortifying organizational cybersecurity infrastructure')");

            // Create tasks table
            stmt.execute("CREATE TABLE TASKS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "project_id NUMBER, " +
                    "name VARCHAR2(255) NOT NULL, " +
                    "description VARCHAR2(500), " +
                    "status VARCHAR2(50), " +
                    "assignee VARCHAR2(100), " +
                    "priority VARCHAR2(50), " +
                    "start_date DATE, " +
                    "end_date DATE, " +
                    "FOREIGN KEY (project_id) REFERENCES PROJECTS(id))");

            // Insert sample tasks for each project
            // Project 1: ERP Implementation
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(1, 1, 'Requirements Analysis', " +
                    "'Conduct comprehensive requirements gathering for ERP implementation', " +
                    "'In Progress', 'John Anderson', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 30)");
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(2, 1, 'System Design', " +
                    "'Create detailed technical design for ERP system', " +
                    "'Planned', 'Emily Roberts', 'High', " +
                    "CURRENT_DATE + 31, CURRENT_DATE + 60)");

            // Project 2: CRM Upgrade
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(3, 2, 'Current System Audit', " +
                    "'Analyze existing CRM system capabilities and limitations', " +
                    "'Not Started', 'Michael Chen', 'Medium', " +
                    "CURRENT_DATE, CURRENT_DATE + 45)");
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(4, 2, 'New Features Design', " +
                    "'Develop specification for new CRM features', " +
                    "'Pending', 'Sarah Kim', 'High', " +
                    "CURRENT_DATE + 46, CURRENT_DATE + 90)");

            // Project 3: Supply Chain Optimization
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(5, 3, 'Vendor Analysis', " +
                    "'Evaluate current supply chain vendors and performance', " +
                    "'In Progress', 'David Martinez', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 40)");
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(6, 3, 'Process Mapping', " +
                    "'Create detailed process flow for supply chain operations', " +
                    "'Planned', 'Lisa Wong', 'Medium', " +
                    "CURRENT_DATE + 41, CURRENT_DATE + 70)");

            // Project 4: Digital Transformation
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(7, 4, 'Digital Strategy Development', " +
                    "'Create comprehensive digital transformation roadmap', " +
                    "'Not Started', 'Alex Rodriguez', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 60)");
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(8, 4, 'Technology Assessment', " +
                    "'Evaluate emerging technologies for digital transformation', " +
                    "'Pending', 'Rachel Green', 'Medium', " +
                    "CURRENT_DATE + 61, CURRENT_DATE + 90)");

            // Project 5: Cybersecurity Enhancement
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(9, 5, 'Security Vulnerability Assessment', " +
                    "'Conduct comprehensive security vulnerability scan', " +
                    "'In Progress', 'Thomas Wilson', 'Critical', " +
                    "CURRENT_DATE, CURRENT_DATE + 30)");
            stmt.execute("INSERT INTO TASKS VALUES " +
                    "(10, 5, 'Security Policy Review', " +
                    "'Update and revise organizational security policies', " +
                    "'Planned', 'Jennifer Lopez', 'High', " +
                    "CURRENT_DATE + 31, CURRENT_DATE + 60)");
        }
    }

    private static void setupDestTestData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing tables to avoid conflicts
            stmt.execute("DROP TABLE IF EXISTS PA_TASKS");
            stmt.execute("DROP TABLE IF EXISTS PA_PROJECTS");

            // Create projects table with corresponding projects
            stmt.execute("CREATE TABLE PA_PROJECTS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "name VARCHAR2(100) NOT NULL, " +
                    "description VARCHAR2(500))");

            // Insert corresponding projects with slightly different names
            stmt.execute("INSERT INTO PA_PROJECTS VALUES " +
                    "(101, 'Enterprise Resource Planning System', " +
                    "'Strategic ERP system implementation and integration')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES " +
                    "(102, 'Advanced CRM Solution', " +
                    "'Next-generation customer relationship management platform')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES " +
                    "(103, 'Supply Chain Management Optimization', " +
                    "'Advanced logistics and supply chain efficiency project')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES " +
                    "(104, 'Digital Strategy Execution', " +
                    "'Comprehensive digital transformation and innovation initiative')");
            stmt.execute("INSERT INTO PA_PROJECTS VALUES " +
                    "(105, 'Enterprise Cybersecurity Program', " +
                    "'Advanced cybersecurity infrastructure and protection strategy')");

            // Create tasks table
            stmt.execute("CREATE TABLE PA_TASKS (" +
                    "id NUMBER PRIMARY KEY, " +
                    "project_id NUMBER, " +
                    "name VARCHAR2(255) NOT NULL, " +
                    "description VARCHAR2(500), " +
                    "status VARCHAR2(50), " +
                    "assignee VARCHAR2(100), " +
                    "priority VARCHAR2(50), " +
                    "start_date DATE, " +
                    "end_date DATE, " +
                    "FOREIGN KEY (project_id) REFERENCES PA_PROJECTS(id))");

            // Insert corresponding tasks with slight variations
            // Project 101: ERP System
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(101, 101, 'Initial ERP Requirements Review', " +
                    "'Validate and refine ERP implementation requirements', " +
                    "'In Review', 'Emma Thompson', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 30)");
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(102, 101, 'ERP Architecture Planning', " +
                    "'Develop comprehensive system architecture framework', " +
                    "'Planned', 'Ryan Clark', 'High', " +
                    "CURRENT_DATE + 31, CURRENT_DATE + 60)");

            // Project 102: CRM Solution
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(103, 102, 'CRM System Evaluation', " +
                    "'Comprehensive analysis of current CRM capabilities', " +
                    "'Not Started', 'Olivia Martin', 'Medium', " +
                    "CURRENT_DATE, CURRENT_DATE + 45)");
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(104, 102, 'CRM Enhancement Specification', " +
                    "'Develop detailed specifications for CRM improvements', " +
                    "'Pending', 'Ethan Brooks', 'High', " +
                    "CURRENT_DATE + 46, CURRENT_DATE + 90)");

            // Project 103: Supply Chain Management
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(105, 103, 'Vendor Performance Analysis', " +
                    "'Detailed assessment of supply chain vendor performance', " +
                    "'In Progress', 'Sophie Turner', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 40)");
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(106, 103, 'Logistics Process Optimization', " +
                    "'Develop strategies for improving supply chain efficiency', " +
                    "'Planned', 'Lucas Anderson', 'Medium', " +
                    "CURRENT_DATE + 41, CURRENT_DATE + 70)");

            // Project 104: Digital Strategy
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(107, 104, 'Digital Transformation Strategy', " +
                    "'Create comprehensive digital innovation roadmap', " +
                    "'Not Started', 'Isabella Garcia', 'High', " +
                    "CURRENT_DATE, CURRENT_DATE + 60)");
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(108, 104, 'Emerging Technology Assessment', " +
                    "'Comprehensive review of potential transformative technologies', " +
                    "'Pending', 'Noah Martinez', 'Medium', " +
                    "CURRENT_DATE + 61, CURRENT_DATE + 90)");

            // Project 105: Cybersecurity Program
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(109, 105, 'Security Vulnerability Diagnostics', " +
                    "'In-depth security vulnerability and risk assessment', " +
                    "'In Progress', 'Ava Wilson', 'Critical', " +
                    "CURRENT_DATE, CURRENT_DATE + 30)");
            stmt.execute("INSERT INTO PA_TASKS VALUES " +
                    "(110, 105, 'Cybersecurity Policy Modernization', " +
                    "'Comprehensive review and update of security policies', " +
                    "'Planned', 'Mason Taylor', 'High', " +
                    "CURRENT_DATE + 31, CURRENT_DATE + 60)");
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

    public static void printProjectTableContents(DatabaseConnectionManager dbManager, String tableName) {
        try {
            System.out.println("Printing contents of table: " + tableName);
            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, description FROM " + tableName)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("Project " + count + ": " +
                            "ID=" + rs.getInt("id") +
                            ", Name=" + rs.getString("name") +
                            ", Description=" + rs.getString("description"));
                }

                if (count == 0) {
                    System.err.println("NO PROJECTS FOUND IN TABLE: " + tableName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error printing project table contents:");
            e.printStackTrace();
        }
    }
}