package com.dbintegrator.controller;

import com.dbintegrator.util.ConfigurationManager;
import com.dbintegrator.util.DatabaseConnectionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnectionController {
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField sidField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button testConnectionButton;
    @FXML private Button connectButton;
    @FXML private Label statusLabel;
    @FXML private CheckBox saveCredentialsCheckbox;

    private DatabaseConnectionManager connectionManager;
    private boolean isConnected = false;
    private boolean isSource;
    private ConfigurationManager configManager;

    public void setIsSource(boolean isSource) {
        this.isSource = isSource;
    }

    @FXML
    public void initialize() {
        portField.setText("1521"); // Default Oracle port

        testConnectionButton.setOnAction(event -> testConnection());
        connectButton.setOnAction(event -> connect());

        saveCredentialsCheckbox = new CheckBox("Save connection details");
        saveCredentialsCheckbox.setSelected(true);

        configManager = new ConfigurationManager();
    }

    public void loadSavedConnection() {
        // Load saved connection details if they exist
        DatabaseConnectionManager savedConnection = isSource ?
                configManager.getSourceConnection() :
                configManager.getDestConnection();

        if (savedConnection != null) {
            hostField.setText(savedConnection.getHost());
            portField.setText(String.valueOf(savedConnection.getPort()));
            sidField.setText(savedConnection.getSid());
            usernameField.setText(savedConnection.getUsername());
            // Password is already stored in the savedConnection object
            passwordField.setText("********"); // Placeholder for UI
            connectionManager = savedConnection;

            statusLabel.setText("Loaded saved connection");
            statusLabel.setStyle("-fx-text-fill: blue;");
        }
    }

    private void testConnection() {
        try {
            createConnectionManager();
            Connection connection = connectionManager.getConnection();
            if (connection != null && !connection.isClosed()) {
                statusLabel.setText("Connection successful!");
                statusLabel.setStyle("-fx-text-fill: green;");
                isConnected = true;
            }
        } catch (SQLException e) {
            statusLabel.setText("Connection failed: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            isConnected = false;
        }
    }

    private void connect() {
        if (!isConnected) {
            testConnection();
        }

        if (isConnected) {
            // Save credentials if checkbox is selected
            if (saveCredentialsCheckbox.isSelected()) {
                if (isSource) {
                    configManager.saveSourceConnection(
                            hostField.getText().trim(),
                            Integer.parseInt(portField.getText().trim()),
                            sidField.getText().trim(),
                            usernameField.getText().trim(),
                            passwordField.getText()
                    );
                } else {
                    configManager.saveDestConnection(
                            hostField.getText().trim(),
                            Integer.parseInt(portField.getText().trim()),
                            sidField.getText().trim(),
                            usernameField.getText().trim(),
                            passwordField.getText()
                    );
                }
            }

            Stage stage = (Stage) connectButton.getScene().getWindow();
            stage.close();
        }
    }

    private void createConnectionManager() {
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String sid = sidField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        connectionManager = new DatabaseConnectionManager(host, port, sid, username, password);
    }

    public DatabaseConnectionManager getConnectionManager() {
        return connectionManager;
    }
}