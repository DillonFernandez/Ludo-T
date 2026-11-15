// Tests tier isolation, SQL persistence, state transfer and authoritative client displays.
package ludot.gui;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import ludot.support.ChildProcess;
import ludot.support.IsolatedClasses;
import ludot.server.ServerFixture;
import ludot.support.TierProcesses;
import ludot.application.GameService;
import ludot.application.GameSnapshot;
import ludot.client.GameClient;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.persistence.JdbcGameRepository;
import ludot.random.RandomNumbers;
import ludot.support.RecordedOutput;
import ludot.support.ScriptedRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class ArchitectureTest {
    @TempDir
    Path directory;

    // An isolated core completes a game, and stepped play matches continuous play
    // with start and finish guards.
    @Test
    void gameLogicRunsWithoutTheGui() throws Exception {
        Path core = IsolatedClasses.create(directory.resolve("core.jar"), false);
        try (ChildProcess process = new ChildProcess(core.toString(), "ludot.game.ProcessCore",
                new String[] { "game" })) {
            assertTrue(process.awaitLine("CORE_GAME_OK ").matches("CORE_GAME_OK [1-9][0-9]*"));
            process.awaitSuccess();
        }
        RecordedOutput original = new RecordedOutput();
        RecordedOutput stepped = new RecordedOutput();
        LudoGame first = new LudoGameFactory().createGame(new RandomNumbers(1234), original);
        LudoGame second = new LudoGameFactory().createGame(new RandomNumbers(1234), stepped);
        first.play();
        assertThrows(IllegalStateException.class, second::playNextRound);
        second.start();
        assertThrows(IllegalStateException.class, second::start);
        while (!second.isFinished()) {
            second.playNextRound();
        }
        assertEquals(original.lines, stepped.lines);
        assertEquals(first.getCompletedRounds(), second.getCompletedRounds());
        int lines = stepped.lines.size();
        second.playNextRound();
        assertEquals(lines, stepped.lines.size());
    }

    // An isolated core executes capture rules successfully without network or
    // persistence classes.
    @Test
    void coreRulesRunWithoutNetworkingOrPersistence() throws Exception {
        Path core = IsolatedClasses.create(directory.resolve("rules.jar"), false);
        try (ChildProcess process = new ChildProcess(core.toString(), "ludot.game.ProcessCore",
                new String[] { "rules" })) {
            assertEquals("CORE_RULES_OK", process.awaitLine("CORE_RULES_OK"));
            process.awaitSuccess();
        }
    }

    // A separate client JVM reads READY state while its distinct server process
    // remains alive.
    @Test
    void clientRunsIndependentlyOfTheServerProcess() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused");
                ChildProcess client = new ChildProcess("ludot.client.ProcessClient", Integer.toString(tiers.port))) {
            long clientId = Long.parseLong(client.awaitLine("READY ").split(" ")[1]);
            assertEquals(client.process.pid(), clientId);
            assertNotEquals(tiers.server.process.pid(), clientId);
            assertNotEquals(ProcessHandle.current().pid(), clientId);
            client.send("STATUS");
            assertEquals("READY", GameSnapshot.decode(client.awaitLine("STATE ").substring(6)).getStatus());
            assertTrue(tiers.server.process.isAlive());
        }
    }

    // The production server runs separately from the test and database processes
    // and answers STATUS.
    @Test
    void serverRunsAsAnIndependentApplication() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "production");
                GameClient client = new GameClient("127.0.0.1", tiers.port, update -> {
                }, reason -> {
                })) {
            assertTrue(tiers.server.process.isAlive());
            assertNotEquals(ProcessHandle.current().pid(), tiers.server.process.pid());
            assertNotEquals(tiers.database.process.pid(), tiers.server.process.pid());
            assertEquals("READY", client.request("STATUS").get(5, TimeUnit.SECONDS).getStatus());
        }
    }

    // The database process retains the saved game after the separate server process
    // stops.
    @Test
    void databaseTierOperatesIndependently() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused");
                GameClient client = new GameClient("127.0.0.1", tiers.port, update -> {
                }, reason -> {
                })) {
            GameSnapshot saved = client.request("START").get(5, TimeUnit.SECONDS);
            tiers.server.close();
            assertFalse(tiers.server.process.isAlive());
            assertTrue(tiers.database.process.isAlive());
            assertNotEquals(ProcessHandle.current().pid(), tiers.database.process.pid());
            try (JdbcGameRepository database = new JdbcGameRepository(tiers.databaseUrl, "sa", "")) {
                ServerFixture.sameState(saved, database.loadLatest());
            }
        }
    }

    // A restricted client JAR connects successfully with persistence and H2 driver
    // classes excluded.
    @Test
    void clientWorksWithoutDirectDatabaseAccess() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused")) {
            Path clientJar = IsolatedClasses.create(directory.resolve("client.jar"), true);
            try (ChildProcess client = new ChildProcess(clientJar.toString(), "ludot.client.ProcessClient",
                    new String[] { Integer.toString(tiers.port), "boundary" })) {
                assertTrue(client.awaitLine("BOUNDARY_OK ").endsWith(" READY"));
                client.awaitSuccess();
            }
        }
    }

    // Saved snapshots and message counts match, and a failed transaction rolls back
    // before a valid retry.
    @Test
    void serverStoresAndRetrievesGameStateAccurately() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused");
                GameClient client = new GameClient("127.0.0.1", tiers.port, update -> {
                }, reason -> {
                });
                JdbcGameRepository database = new JdbcGameRepository(tiers.databaseUrl, "sa", "")) {
            assertNull(database.loadLatest());
            GameSnapshot saved = client.request("START").get(5, TimeUnit.SECONDS);
            ServerFixture.sameState(saved, database.loadLatest());
            try (var connection = DriverManager.getConnection(tiers.databaseUrl, "sa", "");
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM game_messages")) {
                assertTrue(rows.next());
                assertEquals(saved.getLog().split("\n").length, rows.getInt(1));
            }
            GameSnapshot unsaved = new GameSnapshot(saved.getRevision() + 1, saved.getSession(),
                    1, "RUNNING", 19, saved.getLocations(), "unsaved");
            assertThrows(SQLException.class, () -> database.save(unsaved, Arrays.asList("unsaved", null)));
            ServerFixture.sameState(saved, database.loadLatest());
            database.save(unsaved, List.of("valid retry"));
            ServerFixture.sameState(unsaved, database.loadLatest());
        }
    }

    // Snapshots preserve text and copied locations through SQL and encoding, with
    // restart recovery and validation.
    @Test
    void dataRemainsCorrectAcrossTierBoundaries() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused");
                JdbcGameRepository database = new JdbcGameRepository(tiers.databaseUrl, "sa", "")) {
            int[] positions = { -1, 0, 52, 57, 39, 51, 53, -1, 1, 2, 3, 4, 5, 6, 7, 8 };
            GameSnapshot original = new GameSnapshot(20, "transfer", 15, "RUNNING", 19,
                    positions, "£ café\tFirst line\nSecond line");
            positions[0] = 20;
            original.getLocations()[1] = 30;
            database.save(original, List.of(original.getLog()));
            try (JdbcGameRepository otherConnection = new JdbcGameRepository(tiers.databaseUrl, "sa", "")) {
                ServerFixture.sameState(original, GameSnapshot.decode(otherConnection.loadLatest().encode()));
                assertEquals(-1, otherConnection.loadLatest().getLocations()[0]);
                GameService recovered = new GameService(otherConnection);
                assertEquals("INTERRUPTED", recovered.getSnapshot().getStatus());
                assertArrayEquals(original.getLocations(), recovered.getSnapshot().getLocations());
                assertEquals(original.getRound(), recovered.getSnapshot().getRound());
                assertFalse(recovered.advance());
                assertNotEquals(original.getSession(), recovered.start().getSession());
                assertEquals(0, recovered.getSnapshot().getRound());
            }
            assertThrows(IllegalArgumentException.class, () -> GameSnapshot.decode("broken"));
            assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(1, "transfer", 10001,
                    "RUNNING", -1, positions, ""));
            positions[0] = 58;
            assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(1, "transfer", 1,
                    "RUNNING", -1, positions, ""));
        }
    }

    // Two distinct client processes join the same session and receive identical
    // running state.
    @Test
    void independentClientProcessesConnectToTheSameServer() throws Exception {
        try (TierProcesses tiers = new TierProcesses(directory, "paused");
                ChildProcess first = new ChildProcess("ludot.client.ProcessClient", Integer.toString(tiers.port));
                ChildProcess second = new ChildProcess("ludot.client.ProcessClient", Integer.toString(tiers.port))) {
            String[] readyA = first.awaitLine("READY ").split(" ");
            String[] readyB = second.awaitLine("READY ").split(" ");
            assertNotEquals(readyA[1], readyB[1]);
            assertEquals(readyA[2], readyB[2]);
            first.send("START");
            second.send("START");
            GameSnapshot stateA = GameSnapshot.decode(first.awaitLine("STATE ").substring(6));
            GameSnapshot stateB = GameSnapshot.decode(second.awaitLine("STATE ").substring(6));
            ServerFixture.sameState(stateA, stateB);
            assertTrue(stateA.isRunning());
        }
    }

    // The window displays every saved message and renders the authoritative final
    // board at the round limit.
    @Test
    void clientsDisplayAuthoritativeServerState() throws Exception {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 4, 3, 2);
        LinkedBlockingQueue<GameSnapshot> updates = new LinkedBlockingQueue<>();
        try (ServerFixture server = new ServerFixture(false, 1, 100, random)) {
            GameClient observer = server.client(updates::add);
            try (WindowFixture view = new WindowFixture(server.server.getPort(), true)) {
                GameSnapshot state;
                do {
                    state = updates.poll(10, TimeUnit.SECONDS);
                    assertNotNull(state);
                } while (state.isRunning());
                GameSnapshot authoritative = observer.request("STATUS").get(5, TimeUnit.SECONDS);
                view.awaitEnding(authoritative.getLog());
                String[] displayed = view.text().split("\n", -1);
                try (var connection = DriverManager.getConnection(server.databaseUrl, "sa", "");
                        var statement = connection.prepareStatement(
                                "SELECT message FROM game_messages WHERE session_id = ? ORDER BY message_id")) {
                    statement.setString(1, authoritative.getSession());
                    try (var rows = statement.executeQuery()) {
                        int line = 0;
                        while (rows.next()) {
                            assertTrue(line < displayed.length);
                            assertEquals(rows.getString(1), displayed[line++]);
                        }
                        assertEquals(line, displayed.length);
                        assertTrue(line > 600);
                    }
                }
                BufferedImage actual = view.image(750);
                BufferedImage expected = new BufferedImage(750, 750, BufferedImage.TYPE_INT_RGB);
                SwingUtilities.invokeAndWait(() -> {
                    BoardPanel canonical = new BoardPanel();
                    canonical.setSize(750, 750);
                    canonical.setSnapshot(authoritative);
                    var graphics = expected.createGraphics();
                    try {
                        canonical.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                });
                assertArrayEquals(expected.getRGB(0, 0, 750, 750, null, 0, 750),
                        actual.getRGB(0, 0, 750, 750, null, 0, 750));
                assertEquals(10_000, authoritative.getRound());
            }
        }
    }
}
