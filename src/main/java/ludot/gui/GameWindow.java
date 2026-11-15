// Connects a Swing window to the server and displays the board, status and paged game output.
package ludot.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.DefaultCaret;
import ludot.application.GameSnapshot;
import ludot.application.GameService;
import ludot.client.GameClient;

public final class GameWindow extends JFrame {
    private final BoardPanel board = new BoardPanel();
    private final JTextArea output = new JTextArea();
    private final JScrollPane console = new JScrollPane(output);
    private final JLabel status = new JLabel("Connecting...");
    private final String serverHost;
    private final int serverPort;
    private GameClient client;
    private GameSnapshot snapshot;
    private int connectionVersion;
    private String outputSession;
    private int outputLines;
    private boolean readingOutput;
    private long outputRevision = -1;
    CompletableFuture<GameSnapshot> startup;

    public GameWindow() {
        this("localhost", 5050);
    }

    public GameWindow(String serverHost, int serverPort) {
        super("LUDO-T");
        if (serverHost == null || serverHost.isBlank() || serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("A server address and a port from 1 to 65535 are required.");
        }
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);
        output.setEditable(false);
        output.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        output.setMargin(new java.awt.Insets(0, 2, 0, 2));
        output.getAccessibleContext().setAccessibleName("Simulation output");
        ((DefaultCaret) output.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        console.setPreferredSize(new Dimension(430, 750));
        console.setMinimumSize(new Dimension(250, 250));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, board, console);
        split.setResizeWeight(0.64);
        split.setDividerSize(4);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent event) {
                connect();
            }

            public void windowClosed(WindowEvent event) {
                disconnect();
            }
        });
        setMinimumSize(new Dimension(920, 600));
        pack();
        setLocationRelativeTo(null);
    }

    private void connect() {
        int version = ++connectionVersion;
        status.setText("Connecting...");
        new SwingWorker<GameClient, Void>() {
            protected GameClient doInBackground() throws Exception {
                return new GameClient(serverHost, serverPort,
                        update -> SwingUtilities.invokeLater(() -> {
                            if (version == connectionVersion) {
                                showSnapshot(update);
                            }
                        }),
                        reason -> SwingUtilities.invokeLater(() -> {
                            if (version == connectionVersion) {
                                connectionLost(reason);
                            }
                        }));
            }

            protected void done() {
                try {
                    GameClient connected = get();
                    if (version != connectionVersion || !isDisplayable()) {
                        connected.close();
                        return;
                    }
                    client = connected;
                    status.setText("Starting simulation...");
                    startup = client.request("START").whenComplete((update, error) -> SwingUtilities.invokeLater(() -> {
                        if (version != connectionVersion) {
                            return;
                        }
                        if (error == null) {
                            showSnapshot(update);
                        } else {
                            disconnect();
                            status.setText(
                                    "Could not start the simulation. Check the server and database, then reopen this window.");
                        }
                    }));
                } catch (Exception error) {
                    if (version == connectionVersion) {
                        connectionLost(
                                "Connection failed. Start the database and game server, then reopen this window.");
                    }
                }
            }
        }.execute();
    }

    void showSnapshot(GameSnapshot update) {
        if (snapshot != null && update.getRevision() < snapshot.getRevision()) {
            return;
        }
        snapshot = update;
        board.setSnapshot(update);
        if (client == null) {
            output.setText(update.getLog());
        } else {
            if (!update.getSession().equals(outputSession)) {
                outputSession = update.getSession();
                outputLines = 0;
                outputRevision = -1;
                readingOutput = false;
                output.setText("");
            }
            readOutput();
        }
        status.setText("Round " + update.getRound() + " / 10,000 - "
                + update.getStatus().toLowerCase(java.util.Locale.UK));
    }

    private void readOutput() {
        if (readingOutput || client == null || snapshot == null
                || outputRevision >= snapshot.getRevision()) {
            return;
        }
        readingOutput = true;
        int version = connectionVersion;
        String session = outputSession;
        client.request("LOG " + session + " " + outputLines)
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    if (version != connectionVersion || !session.equals(outputSession)) {
                        return;
                    }
                    readingOutput = false;
                    if (error != null) {
                        status.setText("Could not retrieve simulation output. Reopen this window to reconnect.");
                        return;
                    }
                    int lines = page.getLog().isEmpty() ? 0 : page.getLog().split("\n", -1).length;
                    appendOutput(page.getLog());
                    outputLines += lines;
                    if (lines < GameService.OUTPUT_PAGE_SIZE) {
                        outputRevision = page.getRevision();
                    }
                    readOutput();
                }));
    }

    private void appendOutput(String text) {
        if (text.isEmpty()) {
            return;
        }
        int horizontal = console.getHorizontalScrollBar().getValue();
        int vertical = console.getVerticalScrollBar().getValue();
        boolean atEnd = vertical
                + console.getVerticalScrollBar().getVisibleAmount() >= console.getVerticalScrollBar().getMaximum() - 2;
        output.append((output.getDocument().getLength() == 0 ? "" : "\n") + text);
        SwingUtilities.invokeLater(() -> {
            console.getHorizontalScrollBar().setValue(horizontal);
            console.getVerticalScrollBar().setValue(atEnd ? console.getVerticalScrollBar().getMaximum() : vertical);
        });
    }

    private void connectionLost(String reason) {
        connectionVersion++;
        client = null;
        status.setText(reason);
    }

    private void disconnect() {
        GameClient previous = client;
        connectionLost("Disconnected");
        if (previous != null) {
            previous.close();
        }
    }
}
