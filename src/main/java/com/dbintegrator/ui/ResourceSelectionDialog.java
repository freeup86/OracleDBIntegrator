package com.dbintegrator.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dbintegrator.util.DatabaseConnectionManager;

public class ResourceSelectionDialog extends Dialog<List<Integer>> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<String> resourcesListView;
    private final boolean isSource;
    private final Button selectAllButton;

    public ResourceSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;
        this.isSource = isSource;

        setTitle("Select " + (isSource ? "P6" : "EBS") + " Resource");
        setHeaderText("Select " + (isSource ? "a" : "one or more") + " resource" +
                (isSource ? "" : "s") + " from " + tableName);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Add search field
        TextField searchField = new TextField();
        searchField.setPromptText("Search by name...");
        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> filterResources(searchField.getText()));

        GridPane searchPane = new GridPane();
        searchPane.setHgap(10);
        searchPane.setVgap(10);
        searchPane.add(searchField, 0, 0);
        searchPane.add(searchButton, 1, 0);
        content.getChildren().add(searchPane);

        // Resources list
        resourcesListView = new ListView<>();
        resourcesListView.setPrefHeight(300);

        // Default to single select, will be changed by caller if needed
        resourcesListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Create Select All button
        selectAllButton = new Button("Select All");
        selectAllButton.setOnAction(e -> selectAllResources());
        selectAllButton.setVisible(false); // Hidden by default, shown only in multi-select mode

        // Button bar for list actions
        HBox buttonBar = new HBox(10);
        buttonBar.getChildren().add(selectAllButton);

        // Add components to content
        content.getChildren().addAll(
                new Label("Available Resources:"),
                resourcesListView,
                buttonBar
        );

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);

        // Set the buttons
        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        // Set the result converter to extract the IDs from the selected items
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                List<Integer> selectedIds = new ArrayList<>();
                List<String> selectedItems = resourcesListView.getSelectionModel().getSelectedItems();

                for (String item : selectedItems) {
                    if (item != null && item.contains("ID:")) {
                        try {
                            int startIndex = item.indexOf("ID:") + 3;
                            int endIndex = item.indexOf(")", startIndex);
                            if (endIndex > startIndex) {
                                String idStr = item.substring(startIndex, endIndex).trim();
                                int id = Integer.parseInt(idStr);
                                selectedIds.add(id);
                                System.out.println("Selected resource ID: " + id);
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing resource ID: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                return selectedIds;
            }
            return new ArrayList<>();
        });

        // Only load resources if we have a valid database connection
        if (dbManager != null) {
            loadResources();
        } else {
            resourcesListView.setItems(FXCollections.observableArrayList(
                    "No database connection available"));
        }
    }

    public void enableMultiSelect(boolean enableMultiSelect) {
        if (enableMultiSelect) {
            resourcesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            setHeaderText("Select one or more resources from " + tableName);
            selectAllButton.setVisible(true); // Show Select All button
        } else {
            resourcesListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            setHeaderText("Select a resource from " + tableName);
            selectAllButton.setVisible(false); // Hide Select All button
        }
    }

    private void selectAllResources() {
        resourcesListView.getSelectionModel().selectAll();
    }

    private void filterResources(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            loadResources(); // Reset to full list
            return;
        }

        searchText = searchText.toLowerCase().trim();

        // Filter the existing items
        List<String> filteredList = new ArrayList<>();
        for (String item : resourcesListView.getItems()) {
            if (item.toLowerCase().contains(searchText)) {
                filteredList.add(item);
            }
        }

        if (filteredList.isEmpty()) {
            resourcesListView.setItems(FXCollections.observableArrayList("No matching resources found"));
        } else {
            resourcesListView.setItems(FXCollections.observableArrayList(filteredList));
        }
    }

    private void loadResources() {
        List<String> resourceList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            // First, check what tables actually exist
            List<String> existingTables = new ArrayList<>();
            try (ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    existingTables.add(tableName.toUpperCase());
                    System.out.println("Found table: " + tableName);
                }
            }

            // Now we know what tables are actually available
            boolean tableExists = existingTables.contains(tableName.toUpperCase());

            if (tableExists) {
                // Table exists, load the resources based on table type
                if (tableName.equals("HR_ALL_PEOPLE")) {
                    resourceList = loadEBSResources(conn);
                } else if (tableName.equals("RSRC")) {
                    resourceList = loadP6Resources(conn);
                } else {
                    // Generic approach for other tables
                    resourceList = loadGenericResources(conn, tableName);
                }
            } else {
                // Table doesn't exist, try a fallback
                System.out.println(tableName + " table not found, trying fallback");
                if (tableName.equals("HR_ALL_PEOPLE")) {
                    // Try to use RSRC data as a fallback for HR_ALL_PEOPLE
                    if (existingTables.contains("RSRC")) {
                        System.out.println("HR_ALL_PEOPLE table not found, using RSRC as a fallback");
                        resourceList = loadP6Resources(conn);
                    }
                } else if (tableName.equals("RSRC")) {
                    // Try to use HR_ALL_PEOPLE data as a fallback for RSRC
                    if (existingTables.contains("HR_ALL_PEOPLE")) {
                        System.out.println("RSRC table not found, using HR_ALL_PEOPLE as a fallback");
                        resourceList = loadEBSResources(conn);
                    }
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to load resources: " + e.getMessage());
            e.printStackTrace();
        }

        // Set the ListView items
        if (resourceList.isEmpty()) {
            resourcesListView.setItems(FXCollections.observableArrayList("No resources found"));
        } else {
            resourcesListView.setItems(FXCollections.observableArrayList(resourceList));
        }
    }

    private List<String> loadP6Resources(Connection conn) {
        List<String> resourceList = new ArrayList<>();
        try {
            // First determine if we're using RSRC_ID or ID
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, "RSRC", null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toUpperCase());
                }
            }

            // Try RSRC_ID first, fall back to ID if needed
            String idColumn = columnNames.contains("RSRC_ID") ? "RSRC_ID" : "ID";
            System.out.println("Using ID column for RSRC table: " + idColumn);

            String query = "SELECT " + idColumn + ", NAME, EMAIL FROM RSRC";
            System.out.println("Executing query: " + query);

            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    int id = rs.getInt(idColumn);
                    String name = rs.getString("NAME");
                    String email = rs.getString("EMAIL");

                    String displayText = name + " (ID: " + id + ")";
                    if (email != null && !email.isEmpty()) {
                        displayText += " - " + email;
                    }

                    resourceList.add(displayText);
                    System.out.println("Found P6 resource: " + displayText);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load P6 resources: " + e.getMessage());
            e.printStackTrace();
        }

        return resourceList;
    }

    private List<String> loadEBSResources(Connection conn) {
        List<String> resourceList = new ArrayList<>();
        try {
            // First determine if we're using PERSON_ID or ID
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, "HR_ALL_PEOPLE", null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toUpperCase());
                }
            }

            // Try PERSON_ID first, fall back to ID if needed
            String idColumn = columnNames.contains("PERSON_ID") ? "PERSON_ID" : "ID";
            String nameColumn = columnNames.contains("FULL_NAME") ? "FULL_NAME" : "NAME";
            String emailColumn = columnNames.contains("EMAIL_ADDRESS") ? "EMAIL_ADDRESS" : "EMAIL";

            System.out.println("Using columns for HR_ALL_PEOPLE: ID=" + idColumn +
                    ", Name=" + nameColumn + ", Email=" + emailColumn);

            String query = "SELECT " + idColumn + ", " + nameColumn + ", " + emailColumn + " FROM HR_ALL_PEOPLE";
            System.out.println("Executing query: " + query);

            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    int id = rs.getInt(idColumn);
                    String name = rs.getString(nameColumn);
                    String email = rs.getString(emailColumn);

                    String displayText = name + " (ID: " + id + ")";
                    if (email != null && !email.isEmpty()) {
                        displayText += " - " + email;
                    }

                    resourceList.add(displayText);
                    System.out.println("Found EBS resource: " + displayText);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load EBS resources: " + e.getMessage());
            e.printStackTrace();
        }

        return resourceList;
    }

    private List<String> loadGenericResources(Connection conn, String tableName) {
        List<String> resourceList = new ArrayList<>();
        try {
            // Get metadata for the table
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            // Find ID, name, and email columns
            String idColumn = null;
            String nameColumn = null;
            String emailColumn = null;

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME").toUpperCase();
                if ((columnName.contains("ID") || columnName.equals("PERSON_ID") ||
                        columnName.equals("RSRC_ID")) && idColumn == null) {
                    idColumn = columnName;
                } else if ((columnName.contains("NAME") || columnName.equals("FULL_NAME"))
                        && nameColumn == null) {
                    nameColumn = columnName;
                } else if ((columnName.contains("EMAIL") || columnName.equals("EMAIL_ADDRESS"))
                        && emailColumn == null) {
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
            System.err.println("Failed to load resources from " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }

        return resourceList;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}