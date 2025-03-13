package com.dbintegrator.service;

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

    public List<String> getTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet tables = metaData.getTables(null, connectionManager.getUsername().toUpperCase(),
                null, new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
        }

        return tableNames;
    }

    public List<TableColumn> getTableColumns(String tableName) throws SQLException {
        List<TableColumn> columns = new ArrayList<>();
        Connection connection = connectionManager.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet columnsResultSet = metaData.getColumns(null,
                connectionManager.getUsername().toUpperCase(), tableName, null)) {
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