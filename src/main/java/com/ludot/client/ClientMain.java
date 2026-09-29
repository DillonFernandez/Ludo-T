package com.ludot.client;

import com.ludot.gui.LudoFrame;

import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public final class ClientMain {

    private static final int PORT = 5050;

    private final String host;
    private final LudoFrame frame;
    private GameClient client;

    private ClientMain(String host) {
        this.host = host;
        this.frame = new LudoFrame();
    }

    public static void main(String[] args) {
        String host = args.length > 0
                ? args[0]
                : "localhost";

        SwingUtilities.invokeLater(
                () -> new ClientMain(host).start());
    }

    private void start() {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeClient();
            }
        });

        frame.setVisible(true);

        try {
            client = new GameClient(host, PORT);

            client.listenForResponses(message -> SwingUtilities.invokeLater(
                    () -> frame.handleServerMessage(message)));

            client.sendRequest("START_GAME");

        } catch (IOException e) {
            frame.appendMessage(
                    "Unable to connect to server: "
                            + e.getMessage());
        }
    }

    private void closeClient() {
        if (client == null) {
            return;
        }

        try {
            client.close();
        } catch (IOException e) {
            System.err.println(
                    "Unable to close client: "
                            + e.getMessage());
        }
    }
}