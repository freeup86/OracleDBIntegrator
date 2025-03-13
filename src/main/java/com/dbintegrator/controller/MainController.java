package com.dbintegrator.controller;

import com.dbintegrator.model.ColumnMapping;
import com.dbintegrator.model.TableColumn;
import com.dbintegrator.service.DatabaseMetadataService;
import com.dbintegrator.service.DataIntegrationService;
import com.dbintegrator.util.DatabaseConnectionManager;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;
    private DatabaseMetadataService sourceMetadataService;
    private DatabaseMetadataService destMetadataService;
    private ObservableList<ColumnMapping> mappings = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Set up direction toggle
        directionToggle = new ToggleGroup();
        sourceToDestRadio.setToggleGroup(directionToggle);
        destToSourceRadio.setToggleGroup(directionToggle);
        sourceToDestRadio.setSelected(true);

        // Set up buttons
        connectSourceButton.setOnAction(event -> connectToDatabase(true));
        connectDestButton.setOnAction(event -> connectToDatabase(false));

        // Set up table selection combo boxes
        sourceTableComboBox.setOnAction(event -> loadTableColumns(true));
        destTableComboBox.setOnAction(event -> loadTableColumns(false));

        // Set up mapping buttons
        addMappingButton.setOnAction(event -> addMapping());
        removeMappingButton.setOnAction(event -> removeMapping());

        // Set up execute button
        executeButton.setOnAction(event -> executeIntegration());
        executeButton.setDisable(true);

        // Set up mappings list view
        mappingsListView.setItems(mappings);

        // Disable buttons until connections are established
        sourceTableComboBox.setDisable(true);
        destTableComboBox.setDisable(true);
        addMappingButton.setDisable(true);
        removeMappingButton.setDisable(true);
    }

    private void connectToDatabase(boolean isSource) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dbintegrator/ui/database_connection.fxml"));
            VBox root = loader.load();
            DatabaseConnectionController controller = loader.getController();

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
                } else {
                    destDbManager = controller.getConnectionManager();
                    destDbLabel.setText("Destination: " + destDbManager.getConnectionInfo());
                    destMetadataService = new DatabaseMetadataService(destDbManager);
                    loadTables(false);
                }

                // Enable table selections when both connections are established
                if (sourceDbManager != null && destDbManager != null) {
                    sourceTableComboBox.setDisable(false);
                    destTableComboBox.setDisable(false);
                    executeButton.setDisable(false);
                }
            }
        } catch (IOException | SQLException e) {
            showError("Connection Error", e.getMessage());
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
            executeButton.setDisable(false);
            removeMappingButton.setDisable(false);
        } else {
            showError("Mapping Error", "Please select columns from both source and destination tables.");
        }
    }

    private void removeMapping() {
        ColumnMapping selectedMapping = mappingsListView.getSelectionModel().getSelectedItem();
        if (selectedMapping != null) {
            mappings.remove(selectedMapping);

            if (mappings.isEmpty()) {
                removeMappingButton.setDisable(true);
            }
        }
    }

    private void executeIntegration() {
        if (mappings.isEmpty()) {
            showError("Integration Error", "No column mappings defined.");
            return;
        }

        try {
            // Determine integration direction
            boolean sourceToDestDirection = sourceToDestRadio.isSelected();
            DatabaseConnectionManager srcManager = sourceToDestDirection ? sourceDbManager : destDbManager;
            DatabaseConnectionManager dstManager = sourceToDestDirection ? destDbManager : sourceDbManager;

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

            DataIntegrationService integrationService = new DataIntegrationService(srcManager, dstManager);
            int rowsUpdated = integrationService.integrateData(directedMappings);

            logTextArea.appendText("Integration completed. " + rowsUpdated + " rows updated.\n");
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