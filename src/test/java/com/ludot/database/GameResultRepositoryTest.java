package com.ludot.database;

import com.ludot.model.Colour;
import com.ludot.support.IsolatedDatabase;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GameResultRepositoryTest {
    private static final int WRITES_PER_COLOUR = 5;
    private static final int COORDINATION_TIMEOUT_SECONDS = 5;
    private static final int WRITE_COMPLETION_TIMEOUT_SECONDS = 10;

    @TempDir Path directory;

    @Test
    void schemaInitializationPreservesPreviouslySavedResults() throws Exception {
        try (var db = new IsolatedDatabase()) {
            db.repository.saveWinner(Colour.GREEN);
            new GameResultRepository(db.url).saveWinner(Colour.BLUE);
            try (var statement = db.connection.createStatement();
                 var rows = statement.executeQuery("SELECT winner FROM game_results ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals("GREEN", rows.getString(1));
                assertTrue(rows.next());
                assertEquals("BLUE", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void eachWinnerPersistsItsColourUniqueIdentityAndCompletionTime() throws Exception {
        try (var db = new IsolatedDatabase()) {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            for (Colour colour : Colour.values()) db.repository.saveWinner(colour);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);
            try (var statement = db.connection.createStatement();
                 var rows = statement.executeQuery("SELECT id, winner, completed_at FROM game_results ORDER BY id")) {
                long previous = 0;
                for (Colour colour : Colour.values()) {
                    assertTrue(rows.next());
                    assertTrue(rows.getLong("id") > previous);
                    previous = rows.getLong("id");
                    assertEquals(colour.name(), rows.getString("winner"));
                    LocalDateTime completed = rows.getTimestamp("completed_at").toLocalDateTime();
                    assertFalse(completed.isBefore(before));
                    assertFalse(completed.isAfter(after));
                }
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void concurrentSavesPersistEveryResultExactlyOnce() throws Exception {
        try (var db = new IsolatedDatabase()) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(Colour.values().length);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> writes = new ArrayList<>();
            try {
                for (Colour colour : Colour.values()) {
                    writes.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                        for (int i = 0; i < WRITES_PER_COLOUR; i++) {
                            db.repository.saveWinner(colour);
                        }
                        return null;
                    }));
                }
                assertTrue(ready.await(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                start.countDown();
                for (Future<?> write : writes) {
                    write.get(WRITE_COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                try (var statement = db.connection.createStatement();
                     var rows = statement.executeQuery("SELECT winner, COUNT(*) FROM game_results GROUP BY winner")) {
                    int colours = 0;
                    while (rows.next()) {
                        assertNotNull(Colour.valueOf(rows.getString(1)));
                        assertEquals(WRITES_PER_COLOUR, rows.getInt(2));
                        colours++;
                    }
                    assertEquals(Colour.values().length, colours);
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void tcpTierPersistsResultsThatSurviveServerShutdownAndReopening() throws Exception {
        String embedded = "jdbc:h2:" + directory.resolve("results").toAbsolutePath().toString().replace('\\', '/');
        try (var ignored = DriverManager.getConnection(embedded, "sa", "")) {
            // Create only the temporary database before allowing TCP connections.
        }
        Server server = Server.createTcpServer("-tcpPort", "0", "-baseDir", directory.toString()).start();
        try {
            String tcp = "jdbc:h2:tcp://localhost:" + server.getPort() + "/./results";
            new GameResultRepository(tcp).saveWinner(Colour.YELLOW);
            try (var connection = DriverManager.getConnection(tcp, "sa", "");
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT winner FROM game_results")) {
                assertTrue(rows.next());
                assertEquals("YELLOW", rows.getString(1));
                assertFalse(rows.next());
            }
        } finally {
            server.stop();
        }
        try (var connection = DriverManager.getConnection(embedded, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT winner FROM game_results")) {
            assertTrue(rows.next());
            assertEquals("YELLOW", rows.getString(1));
            assertFalse(rows.next());
        }
    }
}
