// Tests asynchronous replies, pushed updates, GUI responsiveness and delayed or failed requests.
package ludot.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import ludot.server.ServerFixture;
import ludot.application.GameSnapshot;
import ludot.client.GameClient;
import ludot.random.RandomNumbers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class AsyncClientsTest {
    // A second request queues while START is blocked, then both futures return the
    // same state.
    @Test
    void asynchronousSubmission() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient client = fixture.client();
            CompletableFuture<GameSnapshot> pending = fixture.beginBlocked(client);
            assertFalse(pending.isDone());
            CompletableFuture<GameSnapshot> second = client.request("STATUS");
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 1);
            assertFalse(second.isDone());
            fixture.repository.release.countDown();
            ServerFixture.sameState(pending.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
    }

    // A completion callback receives RUNNING state after the save is released,
    // matching a later STATUS reply.
    @Test
    void asynchronousReplies() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient client = fixture.client();
            CompletableFuture<GameSnapshot> pending = fixture.beginBlocked(client);
            CompletableFuture<GameSnapshot> response = new CompletableFuture<>();
            pending.thenAccept(response::complete);
            assertFalse(response.isDone());
            fixture.repository.release.countDown();
            GameSnapshot started = response.get(5, TimeUnit.SECONDS);
            assertEquals("RUNNING", started.getStatus());
            ServerFixture.sameState(started, client.request("STATUS").get(5, TimeUnit.SECONDS));
        }
    }

    // The visible Swing window remains responsive while its startup save is
    // blocked.
    @Test
    void pendingGuiRequest() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked();
                WindowFixture view = new WindowFixture(fixture.server.getPort(), true)) {
            assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                assertTrue(view.window.isVisible());
                responsive.countDown();
            });
            assertTrue(responsive.await(2, TimeUnit.SECONDS));
            fixture.repository.release.countDown();
            view.awaitText(text -> text.contains("player"));
        }
    }

    // Overlapping START requests remain pending until release and then return
    // identical game state.
    @Test
    void overlappingClients() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient first = fixture.client();
            GameClient second = fixture.client();
            CompletableFuture<GameSnapshot> start = fixture.beginBlocked(first);
            CompletableFuture<GameSnapshot> other = second.request("START");
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() == 1);
            assertFalse(start.isDone());
            assertFalse(other.isDone());
            fixture.repository.release.countDown();
            ServerFixture.sameState(start.get(5, TimeUnit.SECONDS), other.get(5, TimeUnit.SECONDS));
        }
    }

    // Starting through the first client pushes the same running state to the second
    // client.
    @Test
    void firstToSecond() throws Exception {
        pushedChange(true);
    }

    // Starting through the second client pushes the same running state to the first
    // client.
    @Test
    void secondToFirst() throws Exception {
        pushedChange(false);
    }

    private void pushedChange(boolean firstStarts) throws Exception {
        try (ServerFixture fixture = new ServerFixture()) {
            CompletableFuture<GameSnapshot> firstUpdate = new CompletableFuture<>();
            CompletableFuture<GameSnapshot> secondUpdate = new CompletableFuture<>();
            GameClient first = fixture.client(state -> {
                if (state.getStatus().equals("RUNNING")) {
                    firstUpdate.complete(state);
                }
            });
            GameClient second = fixture.client(state -> {
                if (state.getStatus().equals("RUNNING")) {
                    secondUpdate.complete(state);
                }
            });
            GameSnapshot started = (firstStarts ? first : second).request("START").get(5, TimeUnit.SECONDS);
            GameSnapshot pushed = (firstStarts ? secondUpdate : firstUpdate).get(5, TimeUnit.SECONDS);
            ServerFixture.sameState(started, pushed);
        }
    }

    // Two windows display identical server output after their overlapping starts
    // produce only one save.
    @Test
    void guiConvergence() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient observer = fixture.client();
            try (WindowFixture first = new WindowFixture(fixture.server.getPort(), true);
                    WindowFixture second = new WindowFixture(fixture.server.getPort(), true)) {
                assertTrue(fixture.repository.entered.await(5, TimeUnit.SECONDS));
                fixture.repository.release.countDown();
                ServerFixture.await(() -> fixture.repository.saves.get() == 1);
                GameSnapshot state = observer.request("STATUS").get(5, TimeUnit.SECONDS);
                first.awaitText(state.getLog()::equals);
                second.awaitText(state.getLog()::equals);
                assertEquals(first.text(), second.text());
                assertEquals(1, fixture.repository.saves.get());
            }
        }
    }

    // Completion and save failures are pushed to clients; failure retains committed
    // state and stops advancement.
    @Test
    void serverPushes() throws Exception {
        try (ServerFixture fixture = new ServerFixture(false, 1, 100, new RandomNumbers(1234))) {
            CompletableFuture<GameSnapshot> finished = new CompletableFuture<>();
            GameClient listener = fixture.client(state -> {
                if (state.getStatus().equals("FINISHED")) {
                    finished.complete(state);
                }
            });
            listener.request("START").get(5, TimeUnit.SECONDS);
            GameSnapshot result = finished.get(10, TimeUnit.SECONDS);
            assertTrue(result.getRound() > 0);
            assertTrue(result.getLog().contains("wins!!!"));
        }
        try (ServerFixture fixture = new ServerFixture()) {
            CompletableFuture<GameSnapshot> failed = new CompletableFuture<>();
            GameClient listener = fixture.client(state -> {
                if (state.getStatus().equals("FAILED")) {
                    failed.complete(state);
                }
            });
            fixture.repository.fail = true;
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> listener.request("START").get(5, TimeUnit.SECONDS));
            GameSnapshot state = failed.get(5, TimeUnit.SECONDS);
            assertEquals(0, state.getRound());
            for (int location : state.getLocations()) {
                assertEquals(-1, location);
            }
            assertTrue(state.getLog().contains("could not process or save an update"));
            assertNull(fixture.repository.loadLatest());
        }
        try (ServerFixture fixture = new ServerFixture()) {
            GameSnapshot saved = fixture.client().request("START").get(5, TimeUnit.SECONDS);
            GameSnapshot committed = fixture.service.getSnapshot();
            ServerFixture.sameState(saved, committed);
            fixture.repository.fail = true;
            assertThrows(java.sql.SQLException.class, fixture.service::advance);
            assertSame(committed, fixture.service.getSnapshot());
            fixture.service.fail();
            assertEquals("FAILED", fixture.service.getSnapshot().getStatus());
            assertArrayEquals(committed.getLocations(), fixture.service.getSnapshot().getLocations());
            assertEquals(committed.getRound(), fixture.service.getSnapshot().getRound());
            assertEquals(
                    committed.getLog()
                            + "\nThe simulation stopped because the server could not process or save an update.",
                    fixture.service.getSnapshot().getLog());
            assertEquals(fixture.service.getSnapshot().getLog(),
                    fixture.service.getOutput(saved.getSession(), 0).getLog());
            assertFalse(fixture.service.advance());
        }
    }

    // A delayed startup reply cannot replace a newer finished board, status or
    // complete output.
    @Test
    void delayedReply() throws Exception {
        try (ServerFixture fixture = new ServerFixture(false, 1, 100, new RandomNumbers(1234));
                WindowFixture.DelayedReply connection = new WindowFixture.DelayedReply(fixture.server.getPort());
                WindowFixture view = new WindowFixture(connection.listener.getLocalPort(), true)) {
            assertTrue(connection.captured.await(5, TimeUnit.SECONDS));
            view.awaitEnding(" player wins!!!");
            GameSnapshot latest = fixture.client().request("STATUS").get(5, TimeUnit.SECONDS);
            String completeOutput = view.text();
            assertTrue(completeOutput.startsWith("The red player has four"));
            assertTrue(completeOutput.contains("Round 1\n"));
            assertTrue(completeOutput.split("\n").length > 600);
            connection.release.countDown();
            assertTrue(connection.delivered.await(5, TimeUnit.SECONDS));
            CompletableFuture<GameSnapshot> startup = new CompletableFuture<>();
            SwingUtilities.invokeAndWait(() -> view.window.startup.whenComplete((state, error) -> {
                if (error == null) {
                    startup.complete(state);
                } else {
                    startup.completeExceptionally(error);
                }
            }));
            GameSnapshot delayed = startup.get(5, TimeUnit.SECONDS);
            assertTrue(delayed.getRevision() < latest.getRevision());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Round " + latest.getRound() + " / 10,000 - finished", view.status.getText());
                assertTrue(view.board.getAccessibleContext().getAccessibleDescription()
                        .contains("Round " + latest.getRound()));
            });
            assertEquals(completeOutput, view.text());
            connection.check();
        }
    }

    // Queued requests preserve GUI responsiveness and matching state, while closure
    // fails pending requests.
    @Test
    void outstandingRequests() throws Exception {
        try (ServerFixture fixture = ServerFixture.blocked(); WindowFixture view = new WindowFixture()) {
            GameClient client = fixture.client();
            CompletableFuture<GameSnapshot> start = fixture.beginBlocked(client);
            List<CompletableFuture<GameSnapshot>> pending = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                pending.add(client.request("STATUS"));
            }
            ServerFixture.await(() -> fixture.server.getQueuedRequestCount() >= 200);
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(responsive::countDown);
            assertTrue(responsive.await(2, TimeUnit.SECONDS));
            assertTrue(pending.stream().noneMatch(CompletableFuture::isDone));
            fixture.repository.release.countDown();
            GameSnapshot state = start.get(5, TimeUnit.SECONDS);
            for (CompletableFuture<GameSnapshot> reply : pending) {
                ServerFixture.sameState(state, reply.get(5, TimeUnit.SECONDS));
            }
            client.close();
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> client.request("STATUS").get(5, TimeUnit.SECONDS));
        }
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient client = fixture.client();
            CompletableFuture<GameSnapshot> pending = fixture.beginBlocked(client);
            client.close();
            assertTrue(pending.isCompletedExceptionally());
            fixture.repository.release.countDown();
        }
        try (ServerFixture fixture = ServerFixture.blocked()) {
            GameClient client = fixture.client();
            CompletableFuture<GameSnapshot> pending = fixture.beginBlocked(client);
            fixture.server.close();
            assertTrue(fixture.repository.closed);
            fixture.server.close();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
        }
    }
}
