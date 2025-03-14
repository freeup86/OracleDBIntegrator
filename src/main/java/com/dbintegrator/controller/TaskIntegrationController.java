package com.dbintegrator.controller;

import com.dbintegrator.model.Project;
import com.dbintegrator.model.Task;
import com.dbintegrator.model.TaskMapping;
import com.dbintegrator.util.DatabaseConnectionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskIntegrationController {
    private static final Logger LOGGER = Logger.getLogger(TaskIntegrationController.class.getName());

    @FXML private ComboBox<Project> sourceProjectComboBox;
    @FXML private ComboBox<Project> destProjectComboBox;
    @FXML private ListView<Task> sourceTasksListView;
    @FXML private ListView<Task> destTasksListView;
    @FXML private ListView<TaskMapping> taskMappingsListView;
    @FXML private Button addTaskMappingButton;
    @FXML private Button removeTaskMappingButton;
    @FXML private Button executeTaskIntegrationButton;
    @FXML private TextArea logTextArea;

    private DatabaseConnectionManager sourceDbManager;
    private DatabaseConnectionManager destDbManager;

    private ObservableList<Task> sourceTasksList = FXCollections.observableArrayList();
    private ObservableList<Task> destTasksList = FXCollections.observableArrayList();
    private ObservableList<TaskMapping> taskMappings = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("TaskIntegrationController - INITIALIZE METHOD CALLED");
        System.out.println("Source Project ComboBox: " + (sourceProjectComboBox != null));
        System.out.println("Destination Project ComboBox: " + (destProjectComboBox != null));

        // Force ComboBox configuration
        Platform.runLater(() -> {
            if (sourceProjectComboBox != null) {
                sourceProjectComboBox.setVisibleRowCount(10);
                sourceProjectComboBox.setPromptText("Select P6 Project");
            }

            if (destProjectComboBox != null) {
                destProjectComboBox.setVisibleRowCount(10);
                destProjectComboBox.setPromptText("Select EBS Project");
            }
        });

        // Initialize UI components
        initializeUIComponents();
    }

    private void initializeUIComponents() {
        // Null-safe initialization with extensive logging
        if (sourceTasksListView != null) {
            sourceTasksListView.setItems(sourceTasksList);
        } else {
            System.err.println("sourceTasksListView is NULL");
        }

        if (destTasksListView != null) {
            destTasksListView.setItems(destTasksList);
        } else {
            System.err.println("destTasksListView is NULL");
        }

        if (taskMappingsListView != null) {
            taskMappingsListView.setItems(taskMappings);
        } else {
            System.err.println("taskMappingsListView is NULL");
        }

        // Configure ComboBox cell factories
        configureComboBoxCellFactories();

        // Setup project combo box listeners
        setupProjectComboBoxListeners();
    }

    private void configureComboBoxCellFactories() {
        if (sourceProjectComboBox != null) {
            sourceProjectComboBox.setCellFactory(param -> new ListCell<Project>() {
                @Override
                protected void updateItem(Project item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(item == null ? "" : item.getName());
                }
            });
            sourceProjectComboBox.setVisibleRowCount(5);
        }

        if (destProjectComboBox != null) {
            destProjectComboBox.setCellFactory(param -> new ListCell<Project>() {
                @Override
                protected void updateItem(Project item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(item == null ? "" : item.getName());
                }
            });
            destProjectComboBox.setVisibleRowCount(5);
        }
    }

    private void setupProjectComboBoxListeners() {
        if (sourceProjectComboBox != null) {
            sourceProjectComboBox.setOnAction(event -> {
                Project selectedProject = sourceProjectComboBox.getValue();
                System.out.println("Source Project Selected: " + (selectedProject != null ? selectedProject.getName() : "NULL"));
                if (selectedProject != null) {
                    loadSourceTasks(selectedProject);
                }
            });
        } else {
            System.err.println("sourceProjectComboBox is NULL - cannot set listener");
        }

        if (destProjectComboBox != null) {
            destProjectComboBox.setOnAction(event -> {
                Project selectedProject = destProjectComboBox.getValue();
                System.out.println("Destination Project Selected: " + (selectedProject != null ? selectedProject.getName() : "NULL"));
                if (selectedProject != null) {
                    loadDestinationTasks(selectedProject);
                }
            });
        } else {
            System.err.println("destProjectComboBox is NULL - cannot set listener");
        }
    }

    public void setSourceDbManager(DatabaseConnectionManager sourceDbManager) {
        System.out.println("setSourceDbManager CALLED");
        System.out.println("Source DB Manager: " + (sourceDbManager != null));
        this.sourceDbManager = sourceDbManager;

        // Force project loading on JavaFX Application Thread with additional logging
        Platform.runLater(() -> {
            System.out.println("Platform.runLater - Loading Source Projects");
            loadSourceProjects();
        });
    }

    public void setDestDbManager(DatabaseConnectionManager destDbManager) {
        System.out.println("setDestDbManager CALLED");
        System.out.println("Destination DB Manager: " + (destDbManager != null));
        this.destDbManager = destDbManager;

        // Force project loading on JavaFX Application Thread with additional logging
        Platform.runLater(() -> {
            System.out.println("Platform.runLater - Loading Destination Projects");
            loadDestinationProjects();
        });
    }

    private void loadSourceProjects() {
        System.out.println("loadSourceProjects CALLED");
        System.out.println("Source DB Manager: " + (sourceDbManager != null));
        System.out.println("Source Project ComboBox: " + (sourceProjectComboBox != null));

        if (sourceDbManager == null || sourceProjectComboBox == null) {
            System.err.println("Cannot load source projects - manager or combobox is null");
            return;
        }

        try {
            List<Project> sourceProjects = getProjects(sourceDbManager, "PROJECTS");

            System.out.println("Source Projects Found: " + sourceProjects.size());
            for (Project p : sourceProjects) {
                System.out.println("Source Project: " + p);
            }

            Platform.runLater(() -> {
                try {
                    System.out.println("Setting source project ComboBox items");

                    // Clear and repopulate
                    sourceProjectComboBox.getItems().clear();
                    sourceProjectComboBox.getItems().addAll(sourceProjects);

                    // Force UI updates
                    sourceProjectComboBox.requestLayout();
                    sourceProjectComboBox.layout();

                    // Explicitly show dropdown
                    sourceProjectComboBox.show();

                    // Set custom cell factory again
                    sourceProjectComboBox.setCellFactory(param -> new ListCell<Project>() {
                        @Override
                        protected void updateItem(Project item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(item == null ? "" : item.getName());
                        }
                    });

                    // Explicitly set converter
                    sourceProjectComboBox.setConverter(new StringConverter<Project>() {
                        @Override
                        public String toString(Project project) {
                            return project == null ? "" : project.getName();
                        }

                        @Override
                        public Project fromString(String string) {
                            return null; // Not needed for this use case
                        }
                    });

                    // Ensure first item is selected if available
                    if (!sourceProjects.isEmpty()) {
                        sourceProjectComboBox.setValue(sourceProjects.get(0));
                    }

                    // Log items in combobox
                    System.out.println("Source ComboBox Items: " + sourceProjectComboBox.getItems());
                    System.out.println("Source ComboBox Visible Items: " + sourceProjectComboBox.getVisibleRowCount());
                } catch (Exception e) {
                    System.err.println("Error in Platform.runLater for source projects:");
                    e.printStackTrace();
                }
            });
        } catch (SQLException e) {
            System.err.println("Error loading source projects:");
            e.printStackTrace();
        }
    }

