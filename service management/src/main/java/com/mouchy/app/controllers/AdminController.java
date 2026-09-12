package com.mouchy.app.controllers;

import com.mouchy.app.database.DatabaseManager;
import com.mouchy.app.models.*;
import com.mouchy.app.util.ValidationUtil;
import com.mouchy.app.util.WindowUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Controller for managing the Administrator interface.
 */
public class AdminController {

    // Current Admin user session
    private AdminUser sessionUser;

    // Sidebar navigation buttons
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavServices;
    @FXML private Button btnNavRequests;
    @FXML private Button btnNavUsers;

    // View Panes
    @FXML private VBox paneDashboard;
    @FXML private VBox paneServices;
    @FXML private VBox paneRequests;
    @FXML private VBox paneUsers;

    // --- Dashboard Elements ---
    @FXML private Label lblTotalRevenue;
    @FXML private Label lblActiveBookings;
    @FXML private Label lblTotalServices;
    @FXML private PieChart chartStatusDistribution;
    @FXML private Label lblAdminCount;
    @FXML private Label lblClientCount;
    @FXML private Label lblCompletedCount;
    @FXML private Label lblPendingCount;

    // --- Services Catalog Elements ---
    @FXML private TableView<Service> tableServices;
    @FXML private TableColumn<Service, Integer> colServiceId;
    @FXML private TableColumn<Service, String> colServiceName;
    @FXML private TableColumn<Service, String> colServiceCategory;
    @FXML private TableColumn<Service, Double> colServicePrice;
    @FXML private TableColumn<Service, String> colServiceDesc;

    @FXML private TextField txtServiceName;
    @FXML private TextField txtServiceCategory;
    @FXML private TextField txtServicePrice;
    @FXML private TextArea txtServiceDesc;
    @FXML private Button btnSaveService;
    @FXML private Button btnDeleteService;

    // --- Client Requests Elements ---
    @FXML private TableView<ServiceRequest> tableRequests;
    @FXML private TableColumn<ServiceRequest, Integer> colReqId;
    @FXML private TableColumn<ServiceRequest, String> colReqClient;
    @FXML private TableColumn<ServiceRequest, String> colReqService;
    @FXML private TableColumn<ServiceRequest, String> colReqTitle;
    @FXML private TableColumn<ServiceRequest, Double> colReqPrice;
    @FXML private TableColumn<ServiceRequest, String> colReqDate;
    @FXML private TableColumn<ServiceRequest, String> colReqStatus;

    @FXML private Label lblReqProjectTitle;
    @FXML private Label lblReqDesc;
    @FXML private ComboBox<String> comboReqStatus;
    @FXML private TextArea txtReqComments;

    // --- User Accounts Elements ---
    @FXML private TableView<User> tableUsers;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String> colUserUsername;
    @FXML private TableColumn<User, String> colUserRole;
    @FXML private TableColumn<User, String> colUserFullName;
    @FXML private TableColumn<User, String> colUserEmail;
    @FXML private TableColumn<User, String> colUserPhone;

    @FXML private TextField txtUserUsername;
    @FXML private PasswordField txtUserPassword;
    @FXML private ComboBox<String> comboUserRole;
    @FXML private TextField txtUserFullName;
    @FXML private TextField txtUserEmail;
    @FXML private TextField txtUserPhone;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFormBindings();

        // Hidden StackPane pages should not participate in layout calculations.
        paneDashboard.managedProperty().bind(paneDashboard.visibleProperty());
        paneServices.managedProperty().bind(paneServices.visibleProperty());
        paneRequests.managedProperty().bind(paneRequests.visibleProperty());
        paneUsers.managedProperty().bind(paneUsers.visibleProperty());

