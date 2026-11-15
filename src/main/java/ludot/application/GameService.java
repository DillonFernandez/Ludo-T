// Advances server-owned games and saves snapshots and messages for client retrieval.
package ludot.application;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.random.RandomNumbers;
import ludot.random.RandomSource;

public final class GameService {
    public static final int OUTPUT_PAGE_SIZE = 2000;
    private final GameRepository repository;
    private final RandomSource random;
    private final ArrayDeque<String> recentMessages = new ArrayDeque<>();
    private final List<String> pendingMessages = new ArrayList<>();
    private final List<String> savedMessages = new ArrayList<>();
    private LudoGame game;
    private GameSnapshot snapshot;
    private boolean failed;
    private static final String FAILURE_MESSAGE = "The simulation stopped because the server could not process or save an update.";

    public GameService(GameRepository repository) throws SQLException {
        this(repository, new RandomNumbers());
    }

    public GameService(GameRepository repository, RandomSource random) throws SQLException {
        this.repository = repository;
        this.random = random;
        snapshot = repository.loadLatest();
        if (snapshot == null) {
            game = new LudoGameFactory().createGame(random, this::recordMessage);
            snapshot = GameSnapshot.capture(0, UUID.randomUUID().toString(), "READY", game, "");
        } else {
            savedMessages.addAll(repository.loadMessages(snapshot.getSession(), 0, Integer.MAX_VALUE));
            if (!snapshot.getLog().isEmpty()) {
                for (String message : snapshot.getLog().split("\n")) {
                    recentMessages.addLast(message);
                }
            }
            if (snapshot.isRunning()) {
                recordMessage("The server restarted before this simulation finished.");
                snapshot = new GameSnapshot(snapshot.getRevision() + 1, snapshot.getSession(),
                        snapshot.getRound(), "INTERRUPTED", snapshot.getMystery(),
                        snapshot.getLocations(), String.join("\n", recentMessages));
                repository.save(snapshot, pendingMessages);
                savedMessages.addAll(pendingMessages);
                pendingMessages.clear();
            }
        }
    }

    public GameSnapshot start() throws SQLException {
        if (snapshot.isRunning()) {
            return snapshot;
        }
        recentMessages.clear();
        pendingMessages.clear();
        failed = false;
        game = new LudoGameFactory().createGame(random, this::recordMessage);
        game.start();
        save(UUID.randomUUID().toString(), "RUNNING");
        return snapshot;
    }

    public boolean advance() throws SQLException {
        return advance(1);
    }

    public boolean advance(int maximumRounds) throws SQLException {
        if (maximumRounds < 1) {
            throw new IllegalArgumentException("The number of rounds must be positive.");
        }
        if (!snapshot.isRunning() || failed) {
            return false;
        }
        for (int round = 0; round < maximumRounds && !game.isFinished(); round++) {
            game.playNextRound();
        }
        save(snapshot.getSession(), game.isFinished() ? "FINISHED" : "RUNNING");
        return true;
    }

    private void save(String session, String status) throws SQLException {
        GameSnapshot next = GameSnapshot.capture(snapshot.getRevision() + 1,
                session, status, game, String.join("\n", recentMessages));
        repository.save(next, pendingMessages);
        if (!session.equals(snapshot.getSession())) {
            savedMessages.clear();
        }
        savedMessages.addAll(pendingMessages);
        pendingMessages.clear();
        snapshot = next;
    }

    public void fail() {
        failed = true;
        pendingMessages.clear();
        recentMessages.clear();
        if (!snapshot.getLog().isEmpty()) {
            for (String message : snapshot.getLog().split("\n")) {
                recentMessages.addLast(message);
            }
        }
        recentMessages.addLast(FAILURE_MESSAGE);
        snapshot = new GameSnapshot(snapshot.getRevision() + 1, snapshot.getSession(),
                snapshot.getRound(), "FAILED", snapshot.getMystery(), snapshot.getLocations(),
                String.join("\n", recentMessages));
    }

    public GameSnapshot getOutput(String session, int offset) throws SQLException {
        int messageCount = savedMessages.size();
        if (!session.equals(snapshot.getSession()) || offset < 0 || offset > messageCount + (failed ? 1 : 0)) {
            throw new IllegalArgumentException("Invalid output position.");
        }
        List<String> page = new ArrayList<>();
        for (int index = offset; index < messageCount && page.size() < OUTPUT_PAGE_SIZE; index++) {
            page.add(savedMessages.get(index));
        }
        if (failed && page.size() < OUTPUT_PAGE_SIZE && offset + page.size() == messageCount) {
            page.add(FAILURE_MESSAGE);
        }
        return new GameSnapshot(snapshot.getRevision(), session, snapshot.getRound(), snapshot.getStatus(),
                snapshot.getMystery(), snapshot.getLocations(), String.join("\n", page));
    }

    private void recordMessage(String message) {
        pendingMessages.add(message);
        recentMessages.addLast(message);
        while (recentMessages.size() > 600) {
            recentMessages.removeFirst();
        }
    }

    public GameSnapshot getSnapshot() {
        return snapshot;
    }
}
