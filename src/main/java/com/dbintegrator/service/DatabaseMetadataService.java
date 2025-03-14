package com.dbintegrator.service;

import com.dbintegrator.model.Project;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.util.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            return conn.getMetaData().getDatabaseProductName().contains("H2");
        } catch (SQLException e) {
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

        try (ResultSet tables = metaData.getTables(null, schemaPattern, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
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
    public List<TableColumn> getTableColumns(String tableName) throws SQLException {
        List<TableColumn> columns = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        String schemaPattern = getSchemaName();

        try (ResultSet columnsResultSet = metaData.getColumns(null, schemaPattern, tableName, null)) {
            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                String dataType = columnsResultSet.getString("TYPE_NAME");
                int size = columnsResultSet.getInt("COLUMN_SIZE");
                boolean nullable = columnsResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;

                columns.add(new TableColumn(columnName, dataType, size, nullable));
            }
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
}