        // Phone numbers are numeric-only in both public registration and admin account creation.
        txtUserPhone.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));

        // Populate static choices
        comboReqStatus.setItems(FXCollections.observableArrayList("PENDING", "IN_PROGRESS", "COMPLETED", "REJECTED"));
        comboUserRole.setItems(FXCollections.observableArrayList("ADMIN", "CLIENT"));
    }

    /**
     * Set up session user and load initial data.
     */
    public void initializeSession(AdminUser admin) {
        this.sessionUser = admin;
        loadDashboardStats();
    }

    private void setupTableColumns() {
        // Services Table
        colServiceId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colServiceName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colServiceCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colServicePrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colServiceDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Format price column
        colServicePrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", price));
                }
            }
        });

        // Requests Table
        colReqId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colReqClient.setCellValueFactory(new PropertyValueFactory<>("clientName"));
        colReqService.setCellValueFactory(new PropertyValueFactory<>("serviceName"));
        colReqTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colReqPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colReqDate.setCellValueFactory(new PropertyValueFactory<>("preferredDate"));
        colReqStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colReqPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", price));
                }
            }
        });

        // Users Table
        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUserUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUserRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colUserFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colUserEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colUserPhone.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
    }

    private void setupFormBindings() {
        // Service table selection listener
        tableServices.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                txtServiceName.setText(newSelection.getName());
                txtServiceCategory.setText(newSelection.getCategory());
                txtServicePrice.setText(String.valueOf(newSelection.getPrice()));
                txtServiceDesc.setText(newSelection.getDescription());
                btnSaveService.setText("Update Service");
                btnDeleteService.setDisable(false);
            } else {
                clearServiceForm();
            }
        });

        // Request table selection listener
        tableRequests.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                lblReqProjectTitle.setText(newSelection.getTitle());
                lblReqDesc.setText(newSelection.getDescription());
                comboReqStatus.setValue(newSelection.getStatus());
                txtReqComments.setText(newSelection.getComments());
            } else {
                lblReqProjectTitle.setText("Select a request...");
                lblReqDesc.setText("No request selected.");
                comboReqStatus.setValue(null);
                txtReqComments.clear();
            }
        });
        
        btnDeleteService.setDisable(true);
    }

    // --- Navigation Handlers ---

    private void resetNavButtons() {
        btnNavDashboard.getStyleClass().remove("nav-button-active");
        btnNavServices.getStyleClass().remove("nav-button-active");
        btnNavRequests.getStyleClass().remove("nav-button-active");
        btnNavUsers.getStyleClass().remove("nav-button-active");

        paneDashboard.setVisible(false);
        paneServices.setVisible(false);
        paneRequests.setVisible(false);
        paneUsers.setVisible(false);
    }

    @FXML
    void showDashboard(ActionEvent event) {
        resetNavButtons();
        btnNavDashboard.getStyleClass().add("nav-button-active");
        paneDashboard.setVisible(true);
        loadDashboardStats();
    }

    @FXML
    void showServices(ActionEvent event) {
        resetNavButtons();
        btnNavServices.getStyleClass().add("nav-button-active");
        paneServices.setVisible(true);
        loadServicesTable();
    }

    @FXML
    void showRequests(ActionEvent event) {
        resetNavButtons();
        btnNavRequests.getStyleClass().add("nav-button-active");
        paneRequests.setVisible(true);
        loadRequestsTable();
    }

    @FXML
    void showUsers(ActionEvent event) {
        resetNavButtons();
        btnNavUsers.getStyleClass().add("nav-button-active");
        paneUsers.setVisible(true);
        loadUsersTable();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Stage stage = (Stage) btnNavDashboard.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mouchy/app/views/login.fxml"));
            Parent root = loader.load();
            WindowUtil.configure(stage, new Scene(root), 950, 700);
            stage.setTitle("Mouchy Creative Agency - Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Dashboard logic ---

    private void loadDashboardStats() {
        List<ServiceRequest> requests = DatabaseManager.getAllRequests();
        List<Service> services = DatabaseManager.getAllServices();
        List<User> users = DatabaseManager.getAllUsers();

        lblTotalServices.setText(String.valueOf(services.size()));

        double totalRevenue = 0.0;
        int activeBookings = 0;
        int completedJobs = 0;
        int pendingJobs = 0;
        int progressJobs = 0;
        int rejectedJobs = 0;

        for (ServiceRequest req : requests) {
            if ("COMPLETED".equalsIgnoreCase(req.getStatus())) {
                totalRevenue += req.getPrice();
                completedJobs++;
            } else if ("PENDING".equalsIgnoreCase(req.getStatus())) {
                activeBookings++;
                pendingJobs++;
            } else if ("IN_PROGRESS".equalsIgnoreCase(req.getStatus())) {
                activeBookings++;
                progressJobs++;
            } else if ("REJECTED".equalsIgnoreCase(req.getStatus())) {
                rejectedJobs++;
            }
        }

        lblTotalRevenue.setText(String.format("$%.2f", totalRevenue));
        lblActiveBookings.setText(String.valueOf(activeBookings));

        // Quick Stats
        lblCompletedCount.setText(String.valueOf(completedJobs));
        lblPendingCount.setText(String.valueOf(pendingJobs));

        int adminCount = 0;
        int clientCount = 0;
        for (User u : users) {
            if ("ADMIN".equalsIgnoreCase(u.getRole())) {
                adminCount++;
            } else {
                clientCount++;
            }
        }
        lblAdminCount.setText(String.valueOf(adminCount));
        lblClientCount.setText(String.valueOf(clientCount));

        // Pie Chart Binding
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("Pending (" + pendingJobs + ")", pendingJobs),
                new PieChart.Data("In Progress (" + progressJobs + ")", progressJobs),
                new PieChart.Data("Completed (" + completedJobs + ")", completedJobs),
                new PieChart.Data("Rejected (" + rejectedJobs + ")", rejectedJobs)
        );
        chartStatusDistribution.setData(pieData);
    }

    // --- Services Logic ---

    private void loadServicesTable() {
        List<Service> servicesList = DatabaseManager.getAllServices();
        tableServices.setItems(FXCollections.observableArrayList(servicesList));
    }

    @FXML
    void handleSaveService(ActionEvent event) {
        String name = txtServiceName.getText().trim();
        String category = txtServiceCategory.getText().trim();
        String priceStr = txtServicePrice.getText().trim();
        String desc = txtServiceDesc.getText().trim();

        if (name.isEmpty() || category.isEmpty() || priceStr.isEmpty() || desc.isEmpty()) {
            showAlert("Input Error", "All fields are required.", Alert.AlertType.ERROR);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (!Double.isFinite(price) || price < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Please enter a valid non-negative price.", Alert.AlertType.ERROR);
            return;
        }

        Service selected = tableServices.getSelectionModel().getSelectedItem();
        boolean success;

        if (selected == null) {
            // Create new
            success = DatabaseManager.addService(name, desc, price, category);
        } else {
            // Update
            success = DatabaseManager.updateService(selected.getId(), name, desc, price, category);
        }

        if (success) {
            loadServicesTable();
            clearServiceForm();
            showAlert("Success", "Service saved successfully.", Alert.AlertType.INFORMATION);
        } else {
            showAlert("Database Error", "Failed to save the service.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleDeleteService(ActionEvent event) {
        Service selected = tableServices.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Remove this service from the active catalog? Existing client requests will remain, but clients will no longer be able to book it.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Service Removal");
        confirm.setHeaderText(null);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            boolean success = DatabaseManager.archiveService(selected.getId());
            if (success) {
                loadServicesTable();
                clearServiceForm();
                showAlert("Success", "Service removed from the active catalog.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Could not remove service from the catalog.", Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    void clearServiceForm() {
        tableServices.getSelectionModel().clearSelection();
        txtServiceName.clear();
        txtServiceCategory.clear();
        txtServicePrice.clear();
        txtServiceDesc.clear();
        btnSaveService.setText("Save Service");
        btnDeleteService.setDisable(true);
    }

    // --- Client Requests Logic ---

    private void loadRequestsTable() {
        List<ServiceRequest> requests = DatabaseManager.getAllRequests();
        tableRequests.setItems(FXCollections.observableArrayList(requests));
    }

    @FXML
    void handleSaveRequest(ActionEvent event) {
        ServiceRequest selected = tableRequests.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Selection Error", "Please select a booking request to update.", Alert.AlertType.WARNING);
            return;
        }

        String newStatus = comboReqStatus.getValue();
        String comments = txtReqComments.getText().trim();

        if (newStatus == null) {
            showAlert("Input Error", "Please assign a status.", Alert.AlertType.ERROR);
            return;
        }

        boolean success = DatabaseManager.updateRequestStatus(selected.getId(), newStatus, comments);
        if (success) {
            loadRequestsTable();
            showAlert("Success", "Booking status updated successfully.", Alert.AlertType.INFORMATION);
        } else {
            showAlert("Database Error", "Failed to update booking.", Alert.AlertType.ERROR);
        }
    }

    // --- User Accounts Directory Logic ---

    private void loadUsersTable() {
        List<User> list = DatabaseManager.getAllUsers();
        tableUsers.setItems(FXCollections.observableArrayList(list));
    }

    @FXML
    void handleCreateUser(ActionEvent event) {
        String username = txtUserUsername.getText().trim();
        String password = txtUserPassword.getText().trim();
        String role = comboUserRole.getValue();
        String fullName = txtUserFullName.getText().trim();
        String email = txtUserEmail.getText().trim();
        String phone = txtUserPhone.getText().trim();

        if (username.isEmpty() || password.isEmpty() || role == null || fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert("Input Error", "All fields are required to register an account.", Alert.AlertType.ERROR);
            return;
        }

        if (!ValidationUtil.isValidEmail(email)) {
            showAlert("Input Error", "Please enter a valid email address.", Alert.AlertType.ERROR);
            return;
        }

        if (!ValidationUtil.isValidPhoneNumber(phone)) {
            showAlert("Input Error", "Phone number must contain digits only.", Alert.AlertType.ERROR);
            return;
        }

        if (DatabaseManager.usernameExists(username)) {
            showAlert("Registration Error", "Username is already taken.", Alert.AlertType.ERROR);
            return;
        }

        boolean success = DatabaseManager.createUser(username, password, role, fullName, email, phone);
        if (success) {
            loadUsersTable();
            clearUserForm();
            showAlert("Success", "Account created successfully.", Alert.AlertType.INFORMATION);
        } else {
            showAlert("Database Error", "The account could not be created. Please try again.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleDeleteUser(ActionEvent event) {
        User selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Selection Error", "Please select a user to delete.", Alert.AlertType.WARNING);
            return;
        }

        if (selected.getUsername().equals(sessionUser.getUsername())) {
            showAlert("Error", "You cannot delete your own logged-in admin account.", Alert.AlertType.ERROR);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Are you sure you want to delete user '" + selected.getUsername() + "'? This will permanently delete their account and booking tickets.", 
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Account Deletion");
        confirm.setHeaderText(null);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            boolean success = DatabaseManager.deleteUser(selected.getId());
            if (success) {
                loadUsersTable();
                clearUserForm();
                showAlert("Success", "User account deleted.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Failed to delete user.", Alert.AlertType.ERROR);
            }
        }
    }

    private void clearUserForm() {
        txtUserUsername.clear();
        txtUserPassword.clear();
        comboUserRole.setValue(null);
        txtUserFullName.clear();
        txtUserEmail.clear();
        txtUserPhone.clear();
        tableUsers.getSelectionModel().clearSelection();
    }

    // --- Helpers ---

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
