package com.dbintegrator.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dbintegrator.util.DatabaseConnectionManager;

public class ProjectSelectionDialog extends Dialog<Integer> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<String> projectsListView;
    private TextField projectIdField;
    private int selectedProjectId = -1;

    public ProjectSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;

        setTitle("Select " + (isSource ? "Source" : "Destination") + " Project");
        setHeaderText("Select a project from " + tableName);

        // Set up dialog buttons
        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Search by ID field
        GridPane searchPane = new GridPane();
        searchPane.setHgap(10);
        searchPane.add(new Label("Project ID:"), 0, 0);
        projectIdField = new TextField();
        searchPane.add(projectIdField, 1, 0);
        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> searchById());
        searchPane.add(searchButton, 2, 0);

        // Projects list
        projectsListView = new ListView<>();
        projectsListView.setPrefHeight(300);
        projectsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.contains("ID:")) {
                try {
                    // Extract ID from the selected item (format: "Name (ID: 123)")
                    String idPart = newVal.substring(newVal.lastIndexOf("ID:") + 3).trim();
                    idPart = idPart.substring(0, idPart.indexOf(")"));
                    selectedProjectId = Integer.parseInt(idPart);
                } catch (Exception ex) {
                    selectedProjectId = -1;
                }
            }
        });

        // Add components to content
        content.getChildren().addAll(
                searchPane,
                new Label("Available Projects:"),
                projectsListView
        );

        getDialogPane().setContent(content);

        // Load projects on initialize
        loadProjects();

        // Set the result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType && selectedProjectId > 0) {
                return selectedProjectId;
            }
            return null;
        });
    }

    private void loadProjects() {
        List<String> projectList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, name, description FROM " + tableName + " ORDER BY name")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String description = rs.getString("description");

                String displayText = name + " (ID: " + id + ")";
                if (description != null && !description.isEmpty()) {
                    displayText += " - " + description;
                }

                projectList.add(displayText);
            }

        } catch (SQLException e) {
            showError("Database Error", "Failed to load projects: " + e.getMessage());
        }

        projectsListView.setItems(FXCollections.observableArrayList(projectList));
    }

    private void searchById() {
        try {
            String idText = projectIdField.getText().trim();
            if (idText.isEmpty()) {
                loadProjects();
                return;
            }

            int searchId = Integer.parseInt(idText);

            List<String> results = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, name, description FROM " + tableName + " WHERE id = ?")) {

                stmt.setInt(1, searchId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");

                    String displayText = name + " (ID: " + id + ")";
                    if (description != null && !description.isEmpty()) {
                        displayText += " - " + description;
                    }

                    results.add(displayText);
                    selectedProjectId = id;
                }
            }

            projectsListView.setItems(FXCollections.observableArrayList(results));

            if (results.isEmpty()) {
                showError("Not Found", "No project found with ID: " + searchId);
            } else {
                projectsListView.getSelectionModel().select(0);
            }

        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter a valid numeric ID");
        } catch (SQLException e) {
            showError("Database Error", "Error searching for project: " + e.getMessage());
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