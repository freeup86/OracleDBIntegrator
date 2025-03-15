package com.dbintegrator.controller;

import com.dbintegrator.model.ColumnMapping;
import com.dbintegrator.model.Project;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.service.DatabaseMetadataService;
import com.dbintegrator.service.DataIntegrationService;
import com.dbintegrator.ui.MultiProjectSelectionDialog;
import com.dbintegrator.util.ConfigurationManager;
import com.dbintegrator.util.DatabaseConnectionManager;
import com.dbintegrator.util.TestDatabaseManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainController {
    // Constants for fixed table names
    private static final String SOURCE_TABLE_NAME = "PROJECTS";
    private static final String DEST_TABLE_NAME = "PA_PROJECTS";

    // Project Integration Tab Components
    @FXML private RadioButton sourceToDestRadio;
    @FXML private RadioButton destToSourceRadio;
    @FXML private ToggleGroup directionToggle;
    @FXML private Label sourceDbLabel;
    @FXML private Label destDbLabel;
    @FXML private Button connectSourceButton;
    @FXML private Button connectDestButton;
    @FXML private ListView<TableColumn> sourceColumnsListView;
    @FXML private ListView<TableColumn> destColumnsListView;
    @FXML private ListView<ColumnMapping> mappingsListView;
    @FXML private Button addMappingButton;
    @FXML private Button removeMappingButton;
    @FXML private Button executeButton;
    @FXML private TextArea logTextArea;
    @FXML private Label sourceProjectsLabel;
    @FXML private Label destProjectsLabel;
    @FXML private Button selectSourceProjectsButton;
    @FXML private Button selectDestProjectsButton;
    @FXML private Button testModeButton;
    @FXML private Button openH2ConsoleButton;
    @FXML private Button verifyResultsButton;
    @FXML private TextArea sourceProjectDetailsArea;
    @FXML private TextArea destProjectDetailsArea;

    // Controllers for other tabs
    @FXML private TaskIntegrationController taskIntegrationController;
    @FXML private ResourceIntegrationController resourceIntegrationController;

    // Database and Project Management
    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;
    private ObservableList<ColumnMapping> mappings = FXCollections.observableArrayList();
    private ConfigurationManager configManager;

    // Multi-project selection fields
    private List<Project> selectedSourceProjects = new ArrayList<>();
    private List<Project> selectedDestProjects = new ArrayList<>();

    // Test mode flag
    private boolean testModeEnabled = false;

    @FXML
    public void initialize() {
        setupUIComponents();
        setupEventHandlers();
        initializeConfigManager();
        setupMappingsList();
        tryAutoConnect();

        // Ensure integration tabs are setup
        Platform.runLater(() -> {
            if (sourceDbManager != null && destDbManager != null) {
                setupTaskIntegrationTab();
                setupResourceIntegrationTab();
            }
        });
    }

    private void setupTaskIntegrationTab() {
        try {
            if (taskIntegrationController == null) {
                System.err.println("TaskIntegrationController reference is null");
                return;
            }

            // Set database managers
            if (sourceDbManager != null) {
                System.out.println("Setting source DB manager in Task Integration Tab");
                taskIntegrationController.setSourceDbManager(sourceDbManager);
            }

            if (destDbManager != null) {
                System.out.println("Setting destination DB manager in Task Integration Tab");
                taskIntegrationController.setDestDbManager(destDbManager);
            }

            // Force reload of projects
            taskIntegrationController.forceProjectLoading();

        } catch (Exception e) {
            System.err.println("Error in setupTaskIntegrationTab:");
            e.printStackTrace();
        }
    }

    private void setupResourceIntegrationTab() {
        try {
            if (resourceIntegrationController == null) {
                System.err.println("ResourceIntegrationController reference is null");
                return;
            }

            System.out.println("Setting up Resource Integration Tab");

            // Set database managers
            if (sourceDbManager != null) {
                System.out.println("Setting source DB manager in Resource Integration Tab");
                resourceIntegrationController.setSourceDbManager(sourceDbManager);
            }

            if (destDbManager != null) {
                System.out.println("Setting destination DB manager in Resource Integration Tab");
                resourceIntegrationController.setDestDbManager(destDbManager);
            }

            // Force reload of resources
            resourceIntegrationController.forceResourceLoading();

        } catch (Exception e) {
            System.err.println("Error in setupResourceIntegrationTab:");
            e.printStackTrace();
        }
    }

    // Add this helper method
    private void printTableContents(DatabaseConnectionManager dbManager, String tableName) {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, description FROM " + tableName)) {

            System.out.println("Contents of " + tableName + " table:");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("Project " + count + ": " +
                        "ID=" + rs.getInt("id") +
                        ", Name=" + rs.getString("name") +
                        ", Description=" + rs.getString("description"));
            }

            if (count == 0) {
                System.err.println("NO PROJECTS FOUND IN TABLE: " + tableName);
            }
        } catch (SQLException e) {
            System.err.println("Error printing table contents for " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupUIComponents() {
        // Set up direction toggle
        directionToggle = new ToggleGroup();
        sourceToDestRadio.setToggleGroup(directionToggle);
        destToSourceRadio.setToggleGroup(directionToggle);
        sourceToDestRadio.setSelected(true);

        // Disable buttons initially
        disableIntegrationControls();
    }

    private void setupEventHandlers() {
        // Database connection buttons
        connectSourceButton.setOnAction(event -> connectToDatabase(true));
        connectDestButton.setOnAction(event -> connectToDatabase(false));

        // Project selection buttons
        selectSourceProjectsButton.setOnAction(event -> selectProjects(true));
        selectDestProjectsButton.setOnAction(event -> selectProjects(false));

        // Mapping buttons
        addMappingButton.setOnAction(event -> addMapping());
        removeMappingButton.setOnAction(event -> removeMapping());

        // Test mode and integration buttons
        testModeButton.setOnAction(event -> setupTestMode());
        openH2ConsoleButton.setOnAction(event -> openH2Console());
        executeButton.setOnAction(event -> executeIntegration());
        verifyResultsButton.setOnAction(event -> verifyIntegrationResults());
    }

    private void setupMappingsList() {
        mappingsListView.setItems(mappings);
    }

    private void initializeConfigManager() {
        configManager = new ConfigurationManager();
    }

    private void disableIntegrationControls() {
        selectSourceProjectsButton.setDisable(true);
        selectDestProjectsButton.setDisable(true);
        addMappingButton.setDisable(true);
        removeMappingButton.setDisable(true);
        executeButton.setDisable(true);
        openH2ConsoleButton.setDisable(true);
        verifyResultsButton.setDisable(true);
    }

    private void tryAutoConnect() {
        // Try source connection
        if (configManager.hasSourceConnection()) {
            try {
                sourceDbManager = configManager.getSourceConnection();
                if (sourceDbManager != null) {
                    sourceDbLabel.setText("Source: " + sourceDbManager.getConnectionInfo());
                    sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
                    loadTableColumns(true);
                    logTextArea.appendText("Connected to source database using saved credentials\n");
                    selectSourceProjectsButton.setDisable(false);
                }
            } catch (Exception e) {
                logTextArea.appendText("Failed to connect to source with saved credentials: " + e.getMessage() + "\n");
            }
        }

        // Try destination connection
        if (configManager.hasDestConnection()) {
            try {
                destDbManager = configManager.getDestConnection();
                if (destDbManager != null) {
                    destDbLabel.setText("Destination: " + destDbManager.getConnectionInfo());
                    destMetadataService = new DatabaseMetadataService(destDbManager);
                    loadTableColumns(false);
                    logTextArea.appendText("Connected to destination database using saved credentials\n");
                    selectDestProjectsButton.setDisable(false);
                }
            } catch (Exception e) {
                logTextArea.appendText("Failed to connect to destination with saved credentials: " + e.getMessage() + "\n");
            }
        }

        // Enable column selections if both connections established
        if (sourceDbManager != null && destDbManager != null) {
            // Setup integration tabs
            setupTaskIntegrationTab();
            setupResourceIntegrationTab();
        }
    }

    private void connectToDatabase(boolean isSource) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/dbintegrator/ui/database_connection.fxml"));
            VBox root = loader.load();
            DatabaseConnectionController controller = loader.getController();

            // Set source/destination flag and load saved connection if available
            controller.setIsSource(isSource);
            controller.loadSavedConnection();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Connect to " + (isSource ? "P6" : "EBS") + " Database");
            dialog.setScene(new javafx.scene.Scene(root));
            dialog.showAndWait();

            if (controller.getConnectionManager() != null) {
                if (isSource) {
                    sourceDbManager = controller.getConnectionManager();
                    sourceDbLabel.setText("Source: " + sourceDbManager.getConnectionInfo());
                    sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
                    loadTableColumns(true);
                    selectSourceProjectsButton.setDisable(false);
                } else {
                    destDbManager = controller.getConnectionManager();
                    destDbLabel.setText("Destination: " + destDbManager.getConnectionInfo());
                    destMetadataService = new DatabaseMetadataService(destDbManager);
                    loadTableColumns(false);
                    selectDestProjectsButton.setDisable(false);
                }

                // Enable column selections when both connections are established
                if (sourceDbManager != null && destDbManager != null) {
                    // Setup integration tabs
                    setupTaskIntegrationTab();
                    setupResourceIntegrationTab();
                }
            }
        } catch (IOException e) {
            showError("Connection Error", e.getMessage());
        }
    }

    private void loadTableColumns(boolean isSource) {
        try {
            String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;
            DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
            ListView<TableColumn> listView = isSource ? sourceColumnsListView : destColumnsListView;

            if (metadataService != null) {
                List<TableColumn> columns = metadataService.getTableColumns(tableName);
                listView.setItems(FXCollections.observableArrayList(columns));

                // Enable add mapping when both column lists populated
                if (!sourceColumnsListView.getItems().isEmpty() && !destColumnsListView.getItems().isEmpty()) {
                    addMappingButton.setDisable(false);
                }
            }
        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private void selectProjects(boolean isSource) {
        DatabaseConnectionManager dbManager = isSource ? sourceDbManager : destDbManager;
        if (dbManager == null) {
            showError("Connection Required",
                    "Please connect to " + (isSource ? "source" : "destination") + " database first.");
            return;
        }

        String tableName = isSource ? SOURCE_TABLE_NAME : DEST_TABLE_NAME;
        MultiProjectSelectionDialog dialog = new MultiProjectSelectionDialog(dbManager, tableName, isSource);

        dialog.showAndWait().ifPresent(selectedProjects -> {
            if (selectedProjects != null && !selectedProjects.isEmpty()) {
                // Store selected projects
                if (isSource) {
                    this.selectedSourceProjects = selectedProjects;
                    updateProjectsLabel(true);
                } else {
                    this.selectedDestProjects = selectedProjects;
                    updateProjectsLabel(false);
                }

                // Log selection
                logTextArea.appendText("Selected " + selectedProjects.size() + " " +
                        (isSource ? "source" : "destination") + " projects\n");

                // Enable execute button when both source and destination projects are selected and mappings exist
                if (!this.selectedSourceProjects.isEmpty() && !this.selectedDestProjects.isEmpty() && !mappings.isEmpty()) {
                    executeButton.setDisable(false);
                }
            }
        });
    }

    private void updateProjectsLabel(boolean isSource) {
        List<Project> projects = isSource ? selectedSourceProjects : selectedDestProjects;
        Label label = isSource ? sourceProjectsLabel : destProjectsLabel;
        TextArea projectDetailsArea = isSource ? sourceProjectDetailsArea : destProjectDetailsArea;

        if (projects.isEmpty()) {
            label.setText("No " + (isSource ? "source" : "destination") + " projects selected");
            projectDetailsArea.clear();
            projectDetailsArea.setPromptText("Selected Project IDs Will Appear Here");
        } else {
            label.setText((isSource ? "Source" : "Destination") + " Projects: " + projects.size() + " selected");

            // Build project ID list
            StringBuilder projectIds = new StringBuilder();
            for (Project project : projects) {
                projectIds.append(project.getName()).append(" (ID: ").append(project.getId()).append(")").append("\n");
            }

            projectDetailsArea.setText(projectIds.toString());
        }
    }

    private void addMapping() {
        TableColumn sourceColumn = sourceColumnsListView.getSelectionModel().getSelectedItem();
        TableColumn destColumn = destColumnsListView.getSelectionModel().getSelectedItem();

        if (sourceColumn != null && destColumn != null) {
            ColumnMapping mapping = new ColumnMapping(SOURCE_TABLE_NAME, sourceColumn, DEST_TABLE_NAME, destColumn);
            mappings.add(mapping);

            // Enable integration execution and remove mapping
            removeMappingButton.setDisable(false);

            // Enable execute if both sources and destinations have projects selected
            if (!selectedSourceProjects.isEmpty() && !selectedDestProjects.isEmpty()) {
                executeButton.setDisable(false);
            }

            logTextArea.appendText("Added mapping: " + sourceColumn.getName() +
                    " → " + destColumn.getName() + "\n");
        } else {
            showError("Mapping Error", "Please select columns from both source and destination tables.");
        }
    }

    private void removeMapping() {
        ColumnMapping selectedMapping = mappingsListView.getSelectionModel().getSelectedItem();
        if (selectedMapping != null) {
            mappings.remove(selectedMapping);
            logTextArea.appendText("Removed mapping: " + selectedMapping + "\n");

            if (mappings.isEmpty()) {
                removeMappingButton.setDisable(true);
                executeButton.setDisable(true);
            }
        }
    }

    private void setupTestMode() {
        try {
            // Ensure H2 driver is loaded
            Class.forName("org.h2.Driver");

            // Test if H2 is working properly
            boolean h2Working = TestDatabaseManager.testBasicH2Connection();
            logTextArea.appendText("H2 test connection: " + (h2Working ? "SUCCESS" : "FAILED") + "\n");

            if (!h2Working) {
                throw new SQLException("H2 database connection test failed");
            }

            // Set up test connections
            logTextArea.appendText("Setting up test connections...\n");
            sourceDbManager = TestDatabaseManager.getSourceTestConnection();
            destDbManager = TestDatabaseManager.getDestTestConnection();

            // Update connection labels
            sourceDbLabel.setText("Source: Test Database Connection");
            destDbLabel.setText("Destination: Test Database Connection");

            // Initialize metadata services
            sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
            destMetadataService = new DatabaseMetadataService(destDbManager);

            // Load columns
            loadTableColumns(true);
            loadTableColumns(false);

            // Enable test mode specific controls
            selectSourceProjectsButton.setDisable(false);
            selectDestProjectsButton.setDisable(false);
            openH2ConsoleButton.setDisable(false);

            // Setup integration tabs with test connections
            setupTaskIntegrationTab();
            setupResourceIntegrationTab();

            // Set test mode flag
            testModeEnabled = true;

            // Create an information dialog
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Test Mode Enabled");
                alert.setHeaderText("Test Mode is Now Active");
                alert.setContentText("Test mode has been enabled with sample databases.\n\nReturn to the 'Integration' tab to perform test integrations.");

                // Customize button
                ButtonType okButton = new ButtonType("Go to Integration Tab", ButtonBar.ButtonData.OK_DONE);
                alert.getButtonTypes().setAll(okButton);

                // Show the dialog
                Optional<ButtonType> result = alert.showAndWait();

                // If the user clicks the button, switch to the integration tab
                if (result.isPresent() && result.get() == okButton) {
                    // Assuming the TabPane is a child of the scene
                    TabPane tabPane = (TabPane) testModeButton.getScene().lookup(".tab-pane");
                    if (tabPane != null) {
                        tabPane.getSelectionModel().select(0); // Select the first tab (Database Integration)
                    }
                }
            });

            logTextArea.appendText("Test mode successfully enabled\n");

        } catch (Exception e) {
            // Comprehensive error handling
            logTextArea.appendText("Test mode setup failed: " + e.getMessage() + "\n");

            // Log full stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logTextArea.appendText("Full error details:\n" + sw.toString() + "\n");

            showError("Test Mode Error", "Failed to set up test mode: " + e.getMessage());
        }
    }

    private void executeIntegration() {
        if (mappings.isEmpty()) {
            showError("Integration Error", "No field mappings defined.");
            return;
        }

        if (selectedSourceProjects.isEmpty() || selectedDestProjects.isEmpty()) {
            showError("Project Selection Required",
                    "Please select both source and destination projects.");
            return;
        }

        try {
            // Determine integration direction
            boolean sourceToDestDirection = sourceToDestRadio.isSelected();
            DatabaseConnectionManager srcManager = sourceToDestDirection ? sourceDbManager : destDbManager;
            DatabaseConnectionManager dstManager = sourceToDestDirection ? destDbManager : sourceDbManager;

            List<Project> sourceProjects = sourceToDestDirection ? selectedSourceProjects : selectedDestProjects;
            List<Project> destProjects = sourceToDestDirection ? selectedDestProjects : selectedSourceProjects;

            // Log the operation details
            logTextArea.appendText("Direction: " +
                    (sourceToDestDirection ? "Source → Destination" : "Destination → Source") + "\n");
            logTextArea.appendText("Integrating " + sourceProjects.size() + " source projects to " +
                    destProjects.size() + " destination projects\n");

            // Adjust mappings for direction if needed
            List<ColumnMapping> directedMappings = new ArrayList<>(mappings);
            if (!sourceToDestDirection) {
                // Swap source and destination for each mapping
                directedMappings = new ArrayList<>();
                for (ColumnMapping mapping : mappings) {
                    directedMappings.add(new ColumnMapping(
                            mapping.getDestinationTable(), mapping.getDestinationColumn(),
                            mapping.getSourceTable(), mapping.getSourceColumn()
                    ));
                }
            }

            // Create integration service
            DataIntegrationService integrationService = new DataIntegrationService(srcManager, dstManager);

            int totalRowsUpdated = 0;

            // Iterate through all project combinations
            for (Project sourceProject : sourceProjects) {
                for (Project destProject : destProjects) {
                    logTextArea.appendText("Integrating source project " + sourceProject.getName() +
                            " (ID: " + sourceProject.getId() + ") to destination project " +
                            destProject.getName() + " (ID: " + destProject.getId() + ")\n");

                    // Create WHERE clauses for source and destination
                    String sourceWhereClause = "id = " + sourceProject.getId();
                    String destWhereClause = "id = " + destProject.getId();

                    // Perform the integration for this project pair
                    int rowsUpdated = integrationService.integrateData(
                            directedMappings, sourceWhereClause, destWhereClause);

                    totalRowsUpdated += rowsUpdated;

                    logTextArea.appendText("  Updated " + rowsUpdated + " rows\n");
                }
            }

            logTextArea.appendText("Integration completed. Total rows updated: " + totalRowsUpdated + "\n");

            // Enable verify results button if in test mode
            if (testModeEnabled) {
                verifyResultsButton.setDisable(false);
            }

        } catch (SQLException e) {
            showError("Integration Error", e.getMessage());
            logTextArea.appendText("Integration failed: " + e.getMessage() + "\n");
        }
    }

    private void verifyIntegrationResults() {
        try {
            if (destDbManager == null) {
                logTextArea.appendText("No destination database connection available\n");
                return;
            }

            try (Connection conn = destDbManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, description FROM " + DEST_TABLE_NAME)) {

                logTextArea.appendText("Integration Results (" + DEST_TABLE_NAME + " table):\n");
                logTextArea.appendText("------------------------------------------\n");
                while (rs.next()) {
                    logTextArea.appendText(rs.getInt("id") + ": " +
                            rs.getString("name") + " - " +
                            rs.getString("description") + "\n");
                }
                logTextArea.appendText("------------------------------------------\n");
            }
        } catch (SQLException e) {
            logTextArea.appendText("Error verifying results: " + e.getMessage() + "\n");
        }
    }

    private void openH2Console() {
        try {
            // Start the H2 console
            org.h2.tools.Console.main("-web", "-browser");
            logTextArea.appendText("H2 Console opened. Use 'jdbc:h2:mem:sourcedb' or 'jdbc:h2:mem:destdb' as JDBC URL.\n");
            logTextArea.appendText("Username: sa, Password: leave blank\n");
        } catch (Exception e) {
            showError("H2 Console Error", "Failed to open H2 Console: " + e.getMessage());
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