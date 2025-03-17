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
import java.util.*;

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

    // Modified to support multiple source resources
    private List<Resource> selectedSourceResources = new ArrayList<>();
    private Resource selectedDestResource;
    private ObservableList<ColumnMapping> resourceMappings = FXCollections.observableArrayList();

    // Fixed table names - updated to reflect EBS as source, P6 as destination
    private static final String SOURCE_TABLE_NAME = "HR_ALL_PEOPLE"; // EBS resources as source
    private static final String DEST_TABLE_NAME = "RSRC"; // P6 resources as destination

    // Flag to detect if we're in test mode
    private boolean isTestMode = false;

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
            sourceResourceLabel.setText("No EBS resources selected");
        }

        if (destResourceLabel != null) {
            destResourceLabel.setText("Optional: Select P6 resource (for update only)");
        }

        // Update execute button text to reflect merge behavior
        if (executeResourceIntegrationButton != null) {
            executeResourceIntegrationButton.setText("Create/Update P6 Resources");
        }

        // Hide manual mapping buttons as we'll use auto-mapping
        if (addResourceMappingButton != null) {
            addResourceMappingButton.setVisible(false);
        }
    }

    private void setupEventHandlers() {
        if (selectSourceResourceButton != null) {
            selectSourceResourceButton.setOnAction(event -> selectResource(true));
            selectSourceResourceButton.setTooltip(new Tooltip("Select EBS resources from HR_ALL_PEOPLE table"));
        }

        if (selectDestResourceButton != null) {
            selectDestResourceButton.setOnAction(event -> selectResource(false));
            selectDestResourceButton.setTooltip(new Tooltip("Select a P6 resource from RSRC table (optional)"));
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
            // Check if we're in test mode
            try {
                Connection conn = sourceDbManager.getConnection();
                String url = conn.getMetaData().getURL();
                isTestMode = url.contains("mem:sourcedb") || url.contains("mem:destdb");
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error checking database: " + e.getMessage());
            }

            this.sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
            loadTableColumns(true);

            // Always enable the source resource selection button when a connection is available
            Platform.runLater(() -> {
                if (selectSourceResourceButton != null) {
                    selectSourceResourceButton.setDisable(false);
                }
            });
        }
    }

    public void setDestDbManager(DatabaseConnectionManager destDbManager) {
        System.out.println("ResourceIntegrationController - setDestDbManager CALLED");
        this.destDbManager = destDbManager;
        if (destDbManager != null) {
            this.destMetadataService = new DatabaseMetadataService(destDbManager);
            loadTableColumns(false);

            // Always enable the destination resource selection button when a connection is available
            Platform.runLater(() -> {
                if (selectDestResourceButton != null) {
                    selectDestResourceButton.setDisable(false);
                }
            });
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

        // Important: For test mode, override the database manager to use the correct one for each table
        DatabaseConnectionManager effectiveDbManager = dbManager;

        if (isTestMode) {
            // In test mode, HR_ALL_PEOPLE is in destDbManager and RSRC is in sourceDbManager
            if (tableName.equals("HR_ALL_PEOPLE") && destDbManager != null) {
                effectiveDbManager = destDbManager;
                System.out.println("Using destination DB manager for HR_ALL_PEOPLE table");
            } else if (tableName.equals("RSRC") && sourceDbManager != null) {
                effectiveDbManager = sourceDbManager;
                System.out.println("Using source DB manager for RSRC table");
            }
        }

        // Ensure we have a valid database connection after switching
        if (effectiveDbManager == null) {
            showError("Database Error",
                    "No valid database connection available for the " + tableName + " table.");
            return;
        }

        ResourceSelectionDialog dialog = new ResourceSelectionDialog(effectiveDbManager, tableName, !isSource);

        // Enable multi-select for EBS resources (source)
        if (isSource) {
            dialog.enableMultiSelect(true);
        } else {
            dialog.enableMultiSelect(false);
        }

        Optional<List<Integer>> result = dialog.showAndWait();

        if (result.isPresent() && !result.get().isEmpty()) {
            try {
                if (isSource) {
                    // Handle multiple selections for EBS resources
                    selectedSourceResources.clear();

                    for (Integer resourceId : result.get()) {
                        Resource resource = getResourceById(effectiveDbManager, tableName, resourceId);
                        if (resource != null) {
                            selectedSourceResources.add(resource);
                        }
                    }

                    updateSourceResourceLabel();

                    // Log selection
                    if (logTextArea != null) {
                        logTextArea.appendText("Selected " + selectedSourceResources.size() +
                                " EBS resource(s)\n");
                    }
                } else {
                    // For P6 resource (destination), still single selection
                    int resourceId = result.get().get(0);
                    selectedDestResource = getResourceById(effectiveDbManager, tableName, resourceId);

                    if (selectedDestResource != null) {
                        updateResourceLabel(false);

                        // Log selection
                        if (logTextArea != null) {
                            logTextArea.appendText("Selected P6 resource: " +
                                    selectedDestResource.getName() + " (ID: " +
                                    selectedDestResource.getId() + ")\n");
                        }
                    }
                }

                // Check execute button status
                checkExecuteButtonStatus();

            } catch (SQLException e) {
                System.err.println("SQL Error retrieving resource: " + e.getMessage());
                e.printStackTrace();
                showError("Database Error", "Failed to retrieve resource details: " + e.getMessage());
            }
        }
    }

    private Resource getResourceById(DatabaseConnectionManager dbManager, String tableName, int resourceId) throws SQLException {
        System.out.println("Getting resource by ID: " + resourceId + " from table: " + tableName);

        String query;
        String idColumn;
        String nameColumn;
        String emailColumn;

        if (tableName.equals("HR_ALL_PEOPLE")) {
            // EBS resource table
            idColumn = "PERSON_ID";
            nameColumn = "FULL_NAME";
            emailColumn = "EMAIL_ADDRESS";
        } else if (tableName.equals("RSRC")) {
            // P6 resource table
            idColumn = "RSRC_ID";
            nameColumn = "NAME";
            emailColumn = "EMAIL";
        } else {
            // Default for other tables
            idColumn = "ID";
            nameColumn = "NAME";
            emailColumn = "EMAIL";
        }

        // Verify the table structure
        boolean columnsExist = true;
        try (Connection conn = dbManager.getConnection()) {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            ResultSet columns = meta.getColumns(null, null, tableName, null);

            java.util.Set<String> columnNames = new java.util.HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME").toUpperCase());
            }

            if (!columnNames.contains(idColumn.toUpperCase())) {
                System.err.println("ID column '" + idColumn + "' not found in table " + tableName);
                columnsExist = false;
            }
            if (!columnNames.contains(nameColumn.toUpperCase())) {
                System.err.println("Name column '" + nameColumn + "' not found in table " + tableName);
                columnsExist = false;
            }
            if (!columnNames.contains(emailColumn.toUpperCase())) {
                System.err.println("Email column '" + emailColumn + "' not found in table " + tableName);
                columnsExist = false;
            }
        }

        // If columns don't exist, try a more generic approach
        if (!columnsExist) {
            System.out.println("Attempting generic query approach for " + tableName);
            try (Connection conn = dbManager.getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE " + idColumn + " = " + resourceId)) {

                if (rs.next()) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    // Find ID, name, and email columns by best guess
                    int idColIndex = -1;
                    int nameColIndex = -1;
                    int emailColIndex = -1;

                    for (int i = 1; i <= columnCount; i++) {
                        String colName = meta.getColumnName(i).toUpperCase();
                        if ((colName.contains("ID") || colName.contains("PERSON") || colName.contains("RSRC")) && idColIndex == -1) {
                            idColIndex = i;
                        } else if ((colName.contains("NAME") || colName.contains("FULL")) && nameColIndex == -1) {
                            nameColIndex = i;
                        } else if (colName.contains("EMAIL") && emailColIndex == -1) {
                            emailColIndex = i;
                        }
                    }

                    if (idColIndex != -1 && nameColIndex != -1) {
                        int id = rs.getInt(idColIndex);
                        String name = rs.getString(nameColIndex);
                        String email = emailColIndex != -1 ? rs.getString(emailColIndex) : "";

                        System.out.println("Found resource: " + name + " (ID: " + id + ") with email: " + email);
                        return new Resource(id, name, email);
                    }
                }
            }
            return null;
        }

        // Use the standard approach if columns exist
        query = "SELECT " + idColumn + ", " + nameColumn + ", " + emailColumn +
                " FROM " + tableName + " WHERE " + idColumn + " = ?";

        System.out.println("Executing query: " + query.replace("?", String.valueOf(resourceId)));

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, resourceId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Resource resource = new Resource(
                            rs.getInt(idColumn),
                            rs.getString(nameColumn),
                            rs.getString(emailColumn)
                    );
                    System.out.println("Retrieved resource: " + resource.getName() + " (ID: " + resource.getId() + ")");
                    return resource;
                } else {
                    System.out.println("No resource found with ID: " + resourceId);
                }
            }
        }

        return null;
    }

    // Method for handling a single resource (existing)
    private void updateResourceLabel(boolean isSource) {
        if (isSource) {
            // For source resources, use the new multi-resource method
            updateSourceResourceLabel();
        } else {
            // For destination resource
            if (selectedDestResource == null) {
                destResourceLabel.setText("No P6 resource selected");
                destResourceDetailsArea.clear();
                destResourceDetailsArea.setPromptText("Selected P6 Resource Will Appear Here");
            } else {
                destResourceLabel.setText("P6 Resource: " + selectedDestResource.getName());

                // Build resource details
                destResourceDetailsArea.setText(
                        selectedDestResource.getName() +
                                " (ID: " + selectedDestResource.getId() +
                                ", Email: " + selectedDestResource.getEmail() + ")"
                );
            }
        }
    }

    // New method for handling multiple source resources
    private void updateSourceResourceLabel() {
        if (selectedSourceResources.isEmpty()) {
            sourceResourceLabel.setText("No EBS resources selected");
            sourceResourceDetailsArea.clear();
            sourceResourceDetailsArea.setPromptText("Selected EBS Resources Will Appear Here");
        } else {
            int count = selectedSourceResources.size();
            sourceResourceLabel.setText("EBS Resources: " + count + " selected");

            // Build resource details
            StringBuilder details = new StringBuilder();
            for (Resource resource : selectedSourceResources) {
                details.append(resource.getName())
                        .append(" (ID: ").append(resource.getId())
                        .append(", Email: ").append(resource.getEmail())
                        .append(")\n");
            }

            sourceResourceDetailsArea.setText(details.toString());
        }
    }

    private void loadTableColumns(boolean isSource) {
        try {
            String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;

            // For test mode, use the appropriate database connection for each table
            DatabaseConnectionManager dbManager;
            if (isTestMode) {
                if (tableName.equals("HR_ALL_PEOPLE")) {
                    dbManager = destDbManager;
                } else {
                    dbManager = sourceDbManager;
                }
            } else {
                dbManager = isSource ? sourceDbManager : destDbManager;
            }

            // Add null check here
            if (dbManager == null) {
                System.err.println("Cannot load columns - database manager is null for " +
                        (isSource ? "source" : "destination") + " table: " + tableName);
                return;
            }

            DatabaseMetadataService metadataService = new DatabaseMetadataService(dbManager);
            ListView<TableColumn> listView = isSource ? sourceColumnsListView : destColumnsListView;

            if (metadataService == null || listView == null) {
                System.err.println("Cannot load columns - metadata service or list view is null");
                return;
            }

            List<TableColumn> columns = metadataService.getTableColumns(tableName);

            Platform.runLater(() -> {
                listView.setItems(FXCollections.observableArrayList(columns));

                // If both source and destination columns are loaded, create automatic mappings
                if (!sourceColumnsListView.getItems().isEmpty() && !destColumnsListView.getItems().isEmpty()) {
                    autoCreateResourceMappings();
                }
            });
        } catch (SQLException e) {
            System.err.println("Error loading table columns for " + (isSource ? "source" : "destination") +
                    " table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Automatic mapping creation between HR_ALL_PEOPLE and RSRC tables
    private void autoCreateResourceMappings() {
        if (sourceColumnsListView.getItems().isEmpty() || destColumnsListView.getItems().isEmpty()) {
            logTextArea.appendText("Cannot create mappings: column lists not loaded\n");
            return;
        }

        // Clear existing mappings
        resourceMappings.clear();

        // Create a mapping for known equivalent fields
        Map<String, String> knownMappings = new HashMap<>();
        knownMappings.put("PERSON_ID", "RSRC_ID");
        knownMappings.put("FULL_NAME", "NAME");
        knownMappings.put("EMAIL_ADDRESS", "EMAIL");
        knownMappings.put("PHONE_NUMBER", "PHONE");
        knownMappings.put("DEPARTMENT_NAME", "DEPARTMENT");
        knownMappings.put("JOB_TITLE", "ROLE");

        // Find matching columns in both tables
        for (TableColumn sourceColumn : sourceColumnsListView.getItems()) {
            String sourceColumnName = sourceColumn.getName();

            // Check if this source column has a known mapping
            if (knownMappings.containsKey(sourceColumnName)) {
                String destColumnName = knownMappings.get(sourceColumnName);

                // Find the matching destination column
                TableColumn destColumn = null;
                for (TableColumn col : destColumnsListView.getItems()) {
                    if (col.getName().equals(destColumnName)) {
                        destColumn = col;
                        break;
                    }
                }

                if (destColumn != null) {
                    // Create the mapping
                    ColumnMapping mapping = new ColumnMapping(SOURCE_TABLE_NAME, sourceColumn, DEST_TABLE_NAME, destColumn);
                    resourceMappings.add(mapping);
                    logTextArea.appendText("Created automatic mapping: " + sourceColumnName + " → " + destColumnName + "\n");
                }
            }
        }

        if (resourceMappings.isEmpty()) {
            logTextArea.appendText("No automatic mappings could be created\n");
        } else {
            logTextArea.appendText("Created " + resourceMappings.size() + " automatic mappings\n");
            // Update the UI
            resourceMappingsListView.setItems(resourceMappings);

            // Enable/disable buttons
            removeResourceMappingButton.setDisable(resourceMappings.isEmpty());
            checkExecuteButtonStatus();
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

    private void checkExecuteButtonStatus() {
        boolean canExecute = !selectedSourceResources.isEmpty() && !resourceMappings.isEmpty();

        Platform.runLater(() -> {
            if (executeResourceIntegrationButton != null) {
                executeResourceIntegrationButton.setDisable(!canExecute);
            }
        });
    }

    private void executeResourceIntegration() {
        if (resourceMappings.isEmpty()) {
            // Try to create mappings if they don't exist
            autoCreateResourceMappings();

            if (resourceMappings.isEmpty()) {
                showError("Integration Error", "No column mappings defined and automatic mapping failed.");
                return;
            }
        }

        if (selectedSourceResources.isEmpty()) {
            showError("Resource Selection Required", "Please select at least one source EBS resource.");
            return;
        }

        try {
            // For test mode, make sure to use the right DB connections
            DatabaseConnectionManager ebsDbManager;
            DatabaseConnectionManager p6DbManager;

            if (isTestMode) {
                ebsDbManager = destDbManager; // HR_ALL_PEOPLE is in destdb in test mode
                p6DbManager = sourceDbManager; // RSRC is in sourcedb in test mode
            } else {
                ebsDbManager = sourceDbManager; // Normal mode: EBS is the source
                p6DbManager = destDbManager;    // Normal mode: P6 is the destination
            }

            // Make sure we have valid database connections
            if (ebsDbManager == null || p6DbManager == null) {
                showError("Database Error", "Missing database connection for integration.");
                return;
            }

            // Create integration service
            DataIntegrationService integrationService = new DataIntegrationService(ebsDbManager, p6DbManager);

            int totalRowsMerged = 0;

            // Process each selected source resource
            for (Resource sourceResource : selectedSourceResources) {
                // Log current integration
                if (logTextArea != null) {
                    logTextArea.appendText("Processing EBS resource: " +
                            sourceResource.getName() + " (ID: " + sourceResource.getId() + ")\n");
                }

                // Create source WHERE clause for this resource
                String sourceWhereClause = "PERSON_ID = " + sourceResource.getId();

                // Use email as the match column between EBS and P6
                String sourceMatchColumn = "EMAIL_ADDRESS";
                String destMatchColumn = "EMAIL";

                // Perform integration using the merge method
                int rowsMerged = integrationService.mergeData(
                        resourceMappings,
                        sourceWhereClause,
                        destMatchColumn,
                        sourceMatchColumn
                );

                totalRowsMerged += rowsMerged;
            }

            if (logTextArea != null) {
                logTextArea.appendText("Resource integration completed for " +
                        selectedSourceResources.size() + " resources. " +
                        "Total rows merged (created or updated): " + totalRowsMerged + "\n");
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