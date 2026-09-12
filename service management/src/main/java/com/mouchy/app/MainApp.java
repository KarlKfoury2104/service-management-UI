package com.mouchy.app;

import com.mouchy.app.database.DatabaseManager;
import com.mouchy.app.util.WindowUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * The main JavaFX Application class.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Initialize SQLite Database tables and seed initial data
        DatabaseManager.initializeDatabase();

        try {
            // Load login FXML layout
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mouchy/app/views/login.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root);
            WindowUtil.configure(primaryStage, scene, 950, 700);
            primaryStage.setTitle("Mouchy Creative Agency - Login");
            primaryStage.show();
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("Critical Error loading login.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
