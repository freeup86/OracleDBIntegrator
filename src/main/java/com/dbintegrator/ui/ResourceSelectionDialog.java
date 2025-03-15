package com.dbintegrator.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dbintegrator.util.DatabaseConnectionManager;

public class ResourceSelectionDialog extends Dialog<Integer> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<String> resourcesListView;
    private TextField resourceIdField;
    private TextField nameSearchField;
    private int selectedResourceId = -1;

    public ResourceSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;

        setTitle("Select " + (isSource ? "P6" : "EBS") + " Resource");
        setHeaderText("Select a resource from " + tableName);

        // Set up dialog buttons
        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Search fields
        GridPane searchPane = new GridPane();
        searchPane.setHgap(10);
        searchPane.setVgap(10);

        // Resource ID search
        searchPane.add(new Label("Resource ID:"), 0, 0);
        resourceIdField = new TextField();
        searchPane.add(resourceIdField, 1, 0);
        Button idSearchButton = new Button("Search by ID");
        idSearchButton.setOnAction(e -> searchById());
        searchPane.add(idSearchButton, 2, 0);

        // Resource name search
        searchPane.add(new Label("Resource Name:"), 0, 1);
        nameSearchField = new TextField();
        searchPane.add(nameSearchField, 1, 1);
        Button nameSearchButton = new Button("Search by Name");
        nameSearchButton.setOnAction(e -> searchByName());
        searchPane.add(nameSearchButton, 2, 1);

        // Clear search button
        Button clearSearchButton = new Button("Clear Search");
        clearSearchButton.setOnAction(e -> {
            resourceIdField.clear();
            nameSearchField.clear();
            loadResources();
        });
        searchPane.add(clearSearchButton, 3, 0, 1, 2);

        // Resources list
        resourcesListView = new ListView<>();
        resourcesListView.setPrefHeight(300);
        resourcesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.contains("ID:")) {
                try {
                    // Extract ID from the selected item (format: "Name (ID: 123)")
                    String idPart = newVal.substring(newVal.lastIndexOf("ID:") + 3).trim();
                    idPart = idPart.substring(0, idPart.indexOf(")"));
                    selectedResourceId = Integer.parseInt(idPart);
                } catch (Exception ex) {
                    selectedResourceId = -1;
                }
            }
        });

        // Add double-click handler
        resourcesListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (selectedResourceId > 0) {
                    // Close the dialog with the selected resource ID
                    setResult(selectedResourceId);
                    close();
                }
            }
        });

        // Add components to content
        content.getChildren().addAll(
                searchPane,
                new Label("Available Resources:"),
                resourcesListView
        );

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);

        // Load resources on initialize
        loadResources();

        // Set the result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType && selectedResourceId > 0) {
                return selectedResourceId;
            }
            return null;
        });
    }

    private void loadResources() {
        List<String> resourceList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            String query;

            // Use appropriate query based on table
            if (tableName.equalsIgnoreCase("rsrc")) {
                // P6 resource table
                query = "SELECT id, name, email FROM " + tableName + " ORDER BY name";

                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        String email = rs.getString("email");

                        String displayText = name + " (ID: " + id + ")";
                        if (email != null && !email.isEmpty()) {
                            displayText += " - " + email;
                        }

                        resourceList.add(displayText);
                    }
                }
            } else {
                // EBS resource table (hr_all_people)
                query = "SELECT person_id, full_name, email_address FROM " + tableName + " ORDER BY full_name";

                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        int id = rs.getInt("person_id");
                        String name = rs.getString("full_name");
                        String email = rs.getString("email_address");

                        String displayText = name + " (ID: " + id + ")";
                        if (email != null && !email.isEmpty()) {
                            displayText += " - " + email;
                        }

                        resourceList.add(displayText);
                    }
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Failed to load resources: " + e.getMessage());
        }

        resourcesListView.setItems(FXCollections.observableArrayList(resourceList));
    }

    private void searchById() {
        try {
            String idText = resourceIdField.getText().trim();
            if (idText.isEmpty()) {
                loadResources();
                return;
            }

            int searchId = Integer.parseInt(idText);
            List<String> results = new ArrayList<>();

            try (Connection conn = dbManager.getConnection()) {
                String query;
                String idField;
                String nameField;
                String emailField;

                // Different field names based on the table
                if (tableName.equalsIgnoreCase("rsrc")) {
                    // P6 resource table
                    idField = "id";
                    nameField = "name";
                    emailField = "email";
                } else {
                    // EBS resource table
                    idField = "person_id";
                    nameField = "full_name";
                    emailField = "email_address";
                }

                query = "SELECT " + idField + ", " + nameField + ", " + emailField +
                        " FROM " + tableName + " WHERE " + idField + " = ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, searchId);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int id = rs.getInt(idField);
                            String name = rs.getString(nameField);
                            String email = rs.getString(emailField);

                            String displayText = name + " (ID: " + id + ")";
                            if (email != null && !email.isEmpty()) {
                                displayText += " - " + email;
                            }

                            results.add(displayText);
                            selectedResourceId = id;
                        }
                    }
                }
            }

            resourcesListView.setItems(FXCollections.observableArrayList(results));

            if (results.isEmpty()) {
                showError("Not Found", "No resource found with ID: " + searchId);
            } else {
                resourcesListView.getSelectionModel().select(0);
            }

        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter a valid numeric ID");
        } catch (SQLException e) {
            showError("Database Error", "Error searching for resource: " + e.getMessage());
        }
    }

    private void searchByName() {
        String searchText = nameSearchField.getText().trim();
        if (searchText.isEmpty()) {
            loadResources();
            return;
        }

        List<String> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            String query;
            String idField;
            String nameField;
            String emailField;

            // Different field names based on the table
            if (tableName.equalsIgnoreCase("rsrc")) {
                // P6 resource table
                idField = "id";
                nameField = "name";
                emailField = "email";
                query = "SELECT " + idField + ", " + nameField + ", " + emailField +
                        " FROM " + tableName + " WHERE UPPER(" + nameField + ") LIKE ? ORDER BY " + nameField;
            } else {
                // EBS resource table
                idField = "person_id";
                nameField = "full_name";
                emailField = "email_address";
                query = "SELECT " + idField + ", " + nameField + ", " + emailField +
                        " FROM " + tableName + " WHERE UPPER(" + nameField + ") LIKE ? ORDER BY " + nameField;
            }

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "%" + searchText.toUpperCase() + "%");

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt(idField);
                        String name = rs.getString(nameField);
                        String email = rs.getString(emailField);

                        String displayText = name + " (ID: " + id + ")";
                        if (email != null && !email.isEmpty()) {
                            displayText += " - " + email;
                        }

                        results.add(displayText);
                    }
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Error searching for resources: " + e.getMessage());
        }

        resourcesListView.setItems(FXCollections.observableArrayList(results));

        if (results.isEmpty()) {
            showError("Not Found", "No resources found matching: " + searchText);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}