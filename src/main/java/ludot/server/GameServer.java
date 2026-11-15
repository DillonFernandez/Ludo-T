// Serialises game requests and round updates while managing client connections and reply queues.
package ludot.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import ludot.application.GameRepository;
import ludot.application.GameService;
import ludot.application.GameSnapshot;
import ludot.network.Messages;

public final class GameServer implements AutoCloseable {
    private final GameRepository repository;
    private final GameService service;
    private final int roundsPerUpdate;
    private final ServerSocket listener;
    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private final ScheduledThreadPoolExecutor requests = new ScheduledThreadPoolExecutor(1);
    private final ScheduledFuture<?> roundTask;
    private final Semaphore requestPlaces = new Semaphore(4096);
    private volatile boolean closed;
    private boolean repositoryClosed;

    public GameServer(String host, int port, GameRepository repository,
            GameService service, int roundDelayMillis) throws IOException {
        this(host, port, repository, service, roundDelayMillis, 1);
    }

    public GameServer(String host, int port, GameRepository repository,
            GameService service, int roundDelayMillis, int roundsPerUpdate) throws IOException {
        if (roundDelayMillis < 1) {
            throw new IllegalArgumentException("The round delay must be positive.");
        }
        if (roundsPerUpdate < 1) {
            throw new IllegalArgumentException("The number of rounds per update must be positive.");
        }
        this.repository = repository;
        this.service = service;
        this.roundsPerUpdate = roundsPerUpdate;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(host, port));
        requests.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        roundTask = requests.scheduleWithFixedDelay(this::advanceGame,
                roundDelayMillis, roundDelayMillis, TimeUnit.MILLISECONDS);
        Thread acceptor = new Thread(this::acceptClients, "ludot-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptClients() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                if (clients.size() >= 32) {
                    socket.close();
                    continue;
                }
                ClientConnection client = new ClientConnection(socket);
                clients.add(client);
                client.start();
            } catch (IOException error) {
                if (!closed) {
                    System.err.println("Could not accept a client: " + error.getMessage());
                }
            }
        }
    }

    private void advanceGame() {
        try {
            if (service.advance(roundsPerUpdate)) {
                broadcast();
            }
        } catch (SQLException | RuntimeException error) {
            failGame(error);
        }
    }

    private void process(ClientConnection client, String request) {
        String[] fields = request.split("\t", -1);
        long id;
        try {
            if (fields.length != 2) {
                throw new IllegalArgumentException();
            }
            id = Long.parseLong(fields[0]);
            if (id < 1) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            client.send("0\tERROR\t" + Messages.encodeText("Invalid request."));
            return;
        }
        String previousResponse = client.responses.get(id);
        if (previousResponse != null) {
            if (fields[1].equals(client.commands.get(id))) {
                client.send(previousResponse);
            } else {
                client.send(id + "\tERROR\t" + Messages.encodeText("The request ID has already been used."));
            }
            return;
        }
        if (id <= client.retiredThrough) {
            client.send(id + "\tERROR\t" + Messages.encodeText("The request ID is too old to retry."));
            return;
        }
        try {
            if (fields[1].equals("START")) {
                long previous = service.getSnapshot().getRevision();
                service.start();
                if (service.getSnapshot().getRevision() != previous) {
                    broadcast();
                }
            } else if (fields[1].startsWith("LOG ")) {
                String[] parts = fields[1].split(" ", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid output request.");
                }
                GameSnapshot page = service.getOutput(parts[1], Integer.parseInt(parts[2]));
                client.respond(id, fields[1], id + "\tOK\t" + page.encode());
                return;
            } else if (!fields[1].equals("STATUS")) {
                client.respond(id, fields[1], id + "\tERROR\t" + Messages.encodeText("Unknown request."));
                return;
            }
            client.respond(id, fields[1], id + "\tOK\t" + service.getSnapshot().encode());
        } catch (IllegalArgumentException error) {
            client.respond(id, fields[1], id + "\tERROR\t" + Messages.encodeText("Invalid request."));
        } catch (SQLException | RuntimeException error) {
            failGame(error);
            client.respond(id, fields[1], id + "\tERROR\t" + Messages.encodeText(
                    "The server could not process or save the request."));
        }
    }

    private void failGame(Exception error) {
        System.err.println("Simulation stopped: " + error.getClass().getSimpleName());
        service.fail();
        broadcast();
    }

    private void broadcast() {
        String update = "0\tUPDATE\t" + service.getSnapshot().encode();
        for (ClientConnection client : clients) {
            client.setUpdate(update);
        }
    }

    public int getPort() {
        return listener.getLocalPort();
    }

    public int getQueuedRequestCount() {
        int count = 0;
        for (Runnable queued : requests.getQueue()) {
            if (queued != roundTask) {
                count++;
            }
        }
        return count;
    }

    public synchronized void close() throws IOException {
        if (repositoryClosed) {
            return;
        }
        if (!closed) {
            closed = true;
            listener.close();
            for (ClientConnection client : clients) {
                client.disconnect();
            }
            requests.shutdown();
        }
        try {
            if (!requests.awaitTermination(5, TimeUnit.SECONDS)) {
                requests.shutdownNow();
                if (!requests.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IOException(
                            "The server request worker did not stop in time. Retry closing after it stops.");
                }
            }
            repository.close();
            repositoryClosed = true;
        } catch (InterruptedException error) {
            requests.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping the server.", error);
        } catch (SQLException error) {
            throw new IOException("Could not close the database connection.", error);
        }
    }

    private final class ClientConnection {
        private final Socket socket;
        private final ArrayBlockingQueue<String> outgoing = new ArrayBlockingQueue<>(512);
        private final Semaphore replyPlaces = new Semaphore(512);
        private volatile String latestUpdate;
        private final Map<Long, String> responses = new LinkedHashMap<>();
        private final Map<Long, String> commands = new LinkedHashMap<>();
        private int responseCharacters;
        private long retiredThrough;
        private volatile boolean disconnected;

        ClientConnection(Socket socket) {
            this.socket = socket;
        }

        void start() {
            Thread reader = new Thread(this::readRequests, "ludot-request-reader");
            Thread writer = new Thread(this::writeResponses, "ludot-response-writer");
            reader.setDaemon(true);
            writer.setDaemon(true);
            writer.start();
            reader.start();
        }

        void readRequests() {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!closed && !disconnected && (line = Messages.readLine(input, 128)) != null) {
                    while (!replyPlaces.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                        if (closed || disconnected) {
                            return;
                        }
                    }
                    while (!requestPlaces.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                        if (closed || disconnected) {
                            replyPlaces.release();
                            return;
                        }
                    }
                    String request = line;
                    // Queue reads and moves on one worker so snapshots never show a partially
                    // completed round.
                    try {
                        requests.execute(() -> {
                            try {
                                process(this, request);
                            } finally {
                                requestPlaces.release();
                            }
                        });
                    } catch (RejectedExecutionException error) {
                        replyPlaces.release();
                        requestPlaces.release();
                        throw error;
                    }
                }
            } catch (IOException | RejectedExecutionException error) {
                if (!closed && !disconnected) {
                    System.err.println("A client disconnected: " + error.getClass().getSimpleName());
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                disconnect();
            }
        }

        void writeResponses() {
            try (BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8))) {
                while (!closed && !disconnected) {
                    String message = outgoing.poll(10, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        output.write(message);
                        output.newLine();
                        output.flush();
                        replyPlaces.release();
                    }
                    String update = takeUpdate();
                    if (update != null) {
                        output.write(update);
                        output.newLine();
                        output.flush();
                    }
                }
            } catch (IOException error) {
                if (!disconnected && !closed) {
                    System.err.println("Could not send a client update.");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                disconnect();
            }
        }

        void send(String message) {
            if (!disconnected) {
                outgoing.add(message);
            }
        }

        synchronized void setUpdate(String update) {
            latestUpdate = update;
        }

        synchronized String takeUpdate() {
            String update = latestUpdate;
            latestUpdate = null;
            return update;
        }

        void respond(long id, String command, String message) {
            commands.put(id, command);
            responses.put(id, message);
            responseCharacters += message.length();
            while (responses.size() > 256 || responseCharacters > 1_000_000) {
                long oldest = responses.keySet().iterator().next();
                responseCharacters -= responses.remove(oldest).length();
                commands.remove(oldest);
                retiredThrough = Math.max(retiredThrough, oldest);
            }
            send(message);
        }

        void disconnect() {
            disconnected = true;
            clients.remove(this);
            try {
                socket.close();
            } catch (IOException error) {
                System.err.println("Could not close a client socket.");
            }
        }
    }
}
