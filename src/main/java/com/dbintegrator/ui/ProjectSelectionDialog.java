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
import java.util.Optional;

import com.dbintegrator.util.DatabaseConnectionManager;

public class ProjectSelectionDialog extends Dialog<Integer> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<String> projectsListView;
    private TextField projectIdField;
    private TextField nameSearchField;
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

        // Search fields
        GridPane searchPane = new GridPane();
        searchPane.setHgap(10);
        searchPane.setVgap(10);

        // Project ID search
        searchPane.add(new Label("Project ID:"), 0, 0);
        projectIdField = new TextField();
        searchPane.add(projectIdField, 1, 0);
        Button idSearchButton = new Button("Search by ID");
        idSearchButton.setOnAction(e -> searchById());
        searchPane.add(idSearchButton, 2, 0);

        // Project name search
        searchPane.add(new Label("Project Name:"), 0, 1);
        nameSearchField = new TextField();
        searchPane.add(nameSearchField, 1, 1);
        Button nameSearchButton = new Button("Search by Name");
        nameSearchButton.setOnAction(e -> searchByName());
        searchPane.add(nameSearchButton, 2, 1);

        // Clear search button
        Button clearSearchButton = new Button("Clear Search");
        clearSearchButton.setOnAction(e -> {
            projectIdField.clear();
            nameSearchField.clear();
            loadProjects();
        });
        searchPane.add(clearSearchButton, 3, 0, 1, 2);

        // Projects list
        projectsListView = new ListView<>();
        projectsListView.setPrefHeight(300);

        // Listener to track selected project ID
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

        // Add double-click handler for quick selection
        projectsListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (selectedProjectId > 0) {
                    // Close the dialog with the selected project ID
                    setResult(selectedProjectId);
                    close();
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
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);

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
                projectsListView.getSelectionModel().selectFirst();
            }

        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter a valid numeric ID");
        } catch (SQLException e) {
            showError("Database Error", "Error searching for project: " + e.getMessage());
        }
    }

    private void searchByName() {
        String searchText = nameSearchField.getText().trim();
        if (searchText.isEmpty()) {
            loadProjects();
            return;
        }

        List<String> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, name, description FROM " + tableName +
                             " WHERE UPPER(name) LIKE ? ORDER BY name")) {

            stmt.setString(1, "%" + searchText.toUpperCase() + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String description = rs.getString("description");

                String displayText = name + " (ID: " + id + ")";
                if (description != null && !description.isEmpty()) {
                    displayText += " - " + description;
                }

                results.add(displayText);
            }
        } catch (SQLException e) {
            showError("Database Error", "Error searching for projects: " + e.getMessage());
        }

        projectsListView.setItems(FXCollections.observableArrayList(results));

        if (results.isEmpty()) {
            showError("Not Found", "No projects found matching: " + searchText);
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