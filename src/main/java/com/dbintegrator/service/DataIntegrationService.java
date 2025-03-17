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

            // Connect to both databases and verify tables and columns exist
            System.out.println("Verifying source table: " + sourceTable);
            try (Connection sourceConn = sourceDbManager.getConnection()) {
                // Verify source table exists
                boolean sourceTableExists = false;
                try (ResultSet tables = sourceConn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (tables.next()) {
                        if (tables.getString("TABLE_NAME").equalsIgnoreCase(sourceTable)) {
                            sourceTableExists = true;
                            break;
                        }
                    }
                }

                if (!sourceTableExists) {
                    System.err.println("Source table " + sourceTable + " does not exist!");
                    continue;
                }

                // Determine the correct ID column name based on the table
                String idColumnName;
                if (sourceTable.equals("HR_ALL_PEOPLE")) {
                    idColumnName = getColumnName(sourceConn, sourceTable, new String[]{"PERSON_ID", "ID"});
                } else if (sourceTable.equals("RSRC")) {
                    idColumnName = getColumnName(sourceConn, sourceTable, new String[]{"RSRC_ID", "ID"});
                } else {
                    idColumnName = "ID";
                }

                if (idColumnName == null) {
                    System.err.println("Could not find ID column in " + sourceTable);
                    continue;
                }

                System.out.println("Using ID column for " + sourceTable + ": " + idColumnName);

                // Continue with integration...
                // Rest of the method...
            }
        }

        return totalRowsUpdated;
    }

    // Helper method to get actual column name from table
    private String getColumnName(Connection conn, String tableName, String[] possibleNames) throws SQLException {
        for (String colName : possibleNames) {
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, tableName, colName)) {
                if (columns.next()) {
                    return columns.getString("COLUMN_NAME");
                }
            }
        }
        return null;
    }
}