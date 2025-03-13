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

            // Retrieve data from source table
            StringBuilder sourceQueryBuilder = new StringBuilder("SELECT ");
            for (int i = 0; i < tableMappings.size(); i++) {
                sourceQueryBuilder.append(tableMappings.get(i).getSourceColumn().getName());
                if (i < tableMappings.size() - 1) {
                    sourceQueryBuilder.append(", ");
                }
            }
            sourceQueryBuilder.append(" FROM ").append(sourceTable);

            // Prepare to update destination table
            StringBuilder updateQueryBuilder = new StringBuilder("UPDATE ").append(destTable).append(" SET ");
            for (int i = 0; i < tableMappings.size(); i++) {
                updateQueryBuilder.append(tableMappings.get(i).getDestinationColumn().getName())
                        .append(" = ?");
                if (i < tableMappings.size() - 1) {
                    updateQueryBuilder.append(", ");
                }
            }
            // In a real application, you'd need a proper WHERE clause to match records
            // This is simplified for demonstration purposes
            updateQueryBuilder.append(" WHERE ROWID = ?");

            try (Connection sourceConn = sourceDbManager.getConnection();
                 Connection destConn = destDbManager.getConnection();
                 Statement sourceStmt = sourceConn.createStatement();
                 ResultSet sourceData = sourceStmt.executeQuery(sourceQueryBuilder.toString())) {

                // Get ROWIDs from destination table for matching
                Map<String, String> destRowIds = getDestinationRowIds(destConn, destTable);

                while (sourceData.next()) {
                    // Get the primary key or unique identifier for matching
                    // This is simplified - you'd need proper record matching logic
                    String sourceKey = String.valueOf(sourceData.getRow());
                    String destRowId = destRowIds.getOrDefault(sourceKey, null);

                    if (destRowId != null) {
                        try (PreparedStatement updateStmt = destConn.prepareStatement(updateQueryBuilder.toString())) {
                            // Set parameter values from source data
                            for (int i = 0; i < tableMappings.size(); i++) {
                                updateStmt.setObject(i + 1, sourceData.getObject(tableMappings.get(i).getSourceColumn().getName()));
                            }
                            updateStmt.setString(tableMappings.size() + 1, destRowId);

                            int rowsUpdated = updateStmt.executeUpdate();
                            totalRowsUpdated += rowsUpdated;
                        }
                    }
                }
            }
        }

        return totalRowsUpdated;
    }

    // In a real application, you'd need proper record matching logic
    // This is simplified for demonstration purposes
    private Map<String, String> getDestinationRowIds(Connection destConn, String destTable) throws SQLException {
        Map<String, String> rowIds = new HashMap<>();
        String query = "SELECT ROWID FROM " + destTable;

        try (Statement stmt = destConn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            int rowNum = 1;
            while (rs.next()) {
                rowIds.put(String.valueOf(rowNum++), rs.getString(1));
            }
        }

        return rowIds;
    }
}