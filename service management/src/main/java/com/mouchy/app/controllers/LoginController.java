package com.mouchy.app.controllers;

import com.mouchy.app.database.DatabaseManager;
import com.mouchy.app.models.AdminUser;
import com.mouchy.app.models.ClientUser;
import com.mouchy.app.models.User;
import com.mouchy.app.util.ValidationUtil;
import com.mouchy.app.util.WindowUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller for handling login and registration logic.
 */
public class LoginController {

    @FXML private VBox loginFormPane;
    @FXML private VBox registerFormPane;

    // Login fields
    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginErrorLabel;
    @FXML private Button loginButton;

    // Register fields
    @FXML private TextField regFullName;
    @FXML private TextField regEmail;
    @FXML private TextField regPhone;
    @FXML private TextField regUsername;
    @FXML private PasswordField regPassword;
    @FXML private Label regErrorLabel;
    @FXML private Label regSuccessLabel;
    @FXML private Button registerButton;

    @FXML
    public void initialize() {
        // Clear errors
        clearErrors();

        // Phone numbers are stored as text, but only decimal digits may be entered.
        regPhone.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));
    }

    private void clearErrors() {
        loginErrorLabel.setVisible(false);
        loginErrorLabel.setManaged(false);
        regErrorLabel.setVisible(false);
        regErrorLabel.setManaged(false);
        regSuccessLabel.setVisible(false);
        regSuccessLabel.setManaged(false);
    }

    @FXML
    void showRegisterForm(ActionEvent event) {
        clearErrors();
        loginFormPane.setVisible(false);
        loginFormPane.setManaged(false);
        registerFormPane.setVisible(true);
        registerFormPane.setManaged(true);
    }

    @FXML
    void showLoginForm(ActionEvent event) {
        clearErrors();
        registerFormPane.setVisible(false);
        registerFormPane.setManaged(false);
        loginFormPane.setVisible(true);
        loginFormPane.setManaged(true);
    }

    @FXML
    void handleLogin(ActionEvent event) {
        clearErrors();
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showLoginError("Username and password are required.");
            return;
        }

        User user = DatabaseManager.authenticate(username, password);
        if (user == null) {
            showLoginError("Invalid username or password.");
            return;
        }

        // Direct user to appropriate interface
        try {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            if (user instanceof AdminUser) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mouchy/app/views/admin.fxml"));
                Parent root = loader.load();
                
                AdminController adminController = loader.getController();
                adminController.initializeSession((AdminUser) user);
                
                WindowUtil.configure(stage, new Scene(root), 1100, 760);
                stage.setTitle("Mouchy - Admin Panel Dashboard");
            } else if (user instanceof ClientUser) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mouchy/app/views/client.fxml"));
                Parent root = loader.load();
                
                ClientController clientController = loader.getController();
                clientController.initializeSession((ClientUser) user);
                
                WindowUtil.configure(stage, new Scene(root), 1100, 760);
                stage.setTitle("Mouchy - Client Booking Portal");
            }
            stage.centerOnScreen();
        } catch (IOException e) {
            showLoginError("System load error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleRegister(ActionEvent event) {
        clearErrors();
        String fullName = regFullName.getText().trim();
        String email = regEmail.getText().trim();
        String phone = regPhone.getText().trim();
        String username = regUsername.getText().trim();
        String password = regPassword.getText().trim();

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showRegisterError("All fields are required.");
            return;
        }

        if (!ValidationUtil.isValidEmail(email)) {
            showRegisterError("Please enter a valid email address.");
            return;
        }

        if (!ValidationUtil.isValidPhoneNumber(phone)) {
            showRegisterError("Phone number must contain digits only.");
            return;
        }

        if (DatabaseManager.usernameExists(username)) {
            showRegisterError("Username is already taken. Please try another.");
            return;
        }

        boolean success = DatabaseManager.registerClient(username, password, fullName, email, phone);
        if (success) {
            regSuccessLabel.setText("Account created! You can now log in.");
            regSuccessLabel.setVisible(true);
            regSuccessLabel.setManaged(true);

            // Clear registration inputs
            regFullName.clear();
            regEmail.clear();
            regPhone.clear();
            regUsername.clear();
            regPassword.clear();
        } else {
            showRegisterError("The account could not be created. Please try again.");
        }
    }

    private void showLoginError(String message) {
        loginErrorLabel.setText(message);
        loginErrorLabel.setVisible(true);
        loginErrorLabel.setManaged(true);
    }

    private void showRegisterError(String message) {
        regErrorLabel.setText(message);
        regErrorLabel.setVisible(true);
        regErrorLabel.setManaged(true);
    }
}
