package com.mouchy.app.controllers;

import com.mouchy.app.database.DatabaseManager;
import com.mouchy.app.models.*;
import com.mouchy.app.pdf.PDFGenerator;
import com.mouchy.app.util.ValidationUtil;
import com.mouchy.app.util.WindowUtil;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for managing the Client interface.
 */
public class ClientController {

    private ClientUser sessionUser;

    // Sidebar navigation buttons
    @FXML private Button btnNavServices;
    @FXML private Button btnNavNewRequest;
    @FXML private Button btnNavBookings;
    @FXML private Button btnNavProfile;

    // View Panes
    @FXML private VBox paneServices;
    @FXML private VBox paneNewRequest;
    @FXML private VBox paneBookings;
    @FXML private VBox paneProfile;

    // --- Browse Services Elements ---
    @FXML private TableView<Service> tableServices;
    @FXML private TableColumn<Service, String> colServiceName;
    @FXML private TableColumn<Service, String> colServiceCategory;
    @FXML private TableColumn<Service, Double> colServicePrice;
    @FXML private TableColumn<Service, String> colServiceDesc;

    @FXML private Label lblServiceDetailName;
    @FXML private Label lblServiceDetailCategory;
    @FXML private Label lblServiceDetailPrice;
    @FXML private Label lblServiceDetailDesc;
    @FXML private Button btnQuickBook;

    // --- Request Service Form Elements ---
    @FXML private Label lblRequestError;
    @FXML private Label lblRequestSuccess;
    @FXML private ComboBox<Service> comboRequestService;
    @FXML private TextField txtRequestTitle;
    @FXML private DatePicker dateRequestPreferred;
    @FXML private Label lblRequestPriceEstimate;
    @FXML private TextArea txtRequestDesc;
    @FXML private Button btnSubmitRequest;

    // --- My Bookings Elements ---
    @FXML private TableView<ServiceRequest> tableBookings;
    @FXML private TableColumn<ServiceRequest, Integer> colBookingId;
    @FXML private TableColumn<ServiceRequest, String> colBookingService;
    @FXML private TableColumn<ServiceRequest, String> colBookingTitle;
    @FXML private TableColumn<ServiceRequest, Double> colBookingPrice;
    @FXML private TableColumn<ServiceRequest, String> colBookingDate;
    @FXML private TableColumn<ServiceRequest, String> colBookingStatus;

    @FXML private Label lblBookingDetailTitle;
    @FXML private Label lblBookingDetailStatus;
    @FXML private Label lblBookingDetailPrice;
    @FXML private Label lblBookingDetailDesc;
    @FXML private Label lblBookingDetailComments;

    // --- Profile Elements ---
    @FXML private Label lblProfileError;
    @FXML private Label lblProfileSuccess;
    @FXML private TextField txtProfileUsername;
    @FXML private TextField txtProfileFullName;
    @FXML private TextField txtProfileEmail;
    @FXML private TextField txtProfilePhone;
    @FXML private Button btnSaveProfile;
    @FXML private Button btnGeneratePDF;
    @FXML private Label lblPDFStatus;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFormBindings();

        // Hidden StackPane pages should not participate in layout calculations.
        paneServices.managedProperty().bind(paneServices.visibleProperty());
        paneNewRequest.managedProperty().bind(paneNewRequest.visibleProperty());
        paneBookings.managedProperty().bind(paneBookings.visibleProperty());
        paneProfile.managedProperty().bind(paneProfile.visibleProperty());