// Do the same for loadDestinationProjects()

    private void loadDestinationProjects() {
        System.out.println("loadDestinationProjects CALLED");
        System.out.println("Destination DB Manager: " + (destDbManager != null));
        System.out.println("Destination Project ComboBox: " + (destProjectComboBox != null));

        if (destDbManager == null || destProjectComboBox == null) {
            System.err.println("Cannot load destination projects - manager or combobox is null");
            return;
        }

        try {
            List<Project> destProjects = getProjects(destDbManager, "PA_PROJECTS");

            System.out.println("Destination Projects Found: " + destProjects.size());
            for (Project p : destProjects) {
                System.out.println("Destination Project: " + p);
            }

            Platform.runLater(() -> {
                System.out.println("Setting destination project ComboBox items");
                destProjectComboBox.getItems().clear();
                destProjectComboBox.getItems().addAll(destProjects);

                // Force dropdown to show
                destProjectComboBox.show();

                // Explicitly set the first item if available
                if (!destProjects.isEmpty()) {
                    destProjectComboBox.setValue(destProjects.get(0));
                }

                // Log items in combobox
                System.out.println("Destination ComboBox Items: " + destProjectComboBox.getItems());
            });
        } catch (SQLException e) {
            System.err.println("Error loading destination projects:");
            e.printStackTrace();
        }
    }

    // Remaining methods stay the same as in previous implementations...
    // (getProjects, loadSourceTasks, loadDestinationTasks, etc.)

    private List<Project> getProjects(DatabaseConnectionManager dbManager,
                                      String tableName) throws SQLException {
        List<Project> projects = new ArrayList<>();

        String query = "SELECT id, name, description FROM " + tableName;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Project project = new Project(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description")
                );
                projects.add(project);
            }
        }

        return projects;
    }

    private void loadSourceTasks(Project project) {
        try {
            List<Task> tasks = getProjectTasks(sourceDbManager, project.getId(), "TASKS");
            Platform.runLater(() -> {
                sourceTasksList.setAll(tasks);
                checkMappingAvailability();
            });
        } catch (SQLException e) {
            showError("Source Tasks Error", "Failed to load source tasks: " + e.getMessage());
        }
    }

    private void loadDestinationTasks(Project project) {
        try {
            List<Task> tasks = getProjectTasks(destDbManager, project.getId(), "PA_TASKS");
            Platform.runLater(() -> {
                destTasksList.setAll(tasks);
                checkMappingAvailability();
            });
        } catch (SQLException e) {
            showError("Destination Tasks Error", "Failed to load destination tasks: " + e.getMessage());
        }
    }

    private List<Task> getProjectTasks(DatabaseConnectionManager dbManager,
                                       int projectId,
                                       String tableName) throws SQLException {
        List<Task> tasks = new ArrayList<>();

        String query = "SELECT id, name, description, status, assignee " +
                "FROM " + tableName + " WHERE project_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tasks.add(new Task(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            projectId,
                            rs.getString("status"),
                            rs.getString("assignee")
                    ));
                }
            }
        }

        return tasks;
    }

    private void checkMappingAvailability() {
        boolean canMap = sourceProjectComboBox.getValue() != null &&
                destProjectComboBox.getValue() != null &&
                !sourceTasksList.isEmpty() &&
                !destTasksList.isEmpty();

        Platform.runLater(() -> {
            addTaskMappingButton.setDisable(!canMap);
        });
    }

    private void setupMappingButtons() {
        addTaskMappingButton.setOnAction(event -> addTaskMapping());
        removeTaskMappingButton.setOnAction(event -> removeTaskMapping());
        executeTaskIntegrationButton.setOnAction(event -> executeTaskIntegration());
    }

    private void addTaskMapping() {
        Task sourceTask = sourceTasksListView.getSelectionModel().getSelectedItem();
        Task destTask = destTasksListView.getSelectionModel().getSelectedItem();

        if (sourceTask != null && destTask != null) {
            TaskMapping mapping = new TaskMapping(sourceTask, destTask);

            // Prevent duplicate mappings
            if (!taskMappings.contains(mapping)) {
                taskMappings.add(mapping);
                removeTaskMappingButton.setDisable(false);
                executeTaskIntegrationButton.setDisable(false);
            } else {
                showError("Mapping Error", "This task mapping already exists.");
            }
        } else {
            showError("Mapping Error", "Please select both source and destination tasks.");
        }
    }

    private void removeTaskMapping() {
        TaskMapping selectedMapping = taskMappingsListView.getSelectionModel().getSelectedItem();
        if (selectedMapping != null) {
            taskMappings.remove(selectedMapping);

            if (taskMappings.isEmpty()) {
                removeTaskMappingButton.setDisable(true);
                executeTaskIntegrationButton.setDisable(true);
            }
        }
    }

    private void executeTaskIntegration() {
        if (taskMappings.isEmpty()) {
            showError("Integration Error", "No task mappings defined.");
            return;
        }

        try {
            int totalTasksUpdated = 0;

            for (TaskMapping mapping : taskMappings) {
                int rowsUpdated = updateTask(mapping);
                totalTasksUpdated += rowsUpdated;
            }

            // Refresh tasks after integration
            Project sourceProject = sourceProjectComboBox.getValue();
            Project destProject = destProjectComboBox.getValue();

            if (sourceProject != null) loadSourceTasks(sourceProject);
            if (destProject != null) loadDestinationTasks(destProject);

        } catch (SQLException e) {
            showError("Integration Error", e.getMessage());
        }
    }

    private int updateTask(TaskMapping mapping) throws SQLException {
        String updateQuery = "UPDATE PA_TASKS SET " +
                "name = ?, description = ?, status = ?, assignee = ? " +
                "WHERE id = ?";

        try (Connection conn = destDbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {

            Task sourceTask = mapping.getSourceTask();
            Task destTask = mapping.getDestTask();

            stmt.setString(1, sourceTask.getName());
            stmt.setString(2, sourceTask.getDescription());
            stmt.setString(3, sourceTask.getStatus());
            stmt.setString(4, sourceTask.getAssignee());
            stmt.setInt(5, destTask.getId());

            return stmt.executeUpdate();
        }
    }

    public void forceProjectLoading() {
        if (sourceDbManager != null) {
            loadSourceProjects();
        }
        if (destDbManager != null) {
            loadDestinationProjects();
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