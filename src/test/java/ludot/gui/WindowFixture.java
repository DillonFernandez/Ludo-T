// Creates Swing test windows and captures their output and images, with a delayed-reply proxy.
package ludot.gui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.imageio.ImageIO;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ludot.application.GameSnapshot;
import ludot.network.Messages;

import static org.junit.jupiter.api.Assertions.*;

final class WindowFixture implements AutoCloseable {
    GameWindow window;
    BoardPanel board;
    JTextArea output;
    JLabel status;

    WindowFixture() throws Exception {
        this(1, false);
    }

    WindowFixture(int port, boolean visible) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception error) {
                throw new IllegalStateException("Could not select the application's appearance.", error);
            }
            window = new GameWindow("127.0.0.1", port);
            BorderLayout layout = (BorderLayout) window.getContentPane().getLayout();
            JSplitPane split = (JSplitPane) layout.getLayoutComponent(BorderLayout.CENTER);
            board = (BoardPanel) split.getLeftComponent();
            output = (JTextArea) ((JScrollPane) split.getRightComponent()).getViewport().getView();
            status = (JLabel) layout.getLayoutComponent(BorderLayout.SOUTH);
            if (visible) {
                window.setVisible(true);
            }
        });
    }

    void apply(GameSnapshot snapshot) throws Exception {
        SwingUtilities.invokeAndWait(() -> window.showSnapshot(snapshot));
    }

    String text() throws Exception {
        String[] value = new String[1];
        SwingUtilities.invokeAndWait(() -> value[0] = output.getText());
        return value[0];
    }

    BufferedImage image(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            board.setSize(size, size);
            var graphics = image.createGraphics();
            try {
                board.paint(graphics);
            } finally {
                graphics.dispose();
            }
        });
        return image;
    }

    void saveWindowImage(Path path) throws Exception {
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            var graphics = image.createGraphics();
            try {
                window.paint(graphics);
            } finally {
                graphics.dispose();
            }
        });
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }

    void awaitText(Predicate<String> expected) throws Exception {
        CountDownLatch displayed = new CountDownLatch(1);
        DocumentListener listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) {
                check();
            }

            public void removeUpdate(DocumentEvent event) {
                check();
            }

            public void changedUpdate(DocumentEvent event) {
                check();
            }

            private void check() {
                if (expected.test(output.getText())) {
                    displayed.countDown();
                }
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            output.getDocument().addDocumentListener(listener);
            if (expected.test(output.getText())) {
                displayed.countDown();
            }
        });
        try {
            assertTrue(displayed.await(10, TimeUnit.SECONDS), "The expected server output was not displayed.");
        } finally {
            SwingUtilities.invokeAndWait(() -> output.getDocument().removeDocumentListener(listener));
        }
    }

    void awaitEnding(String ending) throws Exception {
        CountDownLatch displayed = new CountDownLatch(1);
        DocumentListener listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) {
                check();
            }

            public void removeUpdate(DocumentEvent event) {
                check();
            }

            public void changedUpdate(DocumentEvent event) {
                check();
            }

            private void check() {
                try {
                    int length = output.getDocument().getLength();
                    if (length >= ending.length() && ending.equals(output.getDocument().getText(
                            length - ending.length(), ending.length()))) {
                        displayed.countDown();
                    }
                } catch (javax.swing.text.BadLocationException error) {
                    throw new IllegalStateException(error);
                }
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            output.getDocument().addDocumentListener(listener);
            listener.insertUpdate(null);
        });
        try {
            assertTrue(displayed.await(20, TimeUnit.SECONDS), "The complete server output did not arrive.");
        } finally {
            SwingUtilities.invokeAndWait(() -> output.getDocument().removeDocumentListener(listener));
        }
    }

    public void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> window.dispose());
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    static final class DelayedReply implements AutoCloseable {
        final ServerSocket listener = new ServerSocket(0);
        final Socket server;
        Socket gui;
        final CountDownLatch captured = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch delivered = new CountDownLatch(1);
        private final ExecutorService threads = Executors.newFixedThreadPool(3);
        private final CompletableFuture<Void> failure = new CompletableFuture<>();

        DelayedReply(int port) throws Exception {
            server = new Socket("127.0.0.1", port);
            threads.execute(() -> {
                try {
                    gui = listener.accept();
                    BufferedWriter toServer = new BufferedWriter(
                            new OutputStreamWriter(server.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedWriter toGui = new BufferedWriter(
                            new OutputStreamWriter(gui.getOutputStream(), StandardCharsets.UTF_8));
                    threads.execute(() -> {
                        try (BufferedReader input = new BufferedReader(
                                new InputStreamReader(gui.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = input.readLine()) != null) {
                                write(toServer, line);
                            }
                        } catch (Exception error) {
                            failure.completeExceptionally(error);
                        }
                    });
                    try (BufferedReader input = new BufferedReader(
                            new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = Messages.readLine(input, 1_000_000)) != null) {
                            if (line.startsWith("1\tOK\t")) {
                                String held = line;
                                captured.countDown();
                                threads.execute(() -> {
                                    try {
                                        assertTrue(release.await(10, TimeUnit.SECONDS));
                                        write(toGui, held);
                                        delivered.countDown();
                                    } catch (Throwable error) {
                                        failure.completeExceptionally(error);
                                    }
                                });
                            } else {
                                write(toGui, line);
                            }
                        }
                    }
                } catch (Exception error) {
                    failure.completeExceptionally(error);
                }
            });
        }

        private static void write(BufferedWriter writer, String line) throws Exception {
            synchronized (writer) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        }

        void check() throws Exception {
            if (failure.isDone()) {
                failure.get(1, TimeUnit.SECONDS);
            }
        }

        public void close() throws Exception {
            release.countDown();
            listener.close();
            server.close();
            if (gui != null) {
                gui.close();
            }
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
