// Tests concurrent request bursts, reply matching, queue handling and complete tiered games.
package ludot.client;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ludot.application.GameService;
import ludot.application.GameSnapshot;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.persistence.JdbcGameRepository;
import ludot.random.RandomNumbers;
import ludot.state.BriefingState;
import ludot.support.RecordedOutput;
import ludot.support.ScriptedRandom;
import ludot.server.ServerFixture;
import ludot.support.ChildProcess;
import ludot.support.TierProcesses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class AutomaticClientsTest {
    @TempDir
    Path directory;

    // Two separate client JVMs each complete 200 outstanding requests against the
    // same game session.
    @Test
    void independentAutomaticProcesses() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked();
                ChildProcess first = new ChildProcess("ludot.client.ProcessClient", "" + fixture.server.getPort());
                ChildProcess second = new ChildProcess("ludot.client.ProcessClient", "" + fixture.server.getPort())) {
            first.awaitLine("READY ");
            second.awaitLine("READY ");
            assertNotEquals(first.process.pid(), second.process.pid());
            assertNotEquals(ProcessHandle.current().pid(), first.process.pid());
            first.send("BURST 200");
            second.send("BURST 200");
            assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 399);
            fixture.repository.release.countDown();
            String[] a = first.awaitLine("BURST_DONE ").split(" ");
            String[] b = second.awaitLine("BURST_DONE ").split(" ");
            assertEquals("200", a[1]);
            assertEquals("200", b[1]);
            assertEquals("200", a[2]);
            assertEquals("200", b[2]);
            assertEquals(a[3], b[3]);
            first.send("EXIT");
            second.send("EXIT");
            first.awaitSuccess();
            second.awaitSuccess();
        }
    }

    // Two clients submit twenty requests each while the first save is blocked and
    // both bursts complete after release.
    @Test
    void simultaneousStart() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient first = fixture.client();
            GameClient second = fixture.client();
            CountDownLatch start = new CountDownLatch(1);
            var a = AutomaticClients.sendBurst(first, 20, start);
            var b = AutomaticClients.sendBurst(second, 20, start);
            assertEquals(1, fixture.repository.entered.getCount());
            start.countDown();
            assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 39);
            assertFalse(a.isDone());
            assertFalse(b.isDone());
            fixture.repository.release.countDown();
            var resultA = a.get(5, TimeUnit.SECONDS);
            var resultB = b.get(5, TimeUnit.SECONDS);
            assertEquals(20, resultA.getCompletedRequests());
            assertEquals(20, resultB.getCompletedRequests());
            assertTrue(resultA.getSubmittedAt() >= resultA.getStartedAt());
            assertTrue(resultB.getSubmittedAt() >= resultB.getStartedAt());
        }
    }

    // Each client keeps all 100 burst requests outstanding until the blocked save
    // is released.
    @Test
    void asynchronousBursts() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            var results = blockedBursts(fixture, 100);
            for (var result : results) {
                assertEquals(100, result.getCompletedRequests());
                assertEquals(100, result.getMaximumOutstanding());
            }
        }
    }

    // Each client submits 250 outstanding requests within five seconds while the
    // first save is blocked.
    @Test
    void rapidSuccessiveRequests() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            var results = blockedBursts(fixture, 250);
            for (var result : results) {
                assertEquals(250, result.getMaximumOutstanding());
                assertTrue(result.getSubmittedAt() - result.getStartedAt() < TimeUnit.SECONDS.toNanos(5));
            }
        }
    }

    // Two 500-request bursts complete all 1,000 requests with only one game-start
    // save.
    @Test
    void loadQueues() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            var results = blockedBursts(fixture, 500);
            assertEquals(1000, results[0].getCompletedRequests() + results[1].getCompletedRequests());
            assertEquals(1, fixture.repository.saves.get());
        }
    }

    // Queues drain for both clients, and a slow reader receives ordered replies
    // without blocking a healthy client.
    @Test
    void acceptedQueueDrains() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            var results = blockedBursts(fixture, 200);
            assertEquals(200, results[0].getCompletedRequests());
            assertEquals(200, results[1].getCompletedRequests());
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 0);
            assertEquals(results[0].getSession(), results[1].getSession());
        }
        try (ServerFixture fixture = new ServerFixture()) {
            fixture.service.start();
            fixture.service.advance(20);
            try (ServerFixture.RawClient slow = new ServerFixture.RawClient(fixture.server.getPort(), 1024)) {
                for (int id = 1; id <= 2000; id++) {
                    slow.send(id + "\tSTATUS");
                }
                GameClient healthy = fixture.client();
                assertTrue(healthy.request("STATUS").get(5, TimeUnit.SECONDS).isRunning());
                ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 0);
                slow.resumeReading();
                for (int id = 1; id <= 2000; id++) {
                    String[] reply = slow.reply().split("\t", 3);
                    assertEquals(Integer.toString(id), reply[0]);
                    assertEquals("OK", reply[1]);
                    assertEquals(20, GameSnapshot.decode(reply[2]).getRound());
                }
                slow.send("2000\tSTATUS");
                assertTrue(slow.reply().startsWith("2000\tOK\t"));
                slow.send("1\tSTART");
                assertTrue(slow.reply().startsWith("1\tERROR\t"));
                assertEquals(2, fixture.repository.saves.get());
            }
        }
    }

    private AutomaticClients.BurstResult[] blockedBursts(ServerFixture fixture, int count) throws Exception {
        GameClient first = fixture.client();
        GameClient second = fixture.client();
        CountDownLatch start = new CountDownLatch(1);
        var a = AutomaticClients.sendBurst(first, count, start);
        var b = AutomaticClients.sendBurst(second, count, start);
        start.countDown();
        assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
        ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 2 * count - 1);
        assertFalse(a.isDone());
        assertFalse(b.isDone());
        fixture.repository.release.countDown();
        return new AutomaticClients.BurstResult[] {
                a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)
        };
    }

    // Replies cover every request ID; retrying START reuses its reply and changing
    // its command is rejected.
    @Test
    void exactlyOnceProcessing() throws Exception {
        try (ServerFixture fixture = new ServerFixture();
                ServerFixture.RawClient client = new ServerFixture.RawClient(fixture.server.getPort())) {
            client.send("1\tSTART");
            String original = client.reply();
            Set<Long> received = new HashSet<>();
            for (long id = 2; id <= 201; id++) {
                client.send(id + "\tSTATUS");
            }
            for (int i = 0; i < 200; i++) {
                String[] parts = client.reply().split("\t", 3);
                assertTrue(received.add(Long.parseLong(parts[0])));
                assertEquals("OK", parts[1]);
            }
            for (long id = 2; id <= 201; id++) {
                assertTrue(received.contains(id));
            }
            client.send("1\tSTART");
            assertEquals(original, client.reply());
            client.send("1\tSTATUS");
            assertTrue(client.reply().startsWith("1\tERROR\t"));
            assertEquals(1, fixture.repository.saves.get());
            try (var connection = DriverManager.getConnection(fixture.databaseUrl, "sa", "");
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM simulations")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    // Clients receive their own request IDs and the correct success or error for
    // alternating commands.
    @Test
    void matchingResponses() throws Exception {
        try (ServerFixture fixture = new ServerFixture();
                ServerFixture.RawClient first = new ServerFixture.RawClient(fixture.server.getPort());
                ServerFixture.RawClient second = new ServerFixture.RawClient(fixture.server.getPort())) {
            for (int id = 1; id <= 100; id++) {
                first.send(id + (id % 2 == 0 ? "\tSTATUS" : "\tINVALID"));
                second.send(id + (id % 2 == 0 ? "\tINVALID" : "\tSTATUS"));
            }
            for (int id = 1; id <= 100; id++) {
                String[] a = first.reply().split("\t", 3);
                String[] b = second.reply().split("\t", 3);
                assertEquals("" + id, a[0]);
                assertEquals("" + id, b[0]);
                assertEquals(id % 2 == 0 ? "OK" : "ERROR", a[1]);
                assertEquals(id % 2 == 0 ? "ERROR" : "OK", b[1]);
                assertEquals("READY", GameSnapshot.decode(id % 2 == 0 ? a[2] : b[2]).getStatus());
            }
        }
    }

    // Repeated client bursts share one session while pushed rounds and response
    // revisions continue to advance.
    @Test
    void repeatedBursts() throws Exception {
        try (ServerFixture fixture = new ServerFixture(false, 5, 1, new RandomNumbers(1234))) {
            java.util.concurrent.LinkedBlockingQueue<GameSnapshot> updates = new java.util.concurrent.LinkedBlockingQueue<>();
            GameClient first = fixture.client(updates::add);
            GameClient second = fixture.client();
            String session = null;
            int previousRound = 0;
            for (int burst = 0; burst < 4; burst++) {
                CountDownLatch start = new CountDownLatch(1);
                var a = AutomaticClients.sendBurst(first, 100, start);
                var b = AutomaticClients.sendBurst(second, 100, start);
                start.countDown();
                var resultA = a.get(10, TimeUnit.SECONDS);
                var resultB = b.get(10, TimeUnit.SECONDS);
                assertEquals(100, resultA.getCompletedRequests());
                assertEquals(100, resultB.getCompletedRequests());
                assertEquals(resultA.getSession(), resultB.getSession());
                if (session != null) {
                    assertEquals(session, resultA.getSession());
                }
                session = resultA.getSession();
                GameSnapshot next;
                do {
                    next = updates.poll(5, TimeUnit.SECONDS);
                    assertNotNull(next);
                } while (next.getRound() <= previousRound);
                previousRound = next.getRound();
                assertEquals(session, next.getSession());
                assertEquals(previousRound + 1, next.getRevision());
                GameSnapshot aState = first.request("STATUS").get(5, TimeUnit.SECONDS);
                GameSnapshot bState = second.request("STATUS").get(5, TimeUnit.SECONDS);
                assertEquals(aState.getSession(), bState.getSession());
                assertTrue(bState.getRevision() >= aState.getRevision());
                assertEquals(aState.getRound() + 1, aState.getRevision());
                assertEquals(bState.getRound() + 1, bState.getRevision());
            }
            assertTrue(fixture.repository.saves.get() >= 5);
        }
    }

    // Separate tiers preserve winning and round-limit results, including a
    // final-round win and equivalent batched play.
    @Test
    void completeMultiTierExecution() throws Exception {
        String ending = "The game ran 10,000 rounds without a winner, so the simulation has ended.";
        for (String scenario : new String[] { "limit", "winner" }) {
            try (TierProcesses tiers = new TierProcesses(directory.resolve(scenario), scenario);
                    ChildProcess client = new ChildProcess("ludot.client.ProcessClient", "" + tiers.port)) {
                client.awaitLine("READY ");
                client.send("START");
                assertTrue(GameSnapshot.decode(client.awaitLine("STATE ").substring(6)).isRunning());
                client.send("WAIT_FINISHED");
                GameSnapshot finished = GameSnapshot.decode(client.awaitLine("FINISHED ").substring(9));
                assertEquals("FINISHED", finished.getStatus());
                try (JdbcGameRepository repository = new JdbcGameRepository(tiers.databaseUrl, "sa", "")) {
                    ServerFixture.sameState(finished, repository.loadLatest());
                }
                if (scenario.equals("limit")) {
                    assertEquals(10_000, finished.getRound());
                    assertTrue(finished.getLog().endsWith(ending));
                    assertEquals(600, finished.getLog().split("\n").length);
                    for (int location : finished.getLocations()) {
                        assertEquals(-1, location);
                    }
                    try (var connection = DriverManager.getConnection(tiers.databaseUrl, "sa", "");
                            var statement = connection.createStatement()) {
                        try (var rows = statement.executeQuery("SELECT message FROM game_messages")) {
                            int rolls = 0;
                            int endings = 0;
                            int rounds = 0;
                            while (rows.next()) {
                                String message = rows.getString(1);
                                if (message.contains(" player rolled ")) {
                                    rolls++;
                                }
                                if (message.equals(ending)) {
                                    endings++;
                                }
                                if (message.contains("Round ")) {
                                    rounds++;
                                }
                                assertFalse(message.contains("Round 10001"));
                            }
                            assertEquals(40_000, rolls);
                            assertEquals(1, endings);
                            assertEquals(10_000, rounds);
                        }
                    }
                } else {
                    RecordedOutput output = new RecordedOutput();
                    LudoGame original = new LudoGameFactory().createGame(new RandomNumbers(1234), output);
                    original.play();
                    assertEquals(original.getCompletedRounds(), finished.getRound());
                    assertTrue(finished.getLog().endsWith(output.lines.get(output.lines.size() - 1)));
                    assertTrue(finished.getLog().contains("wins!!!"));
                    assertFalse(finished.getLog().contains("without a winner"));
                    ServerFixture.sameState(GameSnapshot.capture(finished.getRevision(), finished.getSession(),
                            "FINISHED", original, finished.getLog()), finished);
                }
                client.send("EXIT");
                client.awaitSuccess();
            }
        }
        lastRoundWinner();
        batchEquivalence();
    }

    private void lastRoundWinner() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 4, 3, 2);
        RecordedOutput output = new RecordedOutput();
        LudoGame game = new LudoGameFactory().createGame(random, output);
        Player blue = game.getBoard().getPlayers()[3];
        for (Piece piece : blue.getPieces()) {
            piece.moveTo(57, 0);
        }
        Piece last = blue.getPieces()[0];
        last.moveTo(56, 0);
        last.applyState(new BriefingState(), 9995);
        game.play();
        assertEquals(10_000, game.getCompletedRounds());
        assertEquals("blue player wins!!!", output.lines.get(output.lines.size() - 1));
        assertFalse(output.text().contains("without a winner"));
    }

    private void batchEquivalence() throws Exception {
        try (JdbcGameRepository first = new JdbcGameRepository("jdbc:h2:mem:single", "sa", "");
                JdbcGameRepository second = new JdbcGameRepository("jdbc:h2:mem:batch", "sa", "")) {
            GameService single = new GameService(first, new RandomNumbers(1234));
            GameService batched = new GameService(second, new RandomNumbers(1234));
            single.start();
            batched.start();
            while (single.advance()) {
            }
            while (batched.advance(100)) {
            }
            assertEquals(single.getSnapshot().getRound(), batched.getSnapshot().getRound());
            assertArrayEquals(single.getSnapshot().getLocations(), batched.getSnapshot().getLocations());
            assertEquals(single.getSnapshot().getLog(), batched.getSnapshot().getLog());
        }
    }
}
