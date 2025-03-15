package com.dbintegrator.controller;

import com.dbintegrator.model.ColumnMapping;
import com.dbintegrator.model.Resource;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.service.DatabaseMetadataService;
import com.dbintegrator.service.DataIntegrationService;
import com.dbintegrator.ui.ResourceSelectionDialog;
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

public class ResourceIntegrationController {

    // FXML Components
    @FXML private Button selectSourceResourceButton;
    @FXML private Button selectDestResourceButton;
    @FXML private Label sourceResourceLabel;
    @FXML private Label destResourceLabel;
    @FXML private TextField sourceTableField;
    @FXML private TextField destTableField;
    @FXML private ListView<TableColumn> sourceColumnsListView;
    @FXML private ListView<TableColumn> destColumnsListView;
    @FXML private ListView<ColumnMapping> resourceMappingsListView;
    @FXML private Button addResourceMappingButton;
    @FXML private Button removeResourceMappingButton;
    @FXML private Button executeResourceIntegrationButton;
    @FXML private TextArea logTextArea;

    // Controller properties
    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;

    private Resource selectedSourceResource;
    private Resource selectedDestResource;
    private ObservableList<ColumnMapping> resourceMappings = FXCollections.observableArrayList();

    // Fixed table names
    private static final String SOURCE_TABLE_NAME = "rsrc";
    private static final String DEST_TABLE_NAME = "hr_all_people";

    @FXML
    public void initialize() {
        System.out.println("ResourceIntegrationController - INITIALIZE METHOD CALLED");

        // Initialize UI components
        initializeUIComponents();
        setupEventHandlers();
    }

    private void initializeUIComponents() {
        // Set fixed table names
        if (sourceTableField != null) {
            sourceTableField.setText(SOURCE_TABLE_NAME);
        }

        if (destTableField != null) {
            destTableField.setText(DEST_TABLE_NAME);
        }

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

        if (resourceMappingsListView != null) {
            resourceMappingsListView.setItems(resourceMappings);
        } else {
            System.err.println("resourceMappingsListView is NULL");
        }

        // Initialize labels
        if (sourceResourceLabel != null) {
            sourceResourceLabel.setText("No P6 resource selected");
        }

        if (destResourceLabel != null) {
            destResourceLabel.setText("No EBS resource selected");
        }
    }

    private void setupEventHandlers() {
        if (selectSourceResourceButton != null) {
            selectSourceResourceButton.setOnAction(event -> selectResource(true));
        }

        if (selectDestResourceButton != null) {
            selectDestResourceButton.setOnAction(event -> selectResource(false));
        }

        if (addResourceMappingButton != null) {
            addResourceMappingButton.setOnAction(event -> addResourceMapping());
            addResourceMappingButton.setDisable(true);
        }

        if (removeResourceMappingButton != null) {
            removeResourceMappingButton.setOnAction(event -> removeResourceMapping());
            removeResourceMappingButton.setDisable(true);
        }

        if (executeResourceIntegrationButton != null) {
            executeResourceIntegrationButton.setOnAction(event -> executeResourceIntegration());
            executeResourceIntegrationButton.setDisable(true);
        }
    }

    public void setSourceDbManager(DatabaseConnectionManager sourceDbManager) {
        System.out.println("ResourceIntegrationController - setSourceDbManager CALLED");
        this.sourceDbManager = sourceDbManager;
        if (sourceDbManager != null) {
            this.sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
            loadTableColumns(true);

            // Enable source resource selection button
            if (selectSourceResourceButton != null) {
                selectSourceResourceButton.setDisable(false);
            }
        }
    }

    public void setDestDbManager(DatabaseConnectionManager destDbManager) {
        System.out.println("ResourceIntegrationController - setDestDbManager CALLED");
        this.destDbManager = destDbManager;
        if (destDbManager != null) {
            this.destMetadataService = new DatabaseMetadataService(destDbManager);
            loadTableColumns(false);

            // Enable destination resource selection button
            if (selectDestResourceButton != null) {
                selectDestResourceButton.setDisable(false);
            }
        }
    }

