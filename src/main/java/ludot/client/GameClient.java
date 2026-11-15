// Sends asynchronous server requests and delivers matching replies and pushed game updates.
package ludot.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import ludot.application.GameSnapshot;
import ludot.network.Messages;

public final class GameClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final BufferedWriter output;
    private final Consumer<GameSnapshot> updates;
    private final Consumer<String> disconnected;
    private final Map<Long, CompletableFuture<GameSnapshot>> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024), task -> {
                Thread thread = new Thread(task, "ludot-client-writer");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean closed;

    public GameClient(String host, int port, Consumer<GameSnapshot> updates,
            Consumer<String> disconnected) throws IOException {
        this.updates = updates;
        this.disconnected = disconnected;
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            socket.close();
            writer.shutdownNow();
            throw error;
        }
        Thread reader = new Thread(this::readResponses, "ludot-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public CompletableFuture<GameSnapshot> request(String command) {
        CompletableFuture<GameSnapshot> result = new CompletableFuture<>();
        if (!"START".equals(command) && !"STATUS".equals(command)
                && (command == null || !command.matches("LOG [a-zA-Z0-9-]{1,40} [0-9]{1,9}"))) {
            result.completeExceptionally(new IllegalArgumentException("Unknown request."));
            return result;
        }
        if (closed || pending.size() >= 1024) {
            result.completeExceptionally(
                    new IOException("The client is disconnected or has too many pending requests."));
            return result;
        }
        long id = sequence.incrementAndGet();
        pending.put(id, result);
        result.orTimeout(10, TimeUnit.SECONDS).whenComplete((snapshot, error) -> pending.remove(id));
        try {
            writer.execute(() -> {
                try {
                    output.write(id + "\t" + command);
                    output.newLine();
                    output.flush();
                } catch (IOException error) {
                    result.completeExceptionally(error);
                    stop("The connection to the server was lost.");
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(new IOException("The client cannot send another request.", error));
        }
        return result;
    }

    private void readResponses() {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8))) {
            String message;
            while ((message = Messages.readLine(input, 1_000_000)) != null) {
                String[] fields = message.split("\t", 3);
                if (fields.length != 3) {
                    throw new IOException("Invalid server response.");
                }
                long id = Long.parseLong(fields[0]);
                if (id == 0 && fields[1].equals("UPDATE")) {
                    updates.accept(GameSnapshot.decode(fields[2]));
                    continue;
                }
                CompletableFuture<GameSnapshot> result = pending.get(id);
                if (result != null) {
                    if (fields[1].equals("OK")) {
                        result.complete(GameSnapshot.decode(fields[2]));
                    } else {
                        result.completeExceptionally(new IOException(Messages.decodeText(fields[2])));
                    }
                }
            }
            stop("The server closed the connection.");
        } catch (IOException | RuntimeException error) {
            stop("The connection to the server was lost.");
        }
    }

    private synchronized void stop(String reason) {
        if (closed) {
            return;
        }
        closed = true;
        writer.shutdownNow();
        try {
            socket.close();
        } catch (IOException error) {
            System.err.println("Could not close the client connection.");
        }
        for (CompletableFuture<GameSnapshot> result : pending.values()) {
            result.completeExceptionally(new IOException(reason));
        }
        pending.clear();
        disconnected.accept(reason);
    }

    public void close() {
        stop("Disconnected.");
    }
}
