package com.ludot.gui;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;

public final class LudoFrame extends JFrame {

    private final JTextArea outputArea;
    private final LudoBoardPanel boardPanel;

    public LudoFrame() {
        super("LUDO-T");

        outputArea = new JTextArea();
        outputArea.setEditable(false);

        JScrollPane outputScrollPane = new JScrollPane(outputArea);

        outputScrollPane.setPreferredSize(
                new Dimension(350, 600));

        setLayout(new BorderLayout());

        boardPanel = new LudoBoardPanel();
        add(boardPanel, BorderLayout.CENTER);
        add(outputScrollPane, BorderLayout.EAST);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    public void appendMessage(String message) {
        outputArea.append(
                message + System.lineSeparator());
    }

    public void handleServerMessage(String message) {
        handleServerMessage(message, boardPanel, outputArea);
    }

    // Keeps the actual message path testable on the EDT without a display server.
    static void handleServerMessage(String message, LudoBoardPanel boardPanel,
            JTextArea outputArea) {
        if ("RESET_BOARD".equals(message)) {
            boardPanel.resetPieces();
            return;
        }

        if (message.startsWith("PIECE|")) {
            String[] parts = message.split("\\|");

            if (parts.length == 5) {
                try {
                    boardPanel.updatePieceState(
                            parts[1],
                            parts[2],
                            parts[3],
                            Integer.parseInt(parts[4]));

                    return;
                } catch (NumberFormatException ignored) {
                    // Malformed messages are shown in the log below.
                }
            }
        }

        outputArea.append(message + System.lineSeparator());
    }
}