    private void selectResource(boolean isSource) {
        DatabaseConnectionManager dbManager = isSource ? sourceDbManager : destDbManager;
        if (dbManager == null) {
            showError("Connection Required",
                    "Please connect to " + (isSource ? "source" : "destination") + " database first.");
            return;
        }

        String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;

        ResourceSelectionDialog dialog = new ResourceSelectionDialog(dbManager, tableName, isSource);

        Optional<Integer> result = dialog.showAndWait();
        if (result.isPresent() && result.get() > 0) {
            int resourceId = result.get();

            // Get full resource details from the database
            try {
                Resource selectedResource = getResourceById(dbManager, tableName, resourceId);

                if (selectedResource != null) {
                    if (isSource) {
                        this.selectedSourceResource = selectedResource;
                        updateResourceLabel(true);
                    } else {
                        this.selectedDestResource = selectedResource;
                        updateResourceLabel(false);
                    }

                    // Log selection
                    if (logTextArea != null) {
                        logTextArea.appendText("Selected " + (isSource ? "source" : "destination") +
                                " resource: " + selectedResource.getName() +
                                " (ID: " + selectedResource.getId() + ")\n");
                    }

                    // Enable execute button when both source and destination resources are selected and mappings exist
                    checkExecuteButtonStatus();
                }
            } catch (SQLException e) {
                showError("Database Error", "Failed to retrieve resource details: " + e.getMessage());
            }
        }
    }

