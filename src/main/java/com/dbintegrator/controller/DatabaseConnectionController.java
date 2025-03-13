package com.dbintegrator.controller;

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

    private DatabaseConnectionManager connectionManager;
    private boolean isConnected = false;

    @FXML
    public void initialize() {
        portField.setText("1521"); // Default Oracle port

        testConnectionButton.setOnAction(event -> testConnection());
        connectButton.setOnAction(event -> connect());
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