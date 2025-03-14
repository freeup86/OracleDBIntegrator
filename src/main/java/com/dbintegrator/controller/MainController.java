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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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

public class MainController {
    @FXML private RadioButton sourceToDestRadio;
    @FXML private RadioButton destToSourceRadio;
    @FXML private ToggleGroup directionToggle;
    @FXML private Label sourceDbLabel;
    @FXML private Label destDbLabel;
    @FXML private Button connectSourceButton;
    @FXML private Button connectDestButton;
    @FXML private ComboBox<String> sourceTableComboBox;
    @FXML private ComboBox<String> destTableComboBox;
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

    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;
    private ObservableList<ColumnMapping> mappings = FXCollections.observableArrayList();
    private ConfigurationManager configManager;
    private Connection sourceConn;
    private Connection destConn;

    // Multi-project selection fields
    private List<Project> selectedSourceProjects = new ArrayList<>();
    private List<Project> selectedDestProjects = new ArrayList<>();

    // Test mode flag
    private boolean testModeEnabled = false;

    @FXML
    public void initialize() {
        // Set up direction toggle
        directionToggle = new ToggleGroup();
        sourceToDestRadio.setToggleGroup(directionToggle);
        destToSourceRadio.setToggleGroup(directionToggle);
        sourceToDestRadio.setSelected(true);

        // Set up database connection buttons
        connectSourceButton.setOnAction(event -> connectToDatabase(true));
        connectDestButton.setOnAction(event -> connectToDatabase(false));

        // Set up table selection combo boxes
        sourceTableComboBox.setOnAction(event -> loadTableColumns(true));
        destTableComboBox.setOnAction(event -> loadTableColumns(false));

        // Set up project selection buttons
        selectSourceProjectsButton.setOnAction(event -> selectProjects(true));
        selectDestProjectsButton.setOnAction(event -> selectProjects(false));
        selectSourceProjectsButton.setDisable(true);
        selectDestProjectsButton.setDisable(true);

        // Set up mapping buttons
        addMappingButton.setOnAction(event -> addMapping());
        removeMappingButton.setOnAction(event -> removeMapping());

        // Set up execute button
        executeButton.setOnAction(event -> executeIntegration());
        executeButton.setDisable(true);

        // Set up test mode buttons
        testModeButton.setOnAction(event -> setupTestMode());
        openH2ConsoleButton.setOnAction(event -> openH2Console());
        openH2ConsoleButton.setDisable(true);

        // Set up verify results button
        verifyResultsButton = new Button("Verify Results");
        verifyResultsButton.setOnAction(event -> verifyIntegrationResults());
        verifyResultsButton.setDisable(true);

        // Set up mappings list view
        mappingsListView.setItems(mappings);

        // Disable buttons until connections are established
        sourceTableComboBox.setDisable(true);
        destTableComboBox.setDisable(true);
        addMappingButton.setDisable(true);
        removeMappingButton.setDisable(true);

        // Initialize configuration manager
        configManager = new ConfigurationManager();

        // Try to connect with saved credentials
        tryAutoConnect();
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

    private void setupTestMode() {
        try {
            // Test if H2 is working properly
            boolean h2Working = TestDatabaseManager.testBasicH2Connection();
            logTextArea.appendText("H2 test connection: " + (h2Working ? "SUCCESS" : "FAILED") + "\n");

            if (!h2Working) {
                logTextArea.appendText("H2 database not working properly. Cannot enable test mode.\n");
                return;
            }

            // Set up test connections
            logTextArea.appendText("Setting up test connections...\n");
            sourceDbManager = TestDatabaseManager.getSourceTestConnection();
            destDbManager = TestDatabaseManager.getDestTestConnection();

            // Update UI
            sourceDbLabel.setText("Source: Test Database Connection");
            destDbLabel.setText("Destination: Test Database Connection");

            // Initialize services
            sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
            destMetadataService = new DatabaseMetadataService(destDbManager);

            // Load tables
            loadTables(true);
            loadTables(false);

            // Enable controls
            sourceTableComboBox.setDisable(false);
            destTableComboBox.setDisable(false);
            selectSourceProjectsButton.setDisable(false);
            selectDestProjectsButton.setDisable(false);
            openH2ConsoleButton.setDisable(false);

            // Set test mode flag
            testModeEnabled = true;

            logTextArea.appendText("Test mode enabled with sample databases\n");

        } catch (Exception e) {
            // Full error reporting
            logTextArea.appendText("ERROR setting up test mode: " + e.getMessage() + "\n");

            // Get detailed stack trace for diagnosis
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logTextArea.appendText("Stack trace:\n" + sw.toString());

            showError("Test Setup Error", "Failed to set up test databases: " + e.getMessage());
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
                 ResultSet rs = stmt.executeQuery("SELECT id, name, description FROM PA_PROJECTS")) {

                logTextArea.appendText("Integration Results (PA_PROJECTS table):\n");
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

    private void tryAutoConnect() {
        // Try source connection
        if (configManager.hasSourceConnection()) {
            try {
                sourceDbManager = configManager.getSourceConnection();
                if (sourceDbManager != null) {
                    sourceDbLabel.setText("Source: " + sourceDbManager.getConnectionInfo());
                    sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
                    loadTables(true);
                    logTextArea.appendText("Connected to source database using saved credentials\n");
                    selectSourceProjectsButton.setDisable(false);
                }
            } catch (SQLException e) {
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
                    loadTables(false);
                    logTextArea.appendText("Connected to destination database using saved credentials\n");
                    selectDestProjectsButton.setDisable(false);
                }
            } catch (SQLException e) {
                logTextArea.appendText("Failed to connect to destination with saved credentials: " + e.getMessage() + "\n");
            }
        }

        // Enable table selections if both connections established
        if (sourceDbManager != null && destDbManager != null) {
            sourceTableComboBox.setDisable(false);
            destTableComboBox.setDisable(false);
        }
    }

    private void connectToDatabase(boolean isSource) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dbintegrator/ui/database_connection.fxml"));
            VBox root = loader.load();
            DatabaseConnectionController controller = loader.getController();

            // Set source/destination flag and load saved connection if available
            controller.setIsSource(isSource);
            controller.loadSavedConnection();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Connect to " + (isSource ? "Source" : "Destination") + " Database");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            if (controller.getConnectionManager() != null) {
                if (isSource) {
                    sourceDbManager = controller.getConnectionManager();
                    sourceDbLabel.setText("Source: " + sourceDbManager.getConnectionInfo());
                    sourceMetadataService = new DatabaseMetadataService(sourceDbManager);
                    loadTables(true);
                    selectSourceProjectsButton.setDisable(false);
                } else {
                    destDbManager = controller.getConnectionManager();
                    destDbLabel.setText("Destination: " + destDbManager.getConnectionInfo());
                    destMetadataService = new DatabaseMetadataService(destDbManager);
                    loadTables(false);
                    selectDestProjectsButton.setDisable(false);
                }

                // Enable table selections when both connections are established
                if (sourceDbManager != null && destDbManager != null) {
                    sourceTableComboBox.setDisable(false);
                    destTableComboBox.setDisable(false);
                }
            }
        } catch (IOException | SQLException e) {
            showError("Connection Error", e.getMessage());
        }
    }

    private void selectProjects(boolean isSource) {
        DatabaseConnectionManager dbManager = isSource ? sourceDbManager : destDbManager;
        if (dbManager == null) {
            showError("Connection Required",
                    "Please connect to " + (isSource ? "source" : "destination") + " database first.");
            return;
        }

        String tableName = isSource ? "PROJECTS" : "PA_PROJECTS";
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

        if (projects.isEmpty()) {
            label.setText("No " + (isSource ? "source" : "destination") + " projects selected");
        } else if (projects.size() == 1) {
            Project p = projects.get(0);
            label.setText((isSource ? "Source" : "Destination") + " Project: " +
                    p.getName() + " (ID: " + p.getId() + ")");
        } else {
            label.setText((isSource ? "Source" : "Destination") +
                    " Projects: " + projects.size() + " selected");
        }
    }

    private void loadTables(boolean isSource) throws SQLException {
        DatabaseMetadataService metadataService = isSource ? sourceMetadataService : destMetadataService;
        ComboBox<String> comboBox = isSource ? sourceTableComboBox : destTableComboBox;

        List<String> tableNames = metadataService.getTableNames();
        comboBox.setItems(FXCollections.observableArrayList(tableNames));

        if (!tableNames.isEmpty()) {
            comboBox.setValue(tableNames.get(0));
            loadTableColumns(isSource);
        }
    }

    private void loadTableColumns(boolean isSource) {
        try {
            String selectedTable = isSource ?
                    sourceTableComboBox.getValue() : destTableComboBox.getValue();

            if (selectedTable != null) {
                DatabaseMetadataService metadataService =
                        isSource ? sourceMetadataService : destMetadataService;
                ListView<TableColumn> listView =
                        isSource ? sourceColumnsListView : destColumnsListView;

                List<TableColumn> columns = metadataService.getTableColumns(selectedTable);
                listView.setItems(FXCollections.observableArrayList(columns));

                // Enable add mapping when both tables selected
                if (sourceTableComboBox.getValue() != null && destTableComboBox.getValue() != null) {
                    addMappingButton.setDisable(false);
                }
            }
        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private void addMapping() {
        TableColumn sourceColumn = sourceColumnsListView.getSelectionModel().getSelectedItem();
        TableColumn destColumn = destColumnsListView.getSelectionModel().getSelectedItem();

        if (sourceColumn != null && destColumn != null) {
            String sourceTable = sourceTableComboBox.getValue();
            String destTable = destTableComboBox.getValue();

            ColumnMapping mapping = new ColumnMapping(sourceTable, sourceColumn, destTable, destColumn);
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}