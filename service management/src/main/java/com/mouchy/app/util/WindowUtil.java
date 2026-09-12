package com.mouchy.app.util;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * Shared desktop-window configuration. Keeps every scene resizable and adds
 * F11 as an explicit full-screen toggle.
 */
public final class WindowUtil {
    private WindowUtil() {
    }

    public static void configure(Stage stage, Scene scene, double preferredWidth, double preferredHeight) {
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setMaxWidth(Double.MAX_VALUE);
        stage.setMaxHeight(Double.MAX_VALUE);
        stage.setFullScreenExitHint("Press F11 or Esc to exit full screen.");

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });

        if (!stage.isMaximized() && !stage.isFullScreen()) {
            stage.setWidth(preferredWidth);
            stage.setHeight(preferredHeight);
        }
    }
}
