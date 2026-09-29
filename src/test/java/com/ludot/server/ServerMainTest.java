package com.ludot.server;

import com.ludot.client.AutomaticTestClients;
import com.ludot.client.GameClient;
import com.ludot.model.Colour;
import com.ludot.support.IsolatedDatabase;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ServerMainTest {
    private static final int ASYNC_TIMEOUT_SECONDS = 5;
    private static final int CONTROLLED_RELEASE_TIMEOUT_SECONDS = 10;
    private static final long LISTENER_JOIN_TIMEOUT_MILLIS = 5_000L;
    private static final int REQUESTS_PER_PRODUCER = 5;
    private static final int PRODUCER_COUNT = 2;
    private static final int SIMULTANEOUS_REQUEST_COUNT = REQUESTS_PER_PRODUCER * PRODUCER_COUNT;
    private static final int AUTOMATIC_CLIENT_REQUEST_COUNT = 10;

    @Test
    void singleClientReceivesResetThenWinnerAndTheDatabaseRecordsTheResult() throws Exception {
        try (var server = new TestServer(logger -> logger.logWinner(Colour.RED));
             var client = server.connect()) {
            client.client.sendRequest("IGNORED_REQUEST");
            client.client.sendRequest("start_game");
            assertEquals("RESET_BOARD", client.next());
            assertEquals("Red player wins!!!", client.next());
            server.games.submit(() -> {}).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try (var statement = server.db.connection.createStatement();
                 var rows = statement.executeQuery("SELECT winner FROM game_results")) {
                assertTrue(rows.next());
                assertEquals("RED", rows.getString(1));
                assertFalse(rows.next());
            }
            assertEquals(1, server.runs.get());
        }
    }

    @Test
    void sequentialClientsReceiveTheSameLiveChanges() throws Exception {
        AtomicInteger game = new AtomicInteger();
        try (var server = new TestServer(logger -> logger.log("PIECE|R1|STANDARD_PATH|-|" + game.incrementAndGet()));
             var first = server.connect()) {
            first.client.sendRequest("START_GAME");
            assertEquals("RESET_BOARD", first.next());
            assertEquals("PIECE|R1|STANDARD_PATH|-|1", first.next());
            try (var second = server.connect()) {
                second.client.sendRequest("START_GAME");
                for (Connection client : List.of(first, second)) {
                    assertEquals("RESET_BOARD", client.next());
                    assertEquals("PIECE|R1|STANDARD_PATH|-|2", client.next());
                }
            }
        }
    }

    @Test
    void simultaneousProducersQueueEveryRequestWithoutOverlappingGames() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        try (var server = new TestServer(logger -> {
            int number = sequence.incrementAndGet();
            maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                logger.log("BEGIN|" + number);
                if (number == 1) {
                    firstStarted.countDown();
                    await(release);
                }
                logger.log("END|" + number);
            } finally {
                active.decrementAndGet();
            }
        }); var first = server.connect()) {
            ExecutorService producers = Executors.newVirtualThreadPerTaskExecutor();
            try {
                first.client.sendRequest("START_GAME");
                assertTrue(firstStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertEquals("RESET_BOARD", first.next());
                assertEquals("BEGIN|1", first.next());
                try (var second = server.connect()) {
                    CyclicBarrier together = new CyclicBarrier(PRODUCER_COUNT);
                    List<Future<?>> sent = new ArrayList<>();
                    for (Connection client : List.of(first, second)) {
                        sent.add(producers.submit(() -> {
                            together.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            for (int i = 0; i < REQUESTS_PER_PRODUCER; i++) {
                                client.client.sendRequest("START_GAME");
                            }
                            return null;
                        }));
                    }
                    for (Future<?> result : sent) {
                        result.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                    server.awaitSubmissions(SIMULTANEOUS_REQUEST_COUNT + 1);
                    assertEquals(SIMULTANEOUS_REQUEST_COUNT, server.games.getQueue().size(),
                            "All requests wait behind the blocked first game");
                    assertEquals(1, sequence.get());
                    release.countDown();
                    assertEquals("END|1", first.next());
                    assertEquals("END|1", second.next());
                    for (int number = 2; number <= SIMULTANEOUS_REQUEST_COUNT + 1; number++) {
                        for (Connection client : List.of(first, second)) {
                            assertEquals("RESET_BOARD", client.next());
                            assertEquals("BEGIN|" + number, client.next());
                            assertEquals("END|" + number, client.next());
                        }
                    }
                    server.games.submit(() -> {}).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    assertEquals(SIMULTANEOUS_REQUEST_COUNT + 1, server.runs.get());
                    assertEquals(1, maximumActive.get());
                    assertEquals(0, active.get());
                }
            } finally {
                release.countDown();
                producers.shutdownNow();
                assertTrue(producers.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    void automaticClientsQueueTenRequestsBeforeTheFirstGameCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        try (var server = new TestServer(logger -> {
            started.countDown();
            await(release);
            completed.incrementAndGet();
        })) {
            ExecutorService launcher = Executors.newVirtualThreadPerTaskExecutor();
            try {
                Future<?> clients = launcher.submit(() -> {
                    AutomaticTestClients.runClients(server.host(), server.port());
                    return null;
                });
                assertTrue(started.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                clients.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                server.awaitSubmissions(AUTOMATIC_CLIENT_REQUEST_COUNT);
                server.awaitDisconnected(PRODUCER_COUNT);
                assertEquals(PRODUCER_COUNT, server.accepted.get());
                assertEquals(AUTOMATIC_CLIENT_REQUEST_COUNT - 1, server.games.getQueue().size());
                assertEquals(0, completed.get(), "Clients must finish sending while processing is blocked");
                release.countDown();
                server.games.submit(() -> {}).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertEquals(AUTOMATIC_CLIENT_REQUEST_COUNT, completed.get());
                assertEquals(AUTOMATIC_CLIENT_REQUEST_COUNT, server.runs.get());
            } finally {
                release.countDown();
                launcher.shutdownNow();
                assertTrue(launcher.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    void disconnectedClientIsUnregisteredAndRemainingClientStillReceivesGames() throws Exception {
        try (var server = new TestServer(logger -> logger.log("DONE"));
             var first = server.connect()) {
            first.client.sendRequest("START_GAME");
            assertEquals("RESET_BOARD", first.next());
            assertEquals("DONE", first.next());
            first.close();
            server.awaitDisconnected(1);
            try (var second = server.connect()) {
                second.client.sendRequest("START_GAME");
                assertEquals("RESET_BOARD", second.next());
                assertEquals("DONE", second.next());
                server.games.submit(() -> {}).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                var field = SocketGameLogger.class.getDeclaredField("clients");
                field.setAccessible(true);
                assertEquals(1, ((List<?>) field.get(server.logger)).size());
                assertEquals(2, server.runs.get());
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(CONTROLLED_RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Timed out waiting for test-controlled release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class TestServer implements AutoCloseable {
        final IsolatedDatabase db = new IsolatedDatabase();
        final SocketGameLogger logger = new SocketGameLogger(db.repository);
        final BlockingQueue<Boolean> submissions = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> disconnected = new LinkedBlockingQueue<>();
        final ThreadPoolExecutor games = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()) {
            @Override public void execute(Runnable command) {
                super.execute(command);
                submissions.add(Thread.currentThread().isVirtual());
            }
        };
        final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final List<Future<?>> tasks = new CopyOnWriteArrayList<>();
        final AtomicInteger accepted = new AtomicInteger();
        final AtomicInteger runs = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final Future<?> acceptor;

        TestServer(Consumer<SocketGameLogger> simulation) throws Exception {
            acceptor = handlers.submit(() -> {
                try {
                    while (!listener.isClosed()) {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        accepted.incrementAndGet();
                        tasks.add(handlers.submit(() -> {
                            try {
                                ServerMain.handleClient(socket, logger, games, () -> {
                                    runs.incrementAndGet();
                                    try {
                                        simulation.accept(logger);
                                    } catch (Throwable e) {
                                        failure.compareAndSet(null, e);
                                        throw e;
                                    }
                                });
                            } finally {
                                disconnected.add(true);
                            }
                        }));
                    }
                } catch (IOException e) {
                    if (!listener.isClosed()) throw new UncheckedIOException(e);
                }
            });
        }

        String host() { return listener.getInetAddress().getHostAddress(); }
        int port() { return listener.getLocalPort(); }
        Connection connect() throws IOException { return new Connection(host(), port()); }
        void awaitSubmissions(int count) throws InterruptedException {
            for (int i = 0; i < count; i++) {
                assertEquals(Boolean.TRUE, submissions.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "Request must be submitted by a virtual client handler");
            }
        }
        void awaitDisconnected(int count) throws InterruptedException {
            for (int i = 0; i < count; i++) {
                assertNotNull(disconnected.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }

        @Override public void close() throws Exception {
            listener.close();
            for (Socket socket : sockets) socket.close();
            handlers.shutdown();
            games.shutdownNow();
            try {
                assertTrue(handlers.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertTrue(games.awaitTermination(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                acceptor.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                for (Future<?> task : tasks) {
                    task.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                assertNull(failure.get(), () -> "Simulation failed: " + failure.get());
            } finally {
                handlers.shutdownNow();
                db.close();
            }
        }
    }

    private static final class Connection implements AutoCloseable {
        final GameClient client;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final AtomicReference<Thread> listener = new AtomicReference<>();
        Connection(String host, int port) throws IOException {
            client = new GameClient(host, port);
            client.listenForResponses(message -> {
                listener.set(Thread.currentThread());
                messages.add(message);
            });
        }
        String next() throws InterruptedException {
            String message = messages.poll(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(message, "Timed out awaiting server response");
            return message;
        }
        @Override public void close() throws Exception {
            client.close();
            Thread thread = listener.get();
            if (thread != null) {
                thread.join(LISTENER_JOIN_TIMEOUT_MILLIS);
                assertFalse(thread.isAlive());
            }
        }
    }

}
