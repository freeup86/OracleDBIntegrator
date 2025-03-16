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
    @FXML private TextArea sourceResourceDetailsArea;
    @FXML private TextArea destResourceDetailsArea;

    // Controller properties
    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;

    private Resource selectedSourceResource;
    private Resource selectedDestResource;
    private ObservableList<ColumnMapping> resourceMappings = FXCollections.observableArrayList();

    // Fixed table names
    private static final String SOURCE_TABLE_NAME = "HR_ALL_PEOPLE"; // EBS resources
    private static final String DEST_TABLE_NAME = "RSRC"; // P6 resources

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
        }

        if (destColumnsListView != null) {
            destColumnsListView.setItems(FXCollections.observableArrayList());
        }

        if (resourceMappingsListView != null) {
            resourceMappingsListView.setItems(resourceMappings);
        }

        // Initialize labels
        if (sourceResourceLabel != null) {
            sourceResourceLabel.setText("No EBS resource selected");
        }

        if (destResourceLabel != null) {
            destResourceLabel.setText("No P6 resource selected");
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

            if (selectDestResourceButton != null) {
                selectDestResourceButton.setDisable(false);
            }
        }
    }

    private void selectResource(boolean isSource) {
        DatabaseConnectionManager dbManager = isSource ? sourceDbManager : destDbManager;
        if (dbManager == null) {
            showError("Connection Required",
                    "Please connect to " + (isSource ? "EBS" : "P6") + " database first.");
            return;
        }

        String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;

        ResourceSelectionDialog dialog = new ResourceSelectionDialog(dbManager, tableName, !isSource);

        Optional<Integer> result = dialog.showAndWait();
        if (result.isPresent() && result.get() > 0) {
            try {
                Resource selectedResource = getResourceById(dbManager, tableName, result.get());

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
                        logTextArea.appendText("Selected " + (isSource ? "EBS" : "P6") +
                                " resource: " + selectedResource.getName() +
                                " (ID: " + selectedResource.getId() + ")\n");
                    }

                    // Check execute button status
                    checkExecuteButtonStatus();
                }
            } catch (SQLException e) {
                showError("Database Error", "Failed to retrieve resource details: " + e.getMessage());
            }
        }
    }

    private Resource getResourceById(DatabaseConnectionManager dbManager, String tableName, int resourceId) throws SQLException {
        String query;
        String idColumn;
        String nameColumn;
        String emailColumn;

        if (tableName.equals("HR_ALL_PEOPLE")) {
            // EBS resource table
            idColumn = "PERSON_ID";
            nameColumn = "FULL_NAME";
            emailColumn = "EMAIL_ADDRESS";
            query = "SELECT " + idColumn + ", " + nameColumn + ", " + emailColumn +
                    " FROM " + tableName + " WHERE " + idColumn + " = ?";
        } else {
            // P6 resource table
            idColumn = "ID";
            nameColumn = "NAME";
            emailColumn = "EMAIL";
            query = "SELECT " + idColumn + ", " + nameColumn + ", " + emailColumn +
                    " FROM " + tableName + " WHERE " + idColumn + " = ?";
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, resourceId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Resource(
                            rs.getInt(idColumn),
                            rs.getString(nameColumn),
                            rs.getString(emailColumn)
                    );
                }
            }
        }

        return null;
    }

    private void updateResourceLabel(boolean isSource) {
        Resource resource = isSource ? selectedSourceResource : selectedDestResource;
        Label label = isSource ? sourceResourceLabel : destResourceLabel;
        TextArea detailsArea = isSource ? sourceResourceDetailsArea : destResourceDetailsArea;

        if (resource == null) {
            label.setText("No " + (isSource ? "EBS" : "P6") + " resource selected");
            detailsArea.clear();
            detailsArea.setPromptText("Selected " + (isSource ? "EBS" : "P6") + " Resource Will Appear Here");
        } else {
            label.setText((isSource ? "EBS" : "P6") + " Resource: " + resource.getName());

            // Build resource details
            detailsArea.setText(
                    resource.getName() +
                            " (ID: " + resource.getId() +
                            ", Email: " + resource.getEmail() + ")"
            );
        }
    }

    private void loadTableColumns(boolean isSource) {
        try {
            String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;
            DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
            ListView<TableColumn> listView = isSource ? sourceColumnsListView : destColumnsListView;

            if (metadataService == null || listView == null) {
                System.err.println("Cannot load columns - metadata service or list view is null");
                return;
            }

            List<TableColumn> columns = metadataService.getTableColumns(tableName);

            Platform.runLater(() -> {
                listView.setItems(FXCollections.observableArrayList(columns));
                checkMappingAvailability();
            });
        } catch (SQLException e) {
            System.err.println("Error loading table columns for " + (isSource ? "source" : "destination") + " table: " + e.getMessage());
            e.printStackTrace();
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

    private void addResourceMapping() {
        TableColumn sourceColumn = sourceColumnsListView.getSelectionModel().getSelectedItem();
        TableColumn destColumn = destColumnsListView.getSelectionModel().getSelectedItem();

        if (sourceColumn != null && destColumn != null) {
            ColumnMapping mapping = new ColumnMapping(SOURCE_TABLE_NAME, sourceColumn, DEST_TABLE_NAME, destColumn);

            // Prevent duplicate mappings
            boolean isDuplicate = resourceMappings.stream()
                    .anyMatch(m -> m.getSourceColumn().getName().equals(mapping.getSourceColumn().getName()) &&
                            m.getDestinationColumn().getName().equals(mapping.getDestinationColumn().getName()));

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
            showError("Resource Selection Required",
                    "Please select both source and destination resources.");
            return;
        }

        try {
            // Create integration service
            DataIntegrationService integrationService =
                    new DataIntegrationService(sourceDbManager, destDbManager);

            // Log current integration pair
            if (logTextArea != null) {
                logTextArea.appendText("Integrating source resource (EBS) " +
                        selectedSourceResource.getName() + " (ID: " + selectedSourceResource.getId() +
                        ") to destination resource (P6) " + selectedDestResource.getName() +
                        " (ID: " + selectedDestResource.getId() + ")\n");
            }

            // Create WHERE clauses for source and destination resources
            String sourceWhereClause = "PERSON_ID = " + selectedSourceResource.getId();
            String destWhereClause = "ID = " + selectedDestResource.getId();

            // Perform integration
            int rowsUpdated = integrationService.integrateData(
                    resourceMappings,
                    sourceWhereClause,
                    destWhereClause
            );

            if (logTextArea != null) {
                logTextArea.appendText("Resource integration completed. " +
                        "Total rows updated: " + rowsUpdated + "\n");
            }

        } catch (SQLException e) {
            showError("Integration Error", e.getMessage());
            if (logTextArea != null) {
                logTextArea.appendText("Integration failed: " + e.getMessage() + "\n");
            }
        }
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

    public void forceResourceLoading() {
        System.out.println("RESOURCE INTEGRATION: Forcing resource loading");
        if (sourceDbManager != null) {
            System.out.println("RESOURCE INTEGRATION: Loading source columns for '" + SOURCE_TABLE_NAME + "' table");
            loadTableColumns(true);
        }
        if (destDbManager != null) {
            System.out.println("RESOURCE INTEGRATION: Loading destination columns for '" + DEST_TABLE_NAME + "' table");
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