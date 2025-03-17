package com.dbintegrator.service;

import com.dbintegrator.model.ColumnMapping;
import com.dbintegrator.util.DatabaseConnectionManager;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DataIntegrationService {
    private final DatabaseConnectionManager sourceDbManager;
    private final DatabaseConnectionManager destDbManager;

    public DataIntegrationService(DatabaseConnectionManager sourceDbManager,
                                  DatabaseConnectionManager destDbManager) {
        this.sourceDbManager = sourceDbManager;
        this.destDbManager = destDbManager;
    }

    public int integrateData(List<ColumnMapping> mappings) throws SQLException {
        return integrateData(mappings, null, null);
    }

    public int integrateData(List<ColumnMapping> mappings,
                             String sourceWhereClause, String destWhereClause) throws SQLException {
        if (mappings.isEmpty()) {
            return 0;
        }

        // Group mappings by table pairs
        Map<String, List<ColumnMapping>> tableGroupedMappings = new HashMap<>();
        for (ColumnMapping mapping : mappings) {
            String key = mapping.getSourceTable() + "->" + mapping.getDestinationTable();
            if (!tableGroupedMappings.containsKey(key)) {
                tableGroupedMappings.put(key, new ArrayList<>());
            }
            tableGroupedMappings.get(key).add(mapping);
        }

        int totalRowsUpdated = 0;

        // Process each table pair
        for (Map.Entry<String, List<ColumnMapping>> entry : tableGroupedMappings.entrySet()) {
            List<ColumnMapping> tableMappings = entry.getValue();
            ColumnMapping firstMapping = tableMappings.get(0);
            String sourceTable = firstMapping.getSourceTable();
            String destTable = firstMapping.getDestinationTable();

            // Retrieve data from source table with the source WHERE clause
            StringBuilder sourceQueryBuilder = new StringBuilder("SELECT ");

            // Always include ID for reference
            sourceQueryBuilder.append("ID, ");

            for (int i = 0; i < tableMappings.size(); i++) {
                sourceQueryBuilder.append(tableMappings.get(i).getSourceColumn().getName());
                if (i < tableMappings.size() - 1) {
                    sourceQueryBuilder.append(", ");
                }
            }
            sourceQueryBuilder.append(" FROM ").append(sourceTable);

            if (sourceWhereClause != null && !sourceWhereClause.isEmpty()) {
                sourceQueryBuilder.append(" WHERE ").append(sourceWhereClause);
            }

            // Log the operation
            System.out.println("Executing source query: " + sourceQueryBuilder);

            try (Connection sourceConn = sourceDbManager.getConnection();
                 Statement sourceStmt = sourceConn.createStatement();
                 ResultSet sourceData = sourceStmt.executeQuery(sourceQueryBuilder.toString())) {

                // Build the update query with destination WHERE clause
                StringBuilder updateQueryBuilder = new StringBuilder("UPDATE ")
                        .append(destTable)
                        .append(" SET ");

                for (int i = 0; i < tableMappings.size(); i++) {
                    updateQueryBuilder.append(tableMappings.get(i).getDestinationColumn().getName())
                            .append(" = ?");
                    if (i < tableMappings.size() - 1) {
                        updateQueryBuilder.append(", ");
                    }
                }

                if (destWhereClause != null && !destWhereClause.isEmpty()) {
                    updateQueryBuilder.append(" WHERE ").append(destWhereClause);
                } else {
                    // Safety measure - don't update everything if no WHERE clause
                    updateQueryBuilder.append(" WHERE ROWNUM = 1");
                }

                // Log the update query
                System.out.println("Preparing update query: " + updateQueryBuilder);

                if (sourceData.next()) {
                    try (Connection destConn = destDbManager.getConnection();
                         PreparedStatement updateStmt = destConn.prepareStatement(updateQueryBuilder.toString())) {

                        // Set values from source to destination
                        for (int i = 0; i < tableMappings.size(); i++) {
                            updateStmt.setObject(i + 1,
                                    sourceData.getObject(tableMappings.get(i).getSourceColumn().getName()));
                        }

                        int rowsUpdated = updateStmt.executeUpdate();
                        totalRowsUpdated += rowsUpdated;
                    }
                } else {
                    System.out.println("No source data found for the specified criteria.");
                }
            }
        }

        return totalRowsUpdated;
    }

    public int mergeData(List<ColumnMapping> mappings,
                         String sourceWhereClause,
                         String destMatchColumn,
                         String sourceMatchColumn) throws SQLException {
        if (mappings.isEmpty()) {
            return 0;
        }

        // Group mappings by table pairs
        Map<String, List<ColumnMapping>> tableGroupedMappings = new HashMap<>();
        for (ColumnMapping mapping : mappings) {
            String key = mapping.getSourceTable() + "->" + mapping.getDestinationTable();
            if (!tableGroupedMappings.containsKey(key)) {
                tableGroupedMappings.put(key, new ArrayList<>());
            }
            tableGroupedMappings.get(key).add(mapping);
        }

        int totalRowsMerged = 0;

        // Process each table pair
        for (Map.Entry<String, List<ColumnMapping>> entry : tableGroupedMappings.entrySet()) {
            List<ColumnMapping> tableMappings = entry.getValue();
            ColumnMapping firstMapping = tableMappings.get(0);
            String sourceTable = firstMapping.getSourceTable();
            String destTable = firstMapping.getDestinationTable();

            // Determine the correct ID column names based on the tables
            String sourceIdColumn;
            if (sourceTable.equals("HR_ALL_PEOPLE")) {
                sourceIdColumn = "PERSON_ID";
            } else if (sourceTable.equals("RSRC")) {
                sourceIdColumn = "RSRC_ID";
            } else {
                sourceIdColumn = "ID";
            }

            String destIdColumn;
            if (destTable.equals("HR_ALL_PEOPLE")) {
                destIdColumn = "PERSON_ID";
            } else if (destTable.equals("RSRC")) {
                destIdColumn = "RSRC_ID";
            } else {
                destIdColumn = "ID";
            }

            // Retrieve data from source table with the source WHERE clause
            StringBuilder sourceQueryBuilder = new StringBuilder("SELECT ");
            sourceQueryBuilder.append(sourceIdColumn).append(", ");
            sourceQueryBuilder.append(sourceMatchColumn).append(", ");

            // Add all the column names for the mapping
            for (int i = 0; i < tableMappings.size(); i++) {
                sourceQueryBuilder.append(tableMappings.get(i).getSourceColumn().getName());
                if (i < tableMappings.size() - 1) {
                    sourceQueryBuilder.append(", ");
                }
            }
            sourceQueryBuilder.append(" FROM ").append(sourceTable);

            if (sourceWhereClause != null && !sourceWhereClause.isEmpty()) {
                sourceQueryBuilder.append(" WHERE ").append(sourceWhereClause);
            }

            // Log the operation
            System.out.println("Executing source query: " + sourceQueryBuilder);

            try (Connection sourceConn = sourceDbManager.getConnection();
                 Statement sourceStmt = sourceConn.createStatement();
                 ResultSet sourceData = sourceStmt.executeQuery(sourceQueryBuilder.toString())) {

                Connection destConn = destDbManager.getConnection();
                destConn.setAutoCommit(false); // Start transaction for better performance

                try {
                    while (sourceData.next()) {
                        // For each source row, check if it exists in destination
                        String matchValue = sourceData.getString(sourceMatchColumn);

                        // Check if a record with this match value exists in destination
                        String checkQuery = "SELECT " + destIdColumn + " FROM " + destTable +
                                " WHERE " + destMatchColumn + " = ?";

                        boolean recordExists = false;
                        int existingId = -1;

                        try (PreparedStatement checkStmt = destConn.prepareStatement(checkQuery)) {
                            checkStmt.setString(1, matchValue);
                            try (ResultSet checkResult = checkStmt.executeQuery()) {
                                if (checkResult.next()) {
                                    recordExists = true;
                                    existingId = checkResult.getInt(destIdColumn);
                                }
                            }
                        }

                        if (recordExists) {
                            // UPDATE existing record
                            StringBuilder updateQueryBuilder = new StringBuilder("UPDATE ")
                                    .append(destTable)
                                    .append(" SET ");

                            for (int i = 0; i < tableMappings.size(); i++) {
                                updateQueryBuilder.append(tableMappings.get(i).getDestinationColumn().getName())
                                        .append(" = ?");
                                if (i < tableMappings.size() - 1) {
                                    updateQueryBuilder.append(", ");
                                }
                            }

                            updateQueryBuilder.append(" WHERE ").append(destIdColumn).append(" = ?");

                            // Log the update query
                            System.out.println("Updating existing record: " + updateQueryBuilder.toString().replace("?", "..."));

                            try (PreparedStatement updateStmt = destConn.prepareStatement(updateQueryBuilder.toString())) {
                                // Set values from source to destination
                                for (int i = 0; i < tableMappings.size(); i++) {
                                    updateStmt.setObject(i + 1,
                                            sourceData.getObject(tableMappings.get(i).getSourceColumn().getName()));
                                }

                                // Set WHERE clause parameter
                                updateStmt.setInt(tableMappings.size() + 1, existingId);

                                int rowsUpdated = updateStmt.executeUpdate();
                                totalRowsMerged += rowsUpdated;
                                System.out.println("Updated " + rowsUpdated + " rows with ID: " + existingId);
                            }
                        } else {
                            // INSERT new record - carefully handle potential duplicate columns
                            StringBuilder insertQueryBuilder = new StringBuilder("INSERT INTO ")
                                    .append(destTable)
                                    .append(" (").append(destIdColumn).append(", ")
                                    .append(destMatchColumn);

                            // Keep track of columns we've already included
                            java.util.Set<String> includedColumns = new java.util.HashSet<>();
                            includedColumns.add(destIdColumn);
                            includedColumns.add(destMatchColumn);

                            // Add all destination column names, avoiding duplicates
                            for (ColumnMapping mapping : tableMappings) {
                                String destColName = mapping.getDestinationColumn().getName();
                                if (!includedColumns.contains(destColName)) {
                                    insertQueryBuilder.append(", ").append(destColName);
                                    includedColumns.add(destColName);
                                }
                            }

                            insertQueryBuilder.append(") VALUES (?, ?");

                            // Add placeholders for each non-duplicate column
                            for (ColumnMapping mapping : tableMappings) {
                                String destColName = mapping.getDestinationColumn().getName();
                                if (!destColName.equals(destIdColumn) && !destColName.equals(destMatchColumn)) {
                                    // Only add a parameter if we added the column to the column list
                                    if (includedColumns.contains(destColName)) {
                                        insertQueryBuilder.append(", ?");
                                    }
                                }
                            }

                            insertQueryBuilder.append(")");

                            // Log the insert query
                            System.out.println("Inserting new record: " + insertQueryBuilder.toString().replace("?", "..."));

                            try (PreparedStatement insertStmt = destConn.prepareStatement(insertQueryBuilder.toString())) {
                                // Generate a new ID for the destination
                                // This is a simple approach - in a real system, you might want a more sophisticated ID generation
                                int newId = (int)(Math.random() * 10000) + 1000; // Random ID between 1000 and 11000

                                // Set ID and match column
                                insertStmt.setInt(1, newId);
                                insertStmt.setString(2, matchValue);

                                // Set all mapped column values, avoiding duplicates
                                int paramIndex = 3;
                                for (ColumnMapping mapping : tableMappings) {
                                    String destColName = mapping.getDestinationColumn().getName();
                                    // Skip columns that were skipped in the column list
                                    if (!destColName.equals(destIdColumn) && !destColName.equals(destMatchColumn)) {
                                        // Only set if we included the column
                                        if (includedColumns.contains(destColName)) {
                                            insertStmt.setObject(paramIndex++,
                                                    sourceData.getObject(mapping.getSourceColumn().getName()));
                                        }
                                    }
                                }

                                int rowsInserted = insertStmt.executeUpdate();
                                totalRowsMerged += rowsInserted;
                                System.out.println("Inserted " + rowsInserted + " new rows with ID: " + newId);
                            }
                        }
                    }

                    // Commit the transaction
                    destConn.commit();

                } catch (SQLException e) {
                    // Rollback on error
                    try {
                        destConn.rollback();
                    } catch (SQLException ex) {
                        System.err.println("Error rolling back transaction: " + ex.getMessage());
                    }
                    throw e;
                } finally {
                    // Reset auto-commit and close the connection
                    try {
                        destConn.setAutoCommit(true);
                        destConn.close();
                    } catch (SQLException ex) {
                        System.err.println("Error closing destination connection: " + ex.getMessage());
                    }
                }
            }
        }

        return totalRowsMerged;
    }
}