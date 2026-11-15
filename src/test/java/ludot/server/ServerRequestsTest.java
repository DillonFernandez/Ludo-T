// Tests server request handling, concurrent access, queue draining and invalid-message isolation.
package ludot.server;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import ludot.application.GameSnapshot;
import ludot.client.GameClient;
import ludot.network.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ServerRequestsTest {
    // STATUS returns initial READY state at round and revision zero with all
    // sixteen pieces at Base.
    @Test
    void oneClientRequestSucceeds() throws Exception {
        try (ServerFixture fixture = new ServerFixture()) {
            GameSnapshot response = fixture.client().request("STATUS").get(5, TimeUnit.SECONDS);
            assertEquals("READY", response.getStatus());
            assertEquals(0, response.getRound());
            assertEquals(0, response.getRevision());
            assertEquals(16, response.getLocations().length);
            for (int location : response.getLocations()) {
                assertEquals(-1, location);
            }
        }
    }

    // START advances the revision, and twenty later STATUS requests return the same
    // running state.
    @Test
    void sequentialRequestsReceiveCorrectResponses() throws Exception {
        try (ServerFixture fixture = new ServerFixture()) {
            GameClient client = fixture.client();
            GameSnapshot ready = client.request("STATUS").get(5, TimeUnit.SECONDS);
            GameSnapshot started = client.request("START").get(5, TimeUnit.SECONDS);
            assertTrue(started.isRunning());
            assertTrue(started.getRevision() > ready.getRevision());
            for (int request = 0; request < 20; request++) {
                ServerFixture.sameState(started, client.request("STATUS").get(5, TimeUnit.SECONDS));
            }
        }
    }

    // New clients retrieve the same running state after the client that started the
    // game disconnects.
    @Test
    void multipleClientsWorkSequentially() throws Exception {
        try (ServerFixture fixture = new ServerFixture()) {
            GameClient first = fixture.client();
            GameSnapshot started = first.request("START").get(5, TimeUnit.SECONDS);
            first.close();
            for (int index = 0; index < 4; index++) {
                GameClient next = fixture.client();
                ServerFixture.sameState(started, next.request("STATUS").get(5, TimeUnit.SECONDS));
                next.close();
            }
        }
    }

    // Eight simultaneous START requests receive identical state and trigger only
    // one repository save.
    @Test
    void simultaneousRequestsExecuteCorrectly() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            List<GameClient> clients = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                clients.add(fixture.client());
            }
            ExecutorService threads = Executors.newFixedThreadPool(8);
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<CompletableFuture<GameSnapshot>> replies = new ArrayList<>();
                for (GameClient client : clients) {
                    replies.add(CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            assertTrue(start.await(5, TimeUnit.SECONDS));
                            return client.request("START").get(10, TimeUnit.SECONDS);
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    }, threads));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
                ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 7);
                fixture.repository.release.countDown();
                CompletableFuture.allOf(replies.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);
                for (var reply : replies) {
                    ServerFixture.sameState(replies.get(0).join(), reply.join());
                }
                assertEquals(1, fixture.repository.saves.get());
            } finally {
                fixture.repository.release.countDown();
                threads.shutdownNow();
                assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    // One hundred STATUS requests remain queued behind a blocked START and complete
    // after release.
    @Test
    void excessRequestsEnterARealQueue() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient first = fixture.client();
            GameClient second = fixture.client();
            var pendingStart = fixture.beginBlocked(first);
            List<CompletableFuture<GameSnapshot>> requests = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                requests.add(second.request("STATUS"));
            }
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 100);
            assertFalse(pendingStart.isDone());
            assertTrue(requests.stream().noneMatch(CompletableFuture::isDone));
            fixture.repository.release.countDown();
            CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);
        }
    }

    // Eighty queued STATUS requests return the started state and leave the server
    // queue empty.
    @Test
    void queuedRequestsEventuallyFinish() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient client = fixture.client();
            var pendingStart = fixture.beginBlocked(client);
            List<CompletableFuture<GameSnapshot>> requests = new ArrayList<>();
            for (int index = 0; index < 80; index++) {
                requests.add(client.request("STATUS"));
            }
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 80);
            fixture.repository.release.countDown();
            GameSnapshot expected = pendingStart.get(5, TimeUnit.SECONDS);
            for (var request : requests) {
                ServerFixture.sameState(expected, request.get(5, TimeUnit.SECONDS));
            }
            client.request("STATUS").get(5, TimeUnit.SECONDS);
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 0);
        }
    }

    // Concurrent starts save one session, and later read bursts preserve consistent
    // advancing state in that session.
    @Test
    void concurrentStartsJoinOneGameWithoutOverwritingItsState() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient first = fixture.client();
            GameClient second = fixture.client();
            List<CompletableFuture<GameSnapshot>> starts = new ArrayList<>();
            starts.add(fixture.beginBlocked(first));
            for (int index = 0; index < 64; index++) {
                starts.add((index % 2 == 0 ? first : second).request("START"));
            }
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 64);
            fixture.repository.release.countDown();
            CompletableFuture.allOf(starts.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);
            GameSnapshot expected = starts.get(0).join();
            for (var start : starts) {
                ServerFixture.sameState(expected, start.join());
            }
            assertEquals(1, fixture.repository.saves.get());
            try (var connection = DriverManager.getConnection(fixture.databaseUrl, "sa", "");
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM simulations")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
        advancingBursts();
    }

    private void advancingBursts() throws Exception {
        try (ServerFixture fixture = new ServerFixture(false, 5, 1, new ludot.random.RandomNumbers(1234))) {
            java.util.concurrent.LinkedBlockingQueue<GameSnapshot> updates = new java.util.concurrent.LinkedBlockingQueue<>();
            GameClient first = fixture.client(updates::add);
            GameClient second = fixture.client();
            GameSnapshot started = first.request("START").get(5, TimeUnit.SECONDS);
            long revision = started.getRevision();
            int round = 0;
            for (int burst = 0; burst < 4; burst++) {
                List<CompletableFuture<GameSnapshot>> reads = new ArrayList<>();
                for (int request = 0; request < 100; request++) {
                    reads.add(first.request("STATUS"));
                    reads.add(second.request("STATUS"));
                }
                GameSnapshot next;
                do {
                    next = updates.poll(5, TimeUnit.SECONDS);
                    assertNotNull(next);
                } while (next.getRound() <= round);
                assertTrue(next.getRevision() > revision);
                assertEquals(started.getSession(), next.getSession());
                round = next.getRound();
                revision = next.getRevision();
                for (CompletableFuture<GameSnapshot> reply : reads) {
                    GameSnapshot state = reply.get(5, TimeUnit.SECONDS);
                    assertEquals(started.getSession(), state.getSession());
                    assertEquals(state.getRound() + 1, state.getRevision());
                    assertTrue(state.getLog().contains("Round " + state.getRound()) || state.getRound() == 0);
                }
            }
            assertTrue(fixture.repository.saves.get() >= 5);
            try (var connection = DriverManager.getConnection(fixture.databaseUrl, "sa", "");
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM simulations")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    // Concurrent reads preserve the session, valid locations and separation of
    // opposing pieces on standard cells.
    @Test
    void sharedStateRemainsConsistentDuringConcurrentReads() throws Exception {
        try (ServerFixture fixture = new ServerFixture(false, 1, 100, new ludot.random.RandomNumbers(1234))) {
            GameClient first = fixture.client();
            GameClient second = fixture.client();
            GameSnapshot started = first.request("START").get(5, TimeUnit.SECONDS);
            List<CompletableFuture<GameSnapshot>> reads = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                reads.add(first.request("STATUS"));
                reads.add(second.request("STATUS"));
            }
            for (var read : reads) {
                GameSnapshot state = read.get(5, TimeUnit.SECONDS);
                assertEquals(started.getSession(), state.getSession());
                assertTrue(state.getRevision() >= started.getRevision());
                assertTrue(state.getRound() >= 0 && state.getRound() <= 10_000);
                int[] locations = state.getLocations();
                assertEquals(16, locations.length);
                for (int index = 0; index < 16; index++) {
                    assertTrue(locations[index] >= -1 && locations[index] <= 57);
                    for (int other = index + 1; other < 16; other++) {
                        if (locations[index] >= 0 && locations[index] < 52 && index / 4 != other / 4) {
                            assertNotEquals(locations[index], locations[other]);
                        }
                    }
                }
            }
        }
    }

    // Each client receives every distinct request ID once, with command results
    // kept separate across connections.
    @Test
    void responsesReachTheirCorrectClients() throws Exception {
        try (ServerFixture fixture = new ServerFixture();
                ServerFixture.RawClient first = new ServerFixture.RawClient(fixture.server.getPort());
                ServerFixture.RawClient second = new ServerFixture.RawClient(fixture.server.getPort())) {
            for (int index = 0; index < 50; index++) {
                first.send((1001 + index) + "\tSTATUS");
                second.send((2001 + index) + "\tSTATUS");
            }
            Set<Long> firstIds = new HashSet<>();
            Set<Long> secondIds = new HashSet<>();
            for (int index = 0; index < 50; index++) {
                String[] a = first.reply().split("\t", 3);
                String[] b = second.reply().split("\t", 3);
                assertEquals("OK", a[1]);
                assertEquals("OK", b[1]);
                assertTrue(firstIds.add(Long.parseLong(a[0])));
                assertTrue(secondIds.add(Long.parseLong(b[0])));
            }
            for (long id = 1001; id <= 1050; id++) {
                assertTrue(firstIds.contains(id));
            }
            for (long id = 2001; id <= 2050; id++) {
                assertTrue(secondIds.contains(id));
            }
            first.send("777\tUNKNOWN");
            second.send("777\tSTATUS");
            assertTrue(first.reply().startsWith("777\tERROR\t"));
            assertTrue(second.reply().startsWith("777\tOK\t"));
        }
    }

    // Malformed and unknown requests return errors without blocking valid reads;
    // oversized or unfinished lines fail.
    @Test
    void invalidRequestsDoNotPreventValidRequests() throws Exception {
        try (ServerFixture fixture = new ServerFixture();
                ServerFixture.RawClient invalid = new ServerFixture.RawClient(fixture.server.getPort())) {
            GameClient healthy = fixture.client();
            invalid.send("bad request");
            assertTrue(invalid.reply().startsWith("0\tERROR\t"));
            invalid.send("1\tUNKNOWN");
            assertEquals("Unknown request.", Messages.decodeText(invalid.reply().split("\t", 3)[2]));
            assertEquals("READY", healthy.request("STATUS").get(5, TimeUnit.SECONDS).getStatus());
            invalid.send("2\tSTATUS");
            assertTrue(invalid.reply().startsWith("2\tOK\t"));
            assertThrows(java.io.IOException.class, () -> Messages.readLine(new java.io.StringReader("12345\n"), 4));
            assertThrows(java.io.IOException.class,
                    () -> Messages.readLine(new java.io.StringReader("unfinished"), 100));
        }
    }
}