        // Phone input is numeric-only. Keep it as text so long phone numbers do not overflow an int.
        txtProfilePhone.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));

        // Only future dates are valid. Disabling manual text entry prevents bypassing
        // the disabled calendar cells by typing today's date directly.
        dateRequestPreferred.setEditable(false);
        dateRequestPreferred.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date == null || !date.isAfter(LocalDate.now()));
            }
        });
        dateRequestPreferred.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null && !newDate.isAfter(LocalDate.now())) {
                dateRequestPreferred.setValue(null);
                showRequestError("Please select a date in the future.");
            }
        });
    }

    /**
     * Set up session user and load initial data.
     */
    public void initializeSession(ClientUser client) {
        this.sessionUser = client;
        
        // Load initial views
        loadServicesTable();
        loadRequestServicesCombo();
        loadProfileInfo();
    }

    private void setupTableColumns() {
        // Services Table
        colServiceName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colServiceCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colServicePrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colServiceDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

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

        // Bookings Table
        colBookingId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBookingService.setCellValueFactory(new PropertyValueFactory<>("serviceName"));
        colBookingTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colBookingPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colBookingDate.setCellValueFactory(new PropertyValueFactory<>("preferredDate"));
        colBookingStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colBookingPrice.setCellFactory(column -> new TableCell<>() {
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
    }

    private void setupFormBindings() {
        // Service Catalog Selection
        tableServices.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                lblServiceDetailName.setText(newSel.getName());
                lblServiceDetailCategory.setText(newSel.getCategory());
                lblServiceDetailPrice.setText(String.format("$%.2f", newSel.getPrice()));
                lblServiceDetailDesc.setText(newSel.getDescription());
                btnQuickBook.setDisable(false);
            } else {
                lblServiceDetailName.setText("Select a service...");
                lblServiceDetailCategory.setText("-");
                lblServiceDetailPrice.setText("-");
                lblServiceDetailDesc.setText("Choose a service from the list to view its full package scope description.");
                btnQuickBook.setDisable(true);
            }
        });
        
        btnQuickBook.setDisable(true);

        // Booking History Selection
        tableBookings.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                lblBookingDetailTitle.setText(newSel.getTitle());
                lblBookingDetailStatus.setText(newSel.getStatus());
                lblBookingDetailPrice.setText(String.format("$%.2f", newSel.getPrice()));
                lblBookingDetailDesc.setText(newSel.getDescription());
                
                String comment = newSel.getComments();
                if (comment == null || comment.trim().isEmpty()) {
                    lblBookingDetailComments.setText("No developer feedback logged yet.");
                } else {
                    lblBookingDetailComments.setText(comment);
                }
            } else {
                lblBookingDetailTitle.setText("Select a project...");
                lblBookingDetailStatus.setText("-");
                lblBookingDetailPrice.setText("-");
                lblBookingDetailDesc.setText("Choose a request from the table to view its full parameters.");
                lblBookingDetailComments.setText("No request selected.");
            }
        });
    }

    // --- Navigation ---

    private void resetNavButtons() {
        btnNavServices.getStyleClass().remove("nav-button-active");
        btnNavNewRequest.getStyleClass().remove("nav-button-active");
        btnNavBookings.getStyleClass().remove("nav-button-active");
        btnNavProfile.getStyleClass().remove("nav-button-active");

        paneServices.setVisible(false);
        paneNewRequest.setVisible(false);
        paneBookings.setVisible(false);
        paneProfile.setVisible(false);
        
        clearFormMessages();
    }

    private void clearFormMessages() {
        lblRequestError.setVisible(false);
        lblRequestError.setManaged(false);
        lblRequestSuccess.setVisible(false);
        lblRequestSuccess.setManaged(false);
        
        lblProfileError.setVisible(false);
        lblProfileError.setManaged(false);
        lblProfileSuccess.setVisible(false);
        lblProfileSuccess.setManaged(false);
        
        lblPDFStatus.setVisible(false);
        lblPDFStatus.setManaged(false);
    }

    @FXML
    void showServices(ActionEvent event) {
        resetNavButtons();
        btnNavServices.getStyleClass().add("nav-button-active");
        paneServices.setVisible(true);
        loadServicesTable();
    }

    @FXML
    void showNewRequest(ActionEvent event) {
        resetNavButtons();
        btnNavNewRequest.getStyleClass().add("nav-button-active");
        paneNewRequest.setVisible(true);
        loadRequestServicesCombo();
    }

    @FXML
    void showBookings(ActionEvent event) {
        resetNavButtons();
        btnNavBookings.getStyleClass().add("nav-button-active");
        paneBookings.setVisible(true);
        loadBookingsTable();
    }

    @FXML
    void showProfile(ActionEvent event) {
        resetNavButtons();
        btnNavProfile.getStyleClass().add("nav-button-active");
        paneProfile.setVisible(true);
        loadProfileInfo();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Stage stage = (Stage) btnNavServices.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mouchy/app/views/login.fxml"));
            Parent root = loader.load();
            WindowUtil.configure(stage, new Scene(root), 950, 700);
            stage.setTitle("Mouchy Creative Agency - Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Services Logic ---

    private void loadServicesTable() {
        List<Service> list = DatabaseManager.getAllServices();
        tableServices.setItems(FXCollections.observableArrayList(list));
    }

    @FXML
    void handleQuickBook(ActionEvent event) {
        Service selected = tableServices.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showNewRequest(null);
            comboRequestService.setValue(selected);
            handleServiceSelection(null);
        }
    }

    // --- New Booking Request Form Logic ---

    private void loadRequestServicesCombo() {
        Service previousSelection = comboRequestService.getValue();
        List<Service> list = DatabaseManager.getAllServices();
        comboRequestService.setItems(FXCollections.observableArrayList(list));

        if (previousSelection != null) {
            Service stillAvailable = list.stream()
                    .filter(service -> service.getId() == previousSelection.getId())
                    .findFirst()
                    .orElse(null);
            comboRequestService.setValue(stillAvailable);
            handleServiceSelection(null);
        }
    }

    @FXML
    void handleServiceSelection(ActionEvent event) {
        Service selected = comboRequestService.getValue();
        if (selected != null) {
            lblRequestPriceEstimate.setText(String.format("$%.2f", selected.getPrice()));
        } else {
            lblRequestPriceEstimate.setText("$0.00");
        }
    }

    @FXML
    void handleSubmitRequest(ActionEvent event) {
        clearFormMessages();
        Service selectedService = comboRequestService.getValue();
        String title = txtRequestTitle.getText().trim();
        LocalDate preferredDate = dateRequestPreferred.getValue();
        String desc = txtRequestDesc.getText().trim();

        if (selectedService == null || title.isEmpty() || preferredDate == null || desc.isEmpty()) {
            showRequestError("All fields are required to request a service.");
            return;
        }

        if (!preferredDate.isAfter(LocalDate.now())) {
            showRequestError("Please select a date in the future.");
            return;
        }

        if (!DatabaseManager.isServiceActive(selectedService.getId())) {
            showRequestError("This service is no longer available. Please select another service.");
            loadRequestServicesCombo();
            return;
        }

        boolean success = DatabaseManager.createRequest(
                sessionUser.getId(),
                selectedService.getId(),
                title,
                desc,
                preferredDate.toString()
        );

        if (success) {
            lblRequestSuccess.setText("Project request submitted successfully! Tracking ID can be seen in 'My Bookings'.");
            lblRequestSuccess.setVisible(true);
            lblRequestSuccess.setManaged(true);
            clearRequestForm(null);
        } else {
            showRequestError("Database Error: Failed to submit booking request.");
        }
    }

    @FXML
    void clearRequestForm(ActionEvent event) {
        comboRequestService.setValue(null);
        txtRequestTitle.clear();
        dateRequestPreferred.setValue(null);
        lblRequestPriceEstimate.setText("$0.00");
        txtRequestDesc.clear();
    }

    private void showRequestError(String message) {
        lblRequestError.setText(message);
        lblRequestError.setVisible(true);
        lblRequestError.setManaged(true);
    }

    // --- Bookings Table Logic ---

    private void loadBookingsTable() {
        List<ServiceRequest> list = DatabaseManager.getRequestsByClient(sessionUser.getId());
        tableBookings.setItems(FXCollections.observableArrayList(list));
    }

    // --- Profile & PDF Report Logic ---

    private void loadProfileInfo() {
        if (sessionUser != null) {
            txtProfileUsername.setText(sessionUser.getUsername());
            txtProfileFullName.setText(sessionUser.getFullName());
            txtProfileEmail.setText(sessionUser.getEmail());
            txtProfilePhone.setText(sessionUser.getPhoneNumber());
        }
    }

    @FXML
    void handleSaveProfile(ActionEvent event) {
        clearFormMessages();
        String fullName = txtProfileFullName.getText().trim();
        String email = txtProfileEmail.getText().trim();
        String phone = txtProfilePhone.getText().trim();

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showProfileError("All contact fields are required.");
            return;
        }

        if (!ValidationUtil.isValidEmail(email)) {
            showProfileError("Please enter a valid email address.");
            return;
        }

        if (!ValidationUtil.isValidPhoneNumber(phone)) {
            showProfileError("Phone number must contain digits only.");
            return;
        }

        boolean success = DatabaseManager.updateUserProfile(sessionUser.getId(), fullName, email, phone);
        if (success) {
            sessionUser.setFullName(fullName);
            sessionUser.setEmail(email);
            sessionUser.setPhoneNumber(phone);
            
            lblProfileSuccess.setText("Profile updated successfully!");
            lblProfileSuccess.setVisible(true);
            lblProfileSuccess.setManaged(true);
        } else {
            showProfileError("Database Error: Failed to update contact info.");
        }
    }

    @FXML
    void handlePDFGeneration(ActionEvent event) {
        clearFormMessages();
        List<ServiceRequest> clientRequests = DatabaseManager.getRequestsByClient(sessionUser.getId());
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Profile & Bookings Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("mouchy_report_" + sessionUser.getUsername() + ".pdf");
        
        Stage stage = (Stage) btnGeneratePDF.getScene().getWindow();
        File destFile = fileChooser.showSaveDialog(stage);
        
        if (destFile != null) {
            if (!destFile.getName().toLowerCase().endsWith(".pdf")) {
                destFile = new File(destFile.getAbsolutePath() + ".pdf");
            }

            try {
                PDFGenerator.generateClientReport(sessionUser, clientRequests, destFile.getAbsolutePath());
                lblPDFStatus.setText("Report successfully saved to: " + destFile.getName());
                lblPDFStatus.setManaged(true);
                lblPDFStatus.setVisible(true);
            } catch (Exception e) {
                lblPDFStatus.setText("PDF Generation Error: " + e.getMessage());
                lblPDFStatus.setManaged(true);
                lblPDFStatus.setVisible(true);
                e.printStackTrace();
            }
        }
    }

    private void showProfileError(String message) {
        lblProfileError.setText(message);
        lblProfileError.setVisible(true);
        lblProfileError.setManaged(true);
    }
}
