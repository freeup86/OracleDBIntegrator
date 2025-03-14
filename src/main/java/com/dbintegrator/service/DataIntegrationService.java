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
}