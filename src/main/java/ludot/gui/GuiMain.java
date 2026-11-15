// Launches the Swing client for the requested server address and port.
package ludot.gui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        if (host.isBlank() || port < 1 || port > 65535 || args.length > 2) {
            throw new IllegalArgumentException("Use: java -jar target/ludo-t-1.0.jar [server-address] [port]");
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception error) {
                System.err.println("Using the default Java appearance.");
            }
            new GameWindow(host, port).setVisible(true);
        });
    }
}
