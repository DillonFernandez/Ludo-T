// Supplies test servers, controlled repository saves and clients for request and state checks.
package ludot.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import ludot.application.GameRepository;
import ludot.application.GameService;
import ludot.application.GameSnapshot;
import ludot.client.GameClient;
import ludot.persistence.JdbcGameRepository;
import ludot.random.RandomNumbers;
import ludot.random.RandomSource;

import static org.junit.jupiter.api.Assertions.*;

public final class ServerFixture implements AutoCloseable {
    public final String databaseUrl = "jdbc:h2:mem:" + UUID.randomUUID();
    public final GateRepository repository;
    public final GameService service;
    public final GameServer server;
    private final List<GameClient> clients = new ArrayList<>();

    public ServerFixture() throws Exception {
        this(false, 60_000, 1, new RandomNumbers(1234));
    }

    public ServerFixture(boolean blocked, int delay, int batch, RandomSource random) throws Exception {
        repository = new GateRepository(new JdbcGameRepository(databaseUrl, "sa", ""), blocked);
        service = new GameService(repository, random);
        server = new GameServer("127.0.0.1", 0, repository, service, delay, batch);
    }

    public static ServerFixture blocked() throws Exception {
        return new ServerFixture(true, 60_000, 1, new RandomNumbers(1234));
    }

    public GameClient client() throws Exception {
        return client(update -> {
        });
    }

    public GameClient client(Consumer<GameSnapshot> updates) throws Exception {
        GameClient client = new GameClient("127.0.0.1", server.getPort(), updates, reason -> {
        });
        clients.add(client);
        client.request("STATUS").get(5, TimeUnit.SECONDS);
        return client;
    }

    public CompletableFuture<GameSnapshot> beginBlocked(GameClient client) throws Exception {
        CompletableFuture<GameSnapshot> request = client.request("START");
        assertTrue(repository.entered.await(5, TimeUnit.SECONDS), "The real save did not begin.");
        return request;
    }

    public void close() throws Exception {
        repository.release.countDown();
        for (GameClient client : clients) {
            client.close();
        }
        server.close();
    }

    public static void await(BooleanSupplier condition) throws Exception {
        ScheduledExecutorService checks = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> reached = new CompletableFuture<>();
        try {
            checks.scheduleAtFixedRate(() -> {
                try {
                    if (condition.getAsBoolean()) {
                        reached.complete(null);
                    }
                } catch (Throwable error) {
                    reached.completeExceptionally(error);
                }
            }, 0, 2, TimeUnit.MILLISECONDS);
            reached.get(10, TimeUnit.SECONDS);
        } finally {
            checks.shutdownNow();
            assertTrue(checks.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    public static void sameState(GameSnapshot expected, GameSnapshot actual) {
        assertEquals(expected.getSession(), actual.getSession());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(expected.getRound(), actual.getRound());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getMystery(), actual.getMystery());
        assertArrayEquals(expected.getLocations(), actual.getLocations());
        assertEquals(expected.getLog(), actual.getLog());
    }

    public static final class GateRepository implements GameRepository {
        private final GameRepository delegate;
        private final boolean blocked;
        public final CountDownLatch entered = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);
        public final AtomicInteger saves = new AtomicInteger();
        public volatile boolean closed;
        public volatile boolean fail;

        GateRepository(GameRepository delegate, boolean blocked) {
            this.delegate = delegate;
            this.blocked = blocked;
        }

        public GameSnapshot loadLatest() throws SQLException {
            return delegate.loadLatest();
        }

        public List<String> loadMessages(String session, int offset, int limit) throws SQLException {
            return delegate.loadMessages(session, offset, limit);
        }

        public void save(GameSnapshot snapshot, List<String> messages) throws SQLException {
            entered.countDown();
            if (blocked) {
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new SQLException("Save gate timed out.");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Save gate interrupted.", error);
                }
            }
            if (fail) {
                throw new SQLException("Database unavailable.");
            }
            delegate.save(snapshot, messages);
            saves.incrementAndGet();
        }

        public void close() throws SQLException {
            delegate.close();
            closed = true;
        }
    }

    public static final class RawClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader input;
        private final BufferedWriter output;

        public RawClient(int port) throws Exception {
            this(port, 0);
        }

        public RawClient(int port, int receiveBuffer) throws Exception {
            socket = new Socket("127.0.0.1", port);
            if (receiveBuffer > 0) {
                socket.setReceiveBufferSize(receiveBuffer);
            }
            socket.setSoTimeout(5000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        public void send(String request) throws Exception {
            output.write(request);
            output.newLine();
            output.flush();
        }

        public String reply() throws Exception {
            String line;
            while ((line = input.readLine()) != null) {
                if (!line.startsWith("0\tUPDATE\t")) {
                    return line;
                }
            }
            throw new AssertionError("The connection closed before its response arrived.");
        }

        public void resumeReading() throws Exception {
            socket.setReceiveBufferSize(262144);
        }

        public void close() throws Exception {
            socket.close();
        }
    }
}
