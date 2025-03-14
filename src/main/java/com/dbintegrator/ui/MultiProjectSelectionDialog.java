package com.dbintegrator.ui;

import com.dbintegrator.model.Project;
import com.dbintegrator.util.DatabaseConnectionManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MultiProjectSelectionDialog extends Dialog<List<Project>> {
    private final DatabaseConnectionManager dbManager;
    private final String tableName;
    private final ListView<Project> projectsListView;
    private final TextField projectIdField;
    private final TextField nameSearchField;
    private final List<Project> selectedProjects = new ArrayList<>();

    public MultiProjectSelectionDialog(DatabaseConnectionManager dbManager, String tableName, boolean isSource) {
        this.dbManager = dbManager;
        this.tableName = tableName;

        setTitle("Select " + (isSource ? "Source" : "Destination") + " Projects");
        setHeaderText("Select projects from " + tableName + " table");

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

        searchPane.add(new Label("Project ID:"), 0, 0);
        projectIdField = new TextField();
        searchPane.add(projectIdField, 1, 0);
        Button idSearchButton = new Button("Search by ID");
        idSearchButton.setOnAction(e -> searchById());
        searchPane.add(idSearchButton, 2, 0);

        searchPane.add(new Label("Project Name:"), 0, 1);
        nameSearchField = new TextField();
        searchPane.add(nameSearchField, 1, 1);
        Button nameSearchButton = new Button("Search by Name");
        nameSearchButton.setOnAction(e -> searchByName());
        searchPane.add(nameSearchButton, 2, 1);

        Button clearSearchButton = new Button("Clear Search");
        clearSearchButton.setOnAction(e -> {
            projectIdField.clear();
            nameSearchField.clear();
            loadProjects();
        });
        searchPane.add(clearSearchButton, 3, 0, 1, 2);

        // Projects list - enable multiple selection
        projectsListView = new ListView<>();
        projectsListView.setPrefHeight(300);
        projectsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Selection controls
        HBox selectionControls = new HBox(10);
        Button selectAllButton = new Button("Select All");
        selectAllButton.setOnAction(e -> projectsListView.getSelectionModel().selectAll());

        Button clearSelectionButton = new Button("Clear Selection");
        clearSelectionButton.setOnAction(e -> projectsListView.getSelectionModel().clearSelection());

        Label selectionCountLabel = new Label("0 projects selected");
        HBox.setHgrow(selectionCountLabel, Priority.ALWAYS);
        selectionCountLabel.setMaxWidth(Double.MAX_VALUE);
        selectionCountLabel.setStyle("-fx-alignment: center-right;");

        selectionControls.getChildren().addAll(selectAllButton, clearSelectionButton, selectionCountLabel);

        // Update selection count when selection changes
        projectsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            int count = projectsListView.getSelectionModel().getSelectedItems().size();
            selectionCountLabel.setText(count + " projects selected");
        });

        // Add components to content
        content.getChildren().addAll(
                searchPane,
                new Label("Available Projects:"),
                projectsListView,
                selectionControls
        );

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(500);

        // Load projects on initialize
        loadProjects();

        // Set the result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                return new ArrayList<>(projectsListView.getSelectionModel().getSelectedItems());
            }
            return null;
        });
    }

    private void loadProjects() {
        List<Project> projectList = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, name, description FROM " + tableName + " ORDER BY name")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String description = rs.getString("description");

                projectList.add(new Project(id, name, description));
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

            List<Project> results = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, name, description FROM " + tableName + " WHERE id = ?")) {

                stmt.setInt(1, searchId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");

                    results.add(new Project(id, name, description));
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

    private void searchByName() {
        String searchText = nameSearchField.getText().trim();
        if (searchText.isEmpty()) {
            loadProjects();
            return;
        }

        List<Project> results = new ArrayList<>();

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

                results.add(new Project(id, name, description));
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