package com.dbintegrator.service;

import com.dbintegrator.model.Project;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.util.DatabaseConnectionManager;
import com.dbintegrator.model.Task;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseMetadataService {
    private final DatabaseConnectionManager connectionManager;

    public DatabaseMetadataService(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Checks if current connection is to an H2 database
     * @return true if using H2 database
     */
    private boolean isH2Database() {
        try (Connection conn = connectionManager.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            System.out.println("Database product name: " + productName);
            return productName != null && productName.contains("H2");
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the appropriate schema name based on database type
     * @return Schema name to use for metadata queries
     */
    private String getSchemaName() {
        if (isH2Database()) {
            // H2 uses PUBLIC as the default schema
            return "PUBLIC";
        } else {
            // Oracle typically uses the username as schema
            return connectionManager.getUsername().toUpperCase();
        }
    }

    /**
     * Get all available projects from the database
     * @return List of Project objects
     * @throws SQLException if database access error occurs
     */
    public List<Project> getAvailableProjects() throws SQLException {
        List<Project> projects = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();
        String dbName = connection.getCatalog();
        if (dbName == null) {
            dbName = connectionManager.getSid();
        }

        String schemaPattern = getSchemaName();
        System.out.println("Getting tables for schema: " + schemaPattern);

        try (ResultSet tables = metaData.getTables(null, schemaPattern, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String schemaName = tables.getString("TABLE_SCHEM");

                // Skip system tables in Oracle
                if (!isH2Database() && (
                        schemaName.startsWith("SYS") ||
                                schemaName.equals("SYSTEM") ||
                                schemaName.equals("MDSYS") ||
                                schemaName.equals("CTXSYS") ||
                                schemaName.equals("DBSNMP") ||
                                schemaName.equals("OUTLN") ||
                                schemaName.equals("ANONYMOUS") ||
                                schemaName.startsWith("APEX_"))) {
                    continue;
                }

                // Use 0 as placeholder ID, name as the table name, and description as the schema
                projects.add(new Project(0, tableName, schemaName));
            }
        }

        return projects;
    }

    /**
     * Search for projects by name
     * @param searchTerm The search term to match against project names
     * @return List of matching Project objects
     * @throws SQLException if database access error occurs
     */
    public List<Project> searchProjectsByName(String searchTerm) throws SQLException {
        List<Project> allProjects = getAvailableProjects();
        List<Project> matchingProjects = new ArrayList<>();

        String searchTermUpper = searchTerm.toUpperCase();
        for (Project project : allProjects) {
            if (project.getName().toUpperCase().contains(searchTermUpper)) {
                matchingProjects.add(project);
            }
        }

        return matchingProjects;
    }

    /**
     * Get list of table names from the database
     * @return List of table names
     * @throws SQLException if database access error occurs
     */
    public List<String> getTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        String schemaPattern = getSchemaName();
        System.out.println("Getting table names for schema: " + schemaPattern);

        try (ResultSet tables = metaData.getTables(null, schemaPattern, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableNames.add(tableName);
                System.out.println("Found table: " + tableName);
            }
        }

        return tableNames;
    }

    /**
     * Get columns for a specified table
     * @param tableName Name of the table to get columns for
     * @return List of TableColumn objects
     * @throws SQLException if database access error occurs
     */
    // In DatabaseMetadataService.java - add debugging to getTableColumns method
    public List<TableColumn> getTableColumns(String tableName) throws SQLException {
        System.out.println("Getting columns for table: " + tableName);
        List<TableColumn> columns = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        String schemaPattern = getSchemaName();
        System.out.println("Using schema: " + schemaPattern);

        // Try to confirm the table exists first
        try (ResultSet tables = metaData.getTables(null, schemaPattern, tableName.toUpperCase(), new String[]{"TABLE"})) {
            boolean tableExists = false;
            while (tables.next()) {
                tableExists = true;
                String tableNameResult = tables.getString("TABLE_NAME");
                System.out.println("Found table: " + tableNameResult);
            }
            if (!tableExists) {
                System.err.println("WARNING: Table " + tableName + " not found in schema " + schemaPattern);
            }
        }

        try (ResultSet columnsResultSet = metaData.getColumns(null, schemaPattern, tableName.toUpperCase(), null)) {
            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                String dataType = columnsResultSet.getString("TYPE_NAME");
                int size = columnsResultSet.getInt("COLUMN_SIZE");
                boolean nullable = columnsResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;

                columns.add(new TableColumn(columnName, dataType, size, nullable));
                System.out.println("Found column: " + columnName + " (" + dataType + ")");
            }
        }

        if (columns.isEmpty()) {
            System.err.println("WARNING: No columns found for table " + tableName);
        }

        return columns;
    }

    /**
     * Get all tables with their columns
     * @return Map of table names to their column lists
     * @throws SQLException if database access error occurs
     */
    public Map<String, List<TableColumn>> getAllTablesWithColumns() throws SQLException {
        Map<String, List<TableColumn>> tablesWithColumns = new HashMap<>();
        List<String> tableNames = getTableNames();

        for (String tableName : tableNames) {
            List<TableColumn> columns = getTableColumns(tableName);
            tablesWithColumns.put(tableName, columns);
        }

        return tablesWithColumns;
    }

    //methods to retrieve tasks:
    public List<Task> getProjectTasks(int projectId) throws SQLException {
        List<Task> tasks = new ArrayList<>();

        String query = "SELECT id, name, description, status, assignee " +
                "FROM tasks WHERE project_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tasks.add(new Task(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            projectId,
                            rs.getString("status"),
                            rs.getString("assignee")
                    ));
                }
            }
        }

        return tasks;
    }
}