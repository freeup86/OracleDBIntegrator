package com.dbintegrator.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dbintegrator.util.DatabaseConnectionManager;

public class ResourceSelectionDialog extends Dialog<Integer> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<String> resourcesListView;

    public ResourceSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;

        setTitle("Select " + (isSource ? "P6" : "EBS") + " Resource");
        setHeaderText("Select a resource from " + tableName);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Resources list
        resourcesListView = new ListView<>();
        resourcesListView.setPrefHeight(300);

        // Add components to content
        content.getChildren().addAll(
                new Label("Available Resources:"),
                resourcesListView
        );

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);

        // Load resources on initialize
        loadResources();
    }

    private void loadResources() {
        List<String> resourceList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            // Comprehensive table discovery
            DatabaseMetaData metaData = conn.getMetaData();

            // Get schema name
            String schema = conn.getSchema();
            System.out.println("Current Schema: " + schema);

            // List all tables with full details
            System.out.println("=== AVAILABLE TABLES ===");
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableSchema = tables.getString("TABLE_SCHEM");
                    String tableType = tables.getString("TABLE_TYPE");

                    System.out.println("Table: " + tableName +
                            " (Schema: " + tableSchema +
                            ", Type: " + tableType + ")");

                    // List columns for each table
                    try (ResultSet columns = metaData.getColumns(null, tableSchema, tableName, "%")) {
                        System.out.println("  Columns for " + tableName + ":");
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            String dataType = columns.getString("TYPE_NAME");
                            System.out.println("    - " + columnName + " (" + dataType + ")");
                        }
                    }
                }
            }

            // Alternate resource table names to try
            String[] possibleTableNames = {
                    tableName,
                    tableName.toUpperCase(),
                    tableName.toLowerCase(),
                    "HR_ALL_PEOPLE",
                    "hr_all_people",
                    "HR_PEOPLE",
                    "EMPLOYEES",
                    "RSRC",
                    "rsrc"
            };

            boolean tableFound = false;
            for (String possibleTable : possibleTableNames) {
                try {
                    // Try to construct a generic query that might work with various table structures
                    String query = "SELECT * FROM " + possibleTable;
                    System.out.println("Attempting query: " + query);

                    try (var stmt = conn.prepareStatement(query);
                         var rs = stmt.executeQuery()) {

                        // Get metadata about the result set
                        var metaData2 = rs.getMetaData();
                        int columnCount = metaData2.getColumnCount();

                        System.out.println("Columns in " + possibleTable + ":");
                        for (int i = 1; i <= columnCount; i++) {
                            System.out.println("  " + metaData2.getColumnName(i) +
                                    " (" + metaData2.getColumnTypeName(i) + ")");
                        }

                        // Attempt to extract resources based on column names
                        int idColumnIndex = -1;
                        int nameColumnIndex = -1;
                        int emailColumnIndex = -1;

                        // Try to find appropriate columns
                        for (int i = 1; i <= columnCount; i++) {
                            String colName = metaData2.getColumnName(i).toLowerCase();
                            if (colName.contains("id") || colName.contains("person_id")) idColumnIndex = i;
                            if (colName.contains("name") || colName.contains("full_name")) nameColumnIndex = i;
                            if (colName.contains("email") || colName.contains("email_address")) emailColumnIndex = i;
                        }

                        // If we found reasonable columns, extract resources
                        if (idColumnIndex != -1 && nameColumnIndex != -1) {
                            while (rs.next()) {
                                int id = rs.getInt(idColumnIndex);
                                String name = rs.getString(nameColumnIndex);
                                String email = emailColumnIndex != -1 ? rs.getString(emailColumnIndex) : "";

                                String displayText = name + " (ID: " + id + ")";
                                if (email != null && !email.isEmpty()) {
                                    displayText += " - " + email;
                                }

                                resourceList.add(displayText);
                            }

                            if (!resourceList.isEmpty()) {
                                System.out.println("Successfully loaded resources from table: " + possibleTable);
                                tableFound = true;
                                break;
                            }
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Failed to query table " + possibleTable + ": " + e.getMessage());
                }
            }

            if (!tableFound) {
                showError("Table Not Found",
                        "Could not find a suitable resource table. " +
                                "Please check your database configuration and ensure the correct table exists.");
            }

        } catch (SQLException e) {
            showError("Database Error", "Failed to load resources: " + e.getMessage());
            e.printStackTrace();
        }

        resourcesListView.setItems(FXCollections.observableArrayList(resourceList));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}