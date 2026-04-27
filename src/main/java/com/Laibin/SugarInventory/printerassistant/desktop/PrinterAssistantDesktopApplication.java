package com.Laibin.SugarInventory.printerassistant.desktop;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class PrinterAssistantDesktopApplication {

    private PrinterAssistantDesktopApplication() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            PrinterAssistantDesktopFrame frame = new PrinterAssistantDesktopFrame(args);
            frame.setVisible(true);
        });
    }
}
