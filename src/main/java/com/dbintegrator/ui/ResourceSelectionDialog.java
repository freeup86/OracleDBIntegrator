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
    private final boolean isSource;

    public ResourceSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;
        this.isSource = isSource;

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

        // Set the buttons
        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        // Set the result converter to extract the ID from the selected item
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                String selectedItem = resourcesListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.contains("ID:")) {
                    try {
                        // Extract the ID more reliably
                        int startIndex = selectedItem.indexOf("ID:") + 3;
                        int endIndex = selectedItem.indexOf(")", startIndex);
                        if (endIndex > startIndex) {
                            String idStr = selectedItem.substring(startIndex, endIndex).trim();
                            int id = Integer.parseInt(idStr);
                            System.out.println("Selected resource ID: " + id);
                            return id;
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing resource ID: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            return -1;
        });

        // Only load resources if we have a valid database connection
        if (dbManager != null) {
            loadResources();
        } else {
            resourcesListView.setItems(FXCollections.observableArrayList(
                    "No database connection available"));
        }
    }

    private void loadResources() {
        List<String> resourceList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            // First, check what tables actually exist
            List<String> existingTables = new ArrayList<>();
            try (ResultSet tables = conn.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    existingTables.add(tables.getString("TABLE_NAME").toUpperCase());
                    System.out.println("Found table: " + tables.getString("TABLE_NAME"));
                }
            }

            // Now we know what tables are actually available
            boolean tableExists = existingTables.contains(tableName.toUpperCase());

            if (tableExists) {
                // Table exists, load the resources based on table type
                if (tableName.equals("HR_ALL_PEOPLE")) {
                    loadEBSResources();
                } else if (tableName.equals("RSRC")) {
                    loadP6Resources();
                } else {
                    // Generic approach for other tables
                    loadGenericResources();
                }
            } else {
                // Table doesn't exist, try a fallback
                System.out.println(tableName + " table not found, trying fallback");
                if (tableName.equals("HR_ALL_PEOPLE")) {
                    // Try to use RSRC data as a fallback for HR_ALL_PEOPLE
                    if (existingTables.contains("RSRC")) {
                        System.out.println("HR_ALL_PEOPLE table not found, using RSRC as a fallback");
                        loadP6Resources();
                    }
                } else if (tableName.equals("RSRC")) {
                    // Try to use HR_ALL_PEOPLE data as a fallback for RSRC
                    if (existingTables.contains("HR_ALL_PEOPLE")) {
                        System.out.println("RSRC table not found, using HR_ALL_PEOPLE as a fallback");
                        loadEBSResources();
                    }
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to load resources: " + e.getMessage());
            e.printStackTrace();
        }

        if (resourceList.isEmpty()) {
            resourcesListView.setItems(FXCollections.observableArrayList("No resources found"));
        }
    }

    private void loadP6Resources() {
        List<String> resourceList = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // First determine if we're using RSRC_ID or ID
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, "RSRC", null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toUpperCase());
                }
            }

            String idColumn = columnNames.contains("RSRC_ID") ? "RSRC_ID" : "ID";
            String query = "SELECT " + idColumn + ", NAME, EMAIL FROM RSRC";

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    int id = rs.getInt(idColumn);
                    String name = rs.getString("NAME");
                    String email = rs.getString("EMAIL");

                    String displayText = name + " (ID: " + id + ")";
                    if (email != null && !email.isEmpty()) {
                        displayText += " - " + email;
                    }

                    resourceList.add(displayText);
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to load P6 resources: " + e.getMessage());
            e.printStackTrace();
        }

        resourcesListView.setItems(FXCollections.observableArrayList(resourceList));
    }

    private void loadEBSResources() {
        List<String> resourceList = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // First determine if we're using PERSON_ID or ID
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, "HR_ALL_PEOPLE", null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toUpperCase());
                }
            }

            String idColumn = columnNames.contains("PERSON_ID") ? "PERSON_ID" : "ID";
            String nameColumn = columnNames.contains("FULL_NAME") ? "FULL_NAME" : "NAME";
            String emailColumn = columnNames.contains("EMAIL_ADDRESS") ? "EMAIL_ADDRESS" : "EMAIL";

            String query = "SELECT " + idColumn + ", " + nameColumn + ", " + emailColumn + " FROM HR_ALL_PEOPLE";

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    int id = rs.getInt(idColumn);
                    String name = rs.getString(nameColumn);
                    String email = rs.getString(emailColumn);

                    String displayText = name + " (ID: " + id + ")";
                    if (email != null && !email.isEmpty()) {
                        displayText += " - " + email;
                    }

                    resourceList.add(displayText);
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to load EBS resources: " + e.getMessage());
            e.printStackTrace();
        }

        resourcesListView.setItems(FXCollections.observableArrayList(resourceList));
    }

    private void loadGenericResources() {
        List<String> resourceList = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            // Get metadata for the table
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            // Find ID, name, and email columns
            String idColumn = null;
            String nameColumn = null;
            String emailColumn = null;

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME").toUpperCase();
                if (columnName.contains("ID") && idColumn == null) {
                    idColumn = columnName;
                } else if ((columnName.contains("NAME") || columnName.equals("FULL_NAME")) && nameColumn == null) {
                    nameColumn = columnName;
                } else if (columnName.contains("EMAIL") && emailColumn == null) {
                    emailColumn = columnName;
                }
            }

            if (idColumn != null && nameColumn != null) {
                // Build query based on found columns
                String query = "SELECT " + idColumn;
                query += ", " + nameColumn;
                if (emailColumn != null) {
                    query += ", " + emailColumn;
                }
                query += " FROM " + tableName;

                System.out.println("Generic query: " + query);

                try (java.sql.Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {

                    while (rs.next()) {
                        int id = rs.getInt(idColumn);
                        String name = rs.getString(nameColumn);
                        String email = emailColumn != null ? rs.getString(emailColumn) : "";

                        String displayText = name + " (ID: " + id + ")";
                        if (email != null && !email.isEmpty()) {
                            displayText += " - " + email;
                        }

                        resourceList.add(displayText);
                    }
                }
            } else {
                resourceList.add("Could not identify required columns in table");
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