    private Resource getResourceById(DatabaseConnectionManager dbManager, String tableName, int resourceId) throws SQLException {
        // Determine ID, name, and email field names based on table
        String idField = "id"; // Default for P6
        String nameField = "name"; // Default for P6
        String emailField = "email"; // Default for P6

        if (tableName.equalsIgnoreCase(DEST_TABLE_NAME)) {
            // EBS resource table field names
            idField = "person_id";
            nameField = "full_name";
            emailField = "email_address";
        }

        String query = "SELECT " + idField + ", " + nameField + ", " + emailField +
                " FROM " + tableName + " WHERE " + idField + " = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, resourceId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Resource(
                            rs.getInt(idField),
                            rs.getString(nameField),
                            rs.getString(emailField)
                    );
                }
            }
        }

        return null;
    }

    private void updateResourceLabel(boolean isSource) {
        Resource resource = isSource ? selectedSourceResource : selectedDestResource;
        Label label = isSource ? sourceResourceLabel : destResourceLabel;

        if (resource == null) {
            label.setText("No " + (isSource ? "P6" : "EBS") + " resource selected");
        } else {
            label.setText((isSource ? "P6" : "EBS") + " Resource: " +
                    resource.getName() + " (ID: " + resource.getId() + ")");
        }
    }

    private void loadTableColumns(boolean isSource) {
        try {
            String tableName = isSource ? "rsrc" : "hr_all_people"; // Use fixed table names
            System.out.println("RESOURCE INTEGRATION: Loading columns for table: " + tableName);

            DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
            ListView<TableColumn> listView = isSource ? sourceColumnsListView : destColumnsListView;

            if (metadataService == null) {
                System.err.println("Cannot load columns - metadata service is null");
                return;
            }

            if (listView == null) {
                System.err.println("Cannot load columns - list view is null");
                return;
            }

            List<TableColumn> columns = metadataService.getTableColumns(tableName);

            System.out.println("Loaded " + columns.size() + " columns for " +
                    (isSource ? "source" : "destination") + " table " + tableName);

            // Debug output to show the columns
            for (TableColumn column : columns) {
                System.out.println("  - Column: " + column.getName() + " (" + column.getDataType() + ")");
            }

            Platform.runLater(() -> {
                listView.setItems(FXCollections.observableArrayList(columns));
                checkMappingAvailability();
            });
        } catch (SQLException e) {
            System.err.println("Error loading table columns: " + e.getMessage());
            e.printStackTrace();

            if (logTextArea != null) {
                logTextArea.appendText("Error loading columns: " + e.getMessage() + "\n");
            }
        }
    }

    private void checkMappingAvailability() {
        boolean canMap = !sourceColumnsListView.getItems().isEmpty() &&
                !destColumnsListView.getItems().isEmpty();

        Platform.runLater(() -> {
            if (addResourceMappingButton != null) {
                addResourceMappingButton.setDisable(!canMap);
            }
        });
    }

    private void checkExecuteButtonStatus() {
        boolean canExecute = selectedSourceResource != null &&
                selectedDestResource != null &&
                !resourceMappings.isEmpty();

        Platform.runLater(() -> {
            if (executeResourceIntegrationButton != null) {
                executeResourceIntegrationButton.setDisable(!canExecute);
            }
        });
    }

    private void addResourceMapping() {
        TableColumn sourceColumn = sourceColumnsListView.getSelectionModel().getSelectedItem();
        TableColumn destColumn = destColumnsListView.getSelectionModel().getSelectedItem();

        if (sourceColumn != null && destColumn != null) {
            ColumnMapping mapping = new ColumnMapping(SOURCE_TABLE_NAME, sourceColumn, DEST_TABLE_NAME, destColumn);

            // Prevent duplicate mappings
            boolean isDuplicate = false;
            for (ColumnMapping existingMapping : resourceMappings) {
                if (existingMapping.getSourceColumn().getName().equals(mapping.getSourceColumn().getName()) &&
                        existingMapping.getDestinationColumn().getName().equals(mapping.getDestinationColumn().getName())) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                resourceMappings.add(mapping);

                if (removeResourceMappingButton != null) {
                    removeResourceMappingButton.setDisable(false);
                }

                // Check if execute button should be enabled
                checkExecuteButtonStatus();

                if (logTextArea != null) {
                    logTextArea.appendText("Added resource mapping: " + sourceColumn.getName() +
                            " → " + destColumn.getName() + "\n");
                }
            } else {
                showError("Mapping Error", "This column mapping already exists.");
            }
        } else {
            showError("Mapping Error", "Please select both source and destination columns.");
        }
    }

    private void removeResourceMapping() {
        ColumnMapping selectedMapping = resourceMappingsListView.getSelectionModel().getSelectedItem();
        if (selectedMapping != null) {
            resourceMappings.remove(selectedMapping);

            if (logTextArea != null) {
                logTextArea.appendText("Removed mapping: " + selectedMapping + "\n");
            }

            if (resourceMappings.isEmpty()) {
                if (removeResourceMappingButton != null) {
                    removeResourceMappingButton.setDisable(true);
                }

                // Check if execute button should be disabled
                checkExecuteButtonStatus();
            }
        }
    }

    private void executeResourceIntegration() {
        if (resourceMappings.isEmpty()) {
            showError("Integration Error", "No column mappings defined.");
            return;
        }

        if (selectedSourceResource == null || selectedDestResource == null) {
            showError("Resource Selection Required", "Please select both source and destination resources.");
            return;
        }

        try {
            // Create WHERE clauses for source and destination resources based on their ID field names
            String sourceWhereClause = "id = " + selectedSourceResource.getId(); // For P6 rsrc table
            String destWhereClause = "person_id = " + selectedDestResource.getId(); // For EBS hr_all_people table

            // Create integration service
            DataIntegrationService integrationService = new DataIntegrationService(sourceDbManager, destDbManager);

            // Perform integration
            int rowsUpdated = integrationService.integrateData(resourceMappings, sourceWhereClause, destWhereClause);

            if (logTextArea != null) {
                logTextArea.appendText("Resource integration completed successfully.\n");
                logTextArea.appendText("Updated " + rowsUpdated + " rows for resource " +
                        selectedDestResource.getName() + " (ID: " + selectedDestResource.getId() + ")\n");
            }

        } catch (SQLException e) {
            showError("Integration Error", e.getMessage());
            if (logTextArea != null) {
                logTextArea.appendText("Integration failed: " + e.getMessage() + "\n");
            }
        }
    }

    public void forceResourceLoading() {
        System.out.println("RESOURCE INTEGRATION: Forcing resource loading");
        if (sourceDbManager != null) {
            System.out.println("RESOURCE INTEGRATION: Loading source columns for 'rsrc' table");
            loadTableColumns(true);
        }
        if (destDbManager != null) {
            System.out.println("RESOURCE INTEGRATION: Loading destination columns for 'hr_all_people' table");
            loadTableColumns(false);
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