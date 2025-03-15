package com.dbintegrator.controller;

import com.dbintegrator.model.ColumnMapping;
import com.dbintegrator.model.Project;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.service.DatabaseMetadataService;
import com.dbintegrator.service.DataIntegrationService;
import com.dbintegrator.ui.MultiProjectSelectionDialog;
import com.dbintegrator.ui.ProjectSelectionDialog;
import com.dbintegrator.util.DatabaseConnectionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TaskIntegrationController {

    @FXML private Button selectSourceProjectButton;
    @FXML private Button selectDestProjectButton;
    @FXML private Label sourceProjectLabel;
    @FXML private Label destProjectLabel;
    @FXML private ComboBox<String> sourceTaskTableComboBox;
    @FXML private ComboBox<String> destTaskTableComboBox;
    @FXML private ListView<TableColumn> sourceColumnsListView;
    @FXML private ListView<TableColumn> destColumnsListView;
    @FXML private ListView<ColumnMapping> taskMappingsListView;
    @FXML private Button addTaskMappingButton;
    @FXML private Button removeTaskMappingButton;
    @FXML private Button executeTaskIntegrationButton;
    @FXML private TextArea logTextArea;

    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;

    private Project selectedSourceProject;
    private Project selectedDestProject;
    private ObservableList<ColumnMapping> taskMappings = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("TaskIntegrationController - INITIALIZE METHOD CALLED");

        // Initialize UI components
        initializeUIComponents();
        setupEventHandlers();
    }

    private void initializeUIComponents() {
        // Configure ListViews
        if (sourceColumnsListView != null) {
            sourceColumnsListView.setItems(FXCollections.observableArrayList());
        } else {
            System.err.println("sourceColumnsListView is NULL");
        }

        if (destColumnsListView != null) {
            destColumnsListView.setItems(FXCollections.observableArrayList());
        } else {
            System.err.println("destColumnsListView is NULL");
        }

        if (taskMappingsListView != null) {
            taskMappingsListView.setItems(taskMappings);
        } else {
            System.err.println("taskMappingsListView is NULL");
        }

        // Initialize labels
        if (sourceProjectLabel != null) {
            sourceProjectLabel.setText("No P6 project selected");
        }

        if (destProjectLabel != null) {
            destProjectLabel.setText("No EBS project selected");
        }

        // Configure Task Table selection handlers
        if (sourceTaskTableComboBox != null) {
            sourceTaskTableComboBox.setOnAction(event -> {
                String selectedTable = sourceTaskTableComboBox.getValue();
                if (selectedTable != null) {
                    loadTableColumns(true, selectedTable);
                }
            });
        }

        if (destTaskTableComboBox != null) {
            destTaskTableComboBox.setOnAction(event -> {
                String selectedTable = destTaskTableComboBox.getValue();
                if (selectedTable != null) {
                    loadTableColumns(false, selectedTable);
                }
            });
        }
    }

    private void setupEventHandlers() {
        if (selectSourceProjectButton != null) {
            selectSourceProjectButton.setOnAction(event -> selectProject(true));
        }

        if (selectDestProjectButton != null) {
            selectDestProjectButton.setOnAction(event -> selectProject(false));
        }

        if (addTaskMappingButton != null) {
            addTaskMappingButton.setOnAction(event -> addTaskMapping());
            addTaskMappingButton.setDisable(true);
        }

        if (removeTaskMappingButton != null) {
            removeTaskMappingButton.setOnAction(event -> removeTaskMapping());
            removeTaskMappingButton.setDisable(true);
        }

        if (executeTaskIntegrationButton != null) {
            executeTaskIntegrationButton.setOnAction(event -> executeTaskIntegration());
            executeTaskIntegrationButton.setDisable(true);
        }
    }

    public void setSourceDbManager(DatabaseConnectionManager sourceDbManager) {
        System.out.println("setSourceDbManager CALLED");
        this.sourceDbManager = sourceDbManager;
        if (sourceDbManager != null) {
            this.sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
            loadTaskTables(true);

            // Enable source project selection button
            if (selectSourceProjectButton != null) {
                selectSourceProjectButton.setDisable(false);
            }
        }
    }

    public void setDestDbManager(DatabaseConnectionManager destDbManager) {
        System.out.println("setDestDbManager CALLED");
        this.destDbManager = destDbManager;
        if (destDbManager != null) {
            this.destMetadataService = new DatabaseMetadataService(destDbManager);
            loadTaskTables(false);

            // Enable destination project selection button
            if (selectDestProjectButton != null) {
                selectDestProjectButton.setDisable(false);
            }
        }
    }

    private void selectProject(boolean isSource) {
        DatabaseConnectionManager dbManager = isSource ? sourceDbManager : destDbManager;
        if (dbManager == null) {
            showError("Connection Required",
                    "Please connect to " + (isSource ? "source" : "destination") + " database first.");
            return;
        }

        String tableName = isSource ? "PROJECTS" : "PA_PROJECTS";

        // Use ProjectSelectionDialog instead of MultiProjectSelectionDialog
        ProjectSelectionDialog dialog = new ProjectSelectionDialog(dbManager, tableName, isSource);

        Optional<Integer> result = dialog.showAndWait();
        if (result.isPresent() && result.get() > 0) {
            int projectId = result.get();

            // Get full project details from the database
            try {
                Project selectedProject = getProjectById(dbManager, tableName, projectId);

                if (selectedProject != null) {
                    if (isSource) {
                        this.selectedSourceProject = selectedProject;
                        updateProjectLabel(true);
                    } else {
                        this.selectedDestProject = selectedProject;
                        updateProjectLabel(false);
                    }

                    // Log selection
                    if (logTextArea != null) {
                        logTextArea.appendText("Selected " + (isSource ? "source" : "destination") +
                                " project: " + selectedProject.getName() +
                                " (ID: " + selectedProject.getId() + ")\n");
                    }

                    // Enable execute button when both source and destination projects are selected and mappings exist
                    checkExecuteButtonStatus();
                }
            } catch (SQLException e) {
                showError("Database Error", "Failed to retrieve project details: " + e.getMessage());
            }
        }
    }

    private Project getProjectById(DatabaseConnectionManager dbManager, String tableName, int projectId) throws SQLException {
        String query = "SELECT id, name, description FROM " + tableName + " WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Project(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description")
                    );
                }
            }
        }

        return null;
    }

    private void updateProjectLabel(boolean isSource) {
        Project project = isSource ? selectedSourceProject : selectedDestProject;
        Label label = isSource ? sourceProjectLabel : destProjectLabel;

        if (project == null) {
            label.setText("No " + (isSource ? "P6" : "EBS") + " project selected");
        } else {
            label.setText((isSource ? "P6" : "EBS") + " Project: " +
                    project.getName() + " (ID: " + project.getId() + ")");
        }
    }

    private void loadTaskTables(boolean isSource) {
        if ((isSource && sourceDbManager == null) || (!isSource && destDbManager == null)) {
            System.err.println("Cannot load task tables - database manager is null");
            return;
        }

        try {
            DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
            ComboBox<String> comboBox = isSource ? sourceTaskTableComboBox : destTaskTableComboBox;

            if (metadataService == null || comboBox == null) {
                System.err.println("Cannot load task tables - service or combobox is null");
                return;
            }

            List<String> tableNames = metadataService.getTableNames();

            // Filter for task-related tables
            List<String> taskTables = new ArrayList<>();
            for (String tableName : tableNames) {
                if (tableName.toUpperCase().contains("TASK") ||
                        tableName.toUpperCase().contains("ACTIVITY")) {
                    taskTables.add(tableName);
                }
            }

            System.out.println((isSource ? "Source" : "Destination") + " Task Tables: " + taskTables.size());

            Platform.runLater(() -> {
                comboBox.getItems().clear();
                comboBox.getItems().addAll(taskTables);

                if (!taskTables.isEmpty()) {
                    comboBox.setValue(taskTables.get(0));
                }
            });
        } catch (SQLException e) {
            System.err.println("Error loading task tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadTableColumns(boolean isSource, String tableName) {
        try {
            DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
            ListView<TableColumn> listView = isSource ? sourceColumnsListView : destColumnsListView;

            if (metadataService == null || listView == null) {
                System.err.println("Cannot load columns - metadata service or list view is null");
                return;
            }

            List<TableColumn> columns = metadataService.getTableColumns(tableName);

            System.out.println("Loaded " + columns.size() + " columns for " +
                    (isSource ? "source" : "destination") + " table " + tableName);

            Platform.runLater(() -> {
                listView.setItems(FXCollections.observableArrayList(columns));
                checkMappingAvailability();
            });
        } catch (SQLException e) {
            System.err.println("Error loading table columns: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkMappingAvailability() {
        boolean canMap = sourceTaskTableComboBox != null && sourceTaskTableComboBox.getValue() != null &&
                destTaskTableComboBox != null && destTaskTableComboBox.getValue() != null &&
                !sourceColumnsListView.getItems().isEmpty() &&
                !destColumnsListView.getItems().isEmpty();

        Platform.runLater(() -> {
            if (addTaskMappingButton != null) {
                addTaskMappingButton.setDisable(!canMap);
            }
        });
    }

    private void checkExecuteButtonStatus() {
        boolean canExecute = selectedSourceProject != null &&
                selectedDestProject != null &&
                !taskMappings.isEmpty();

        Platform.runLater(() -> {
            if (executeTaskIntegrationButton != null) {
                executeTaskIntegrationButton.setDisable(!canExecute);
            }
        });
    }

    // Update the addTaskMapping method to only log to the main logTextArea
    private void addTaskMapping() {
        TableColumn sourceColumn = sourceColumnsListView.getSelectionModel().getSelectedItem();
        TableColumn destColumn = destColumnsListView.getSelectionModel().getSelectedItem();

        if (sourceColumn != null && destColumn != null) {
            String sourceTable = sourceTaskTableComboBox.getValue();
            String destTable = destTaskTableComboBox.getValue();

            ColumnMapping mapping = new ColumnMapping(sourceTable, sourceColumn, destTable, destColumn);

            // Prevent duplicate mappings
            boolean isDuplicate = false;
            for (ColumnMapping existingMapping : taskMappings) {
                if (existingMapping.getSourceTable().equals(mapping.getSourceTable()) &&
                        existingMapping.getSourceColumn().getName().equals(mapping.getSourceColumn().getName()) &&
                        existingMapping.getDestinationTable().equals(mapping.getDestinationTable()) &&
                        existingMapping.getDestinationColumn().getName().equals(mapping.getDestinationColumn().getName())) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                taskMappings.add(mapping);

                if (removeTaskMappingButton != null) {
                    removeTaskMappingButton.setDisable(false);
                }

                // Check if execute button should be enabled
                checkExecuteButtonStatus();

                // Only log to the main logTextArea
                if (logTextArea != null) {
                    logTextArea.appendText("Added mapping: " + sourceColumn.getName() +
                            " → " + destColumn.getName() + "\n");
                }
            } else {
                showError("Mapping Error", "This column mapping already exists.");
            }
        } else {
            showError("Mapping Error", "Please select both source and destination columns.");
        }
    }

    // Similar update for removeTaskMapping
    private void removeTaskMapping() {
        ColumnMapping selectedMapping = taskMappingsListView.getSelectionModel().getSelectedItem();
        if (selectedMapping != null) {
            taskMappings.remove(selectedMapping);

            // Only log to the main logTextArea
            if (logTextArea != null) {
                logTextArea.appendText("Removed mapping: " + selectedMapping + "\n");
            }

            if (taskMappings.isEmpty()) {
                if (removeTaskMappingButton != null) {
                    removeTaskMappingButton.setDisable(true);
                }

                // Check if execute button should be disabled
                checkExecuteButtonStatus();
            }
        }
    }

    private void executeTaskIntegration() {
        if (taskMappings.isEmpty()) {
            showError("Integration Error", "No column mappings defined.");
            return;
        }

        if (selectedSourceProject == null || selectedDestProject == null) {
            showError("Project Selection Required", "Please select both source and destination projects.");
            return;
        }

        try {
            // Create WHERE clauses for source and destination projects
            String sourceWhereClause = "project_id = " + selectedSourceProject.getId();
            String destWhereClause = "project_id = " + selectedDestProject.getId();

            // Create integration service
            DataIntegrationService integrationService = new DataIntegrationService(sourceDbManager, destDbManager);

            // Perform integration
            int rowsUpdated = integrationService.integrateData(taskMappings, sourceWhereClause, destWhereClause);

            if (logTextArea != null) {
                logTextArea.appendText("Task integration completed successfully.\n");
                logTextArea.appendText("Updated " + rowsUpdated + " rows for project " +
                        selectedDestProject.getName() + " (ID: " + selectedDestProject.getId() + ")\n");
            }

        } catch (SQLException e) {
            showError("Integration Error", e.getMessage());
            if (logTextArea != null) {
                logTextArea.appendText("Integration failed: " + e.getMessage() + "\n");
            }
        }
    }

    public void forceProjectLoading() {
        // This method is kept for backward compatibility
        // Now it doesn't do anything with projects since we're using the dialog
        if (sourceDbManager != null) {
            loadTaskTables(true);
        }
        if (destDbManager != null) {
            loadTaskTables(false);
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}